"""
Converts a 1Money backup (SQLite, "1Money_BACKUP_*.bin") into SQL inserts
for the `money` schema (see supabase/migrations).

Only the latest snapshot in the backup is used. Scheduled (future) transactions are skipped.
IDs are deterministic (UUIDv5 of the 1Money IDs), so the output can be re-applied
after deleting the user's data.

Usage:
  python onemoney_to_4money.py BACKUP.bin --user-id UUID --currency-code EUR \
      [--tz Europe/Riga] [--balance "Карта=123.45" ...] > import.sql

1Money table semantics (obfuscated names):
  de  - accounts and categories. _ty: 0 = account (if _a_i_i_b is set) or income category,
        1 = expense category, 2 = savings account, 4 = "All accounts" virtual entry.
        _pi = parent ID (subcategory), _ar = archived, _co = ARGB color, _a_o = account order.
  tr  - transactions. _ty: 0 = expense or transfer to an account, 1 = income.
        _a_i = account, _d_i = category or account, _a_m/_d_m = amounts, _da = epoch ms,
        _sch = scheduled, _co = comment.
  ba  - snapshots, _b_i in other tables refers to it.
"""

import argparse
import re
import sqlite3
import sys
import uuid
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from zoneinfo import ZoneInfo

NAMESPACE = uuid.UUID("6f1c3f5e-4d2b-4a8e-9a57-1a0f6b9c4d10")
REPO_ROOT = Path(__file__).resolve().parents[2]
COLOR_SCHEMES_FILE = next(REPO_ROOT.glob("app/src/**/HardcodedItemColorSchemeRepository.kt"))


def stable_id(kind: str, onemoney_id) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{kind}:{onemoney_id}"))


def load_color_schemes() -> dict[str, tuple[int, int, int]]:
    text = COLOR_SCHEMES_FILE.read_text(encoding="utf-8")
    schemes = {}
    for name, primary in re.findall(r'name = "(\w+)",\s*primary = 0x([0-9A-Fa-f]{8})', text):
        value = int(primary, 16)
        schemes[name] = ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)
    return schemes


def nearest_scheme(argb: int | None, schemes) -> str:
    if argb is None:
        return "Blue3"
    value = argb & 0xFFFFFFFF
    rgb = ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)
    return min(schemes, key=lambda n: sum((a - b) ** 2 for a, b in zip(schemes[n], rgb)))


def minor(amount: str, precision: int) -> int:
    return int((Decimal(amount) * (10 ** precision)).quantize(Decimal(1), rounding=ROUND_HALF_UP))


def sql(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, Decimal)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("backup")
    parser.add_argument("--user-id", required=True)
    parser.add_argument("--currency-code", required=True,
                        help="4Money currency code for the 1Money main currency")
    parser.add_argument("--precision", type=int, default=2)
    parser.add_argument("--tz", default="Europe/Riga")
    parser.add_argument("--balance", action="append", default=[],
                        help="Actual account balance, 'Title=123.45'. "
                             "If not set, the balance is computed from the history.")
    args = parser.parse_args()

    tz = ZoneInfo(args.tz)
    schemes = load_color_schemes()
    db = sqlite3.connect(f"file:{args.backup}?mode=ro", uri=True)
    snapshot = db.execute("select max(_b_i) from tr").fetchone()[0]

    definitions = db.execute(
        "select _id, _ty, _co, _na, _ar, _a_i_i_b, _a_o, _pi from de where _b_i = ? order by _id",
        (snapshot,),
    ).fetchall()

    accounts = {}  # 1Money ID -> dict
    categories = {}
    for (om_id, ty, color, title, archived, in_balance, order, parent) in definitions:
        if (ty == 0 and in_balance is not None) or ty == 2:
            accounts[om_id] = dict(
                id=stable_id("account", om_id),
                title=title.strip(),
                type="savings" if ty == 2 else "regular",
                color=nearest_scheme(color, schemes),
                archived=bool(archived),
                order=order or 0,
                balance=Decimal(0),
            )
        elif ty in (0, 1):
            categories[om_id] = dict(
                id=stable_id("category", om_id),
                title=title.strip(),
                is_income=ty == 0,
                color=nearest_scheme(color, schemes),
                archived=bool(archived),
                parent=parent,
            )

    transfers = []
    skipped = 0
    for (om_id, ty, time_ms, account_id, dest_id, a_amount, d_amount, comment, scheduled) in db.execute(
            "select _id, _ty, _da, _a_i, _d_i, _a_m, _d_m, _co, _sch from tr where _b_i = ? order by _da",
            (snapshot,),
    ):
        if scheduled or account_id not in accounts:
            skipped += 1
            continue

        local_time = (datetime.fromtimestamp(time_ms / 1000, tz=timezone.utc)
                      .astimezone(tz).strftime("%Y-%m-%d %H:%M:%S"))
        account = accounts[account_id]
        a_minor = minor(a_amount, args.precision)
        d_minor = minor(d_amount, args.precision)

        if ty == 1:
            if dest_id not in categories:
                skipped += 1
                continue
            source, source_amount = categories[dest_id]["id"], d_minor
            destination, destination_amount = account["id"], a_minor
            account["balance"] += a_minor
        elif dest_id in accounts:
            source, source_amount = account["id"], a_minor
            destination, destination_amount = accounts[dest_id]["id"], d_minor
            account["balance"] -= a_minor
            accounts[dest_id]["balance"] += d_minor
        elif dest_id in categories:
            source, source_amount = account["id"], a_minor
            destination, destination_amount = categories[dest_id]["id"], d_minor
            account["balance"] -= a_minor
        else:
            skipped += 1
            continue

        transfers.append((stable_id("transfer", om_id), local_time, source, source_amount,
                          destination, destination_amount,
                          comment.strip() if comment and comment.strip() else None))

    actual_balances = {}
    for entry in args.balance:
        title, value = entry.rsplit("=", 1)
        actual_balances[title.strip()] = minor(value, args.precision)

    out = sys.stdout
    out.write("-- Generated by onemoney_to_4money.py\n")
    out.write(f"-- snapshot={snapshot} accounts={len(accounts)} categories={len(categories)} "
              f"transfers={len(transfers)} skipped={skipped}\n")
    out.write("begin;\n")
    out.write(f"create temp table _import_currency as select id from money.currencies "
              f"where code = {sql(args.currency_code)};\n")
    out.write("do $$ begin if (select count(*) from _import_currency) <> 1 then "
              "raise exception 'Currency not found'; end if; end $$;\n")

    # Higher position goes first for accounts.
    sorted_accounts = sorted(accounts.values(), key=lambda a: a["order"])
    for index, account in enumerate(sorted_accounts):
        balance = actual_balances.get(account["title"], account["balance"])
        out.write(
            "insert into money.accounts (id, user_id, title, balance, currency_id, position, color_scheme, type, is_archived) "
            f"select {sql(account['id'])}, {sql(args.user_id)}, {sql(account['title'])}, {balance}, id, "
            f"{len(sorted_accounts) - index}, {sql(account['color'])}, {sql(account['type'])}, {sql(account['archived'])} "
            "from _import_currency;\n")

    # Parents first, subcategories inherit parent's color and kind, as the client does.
    top_level = [c for c in categories.values() if c["parent"] is None]
    children = [c for c in categories.values() if c["parent"] is not None]
    for index, category in enumerate(top_level):
        category["position"] = len(top_level) - index
    for parent_om_id, parent in categories.items():
        subs = [c for c in children if c["parent"] == parent_om_id]
        for index, sub in enumerate(subs):
            sub["position"] = index + 1
            sub["color"] = parent["color"]
            sub["is_income"] = parent["is_income"]

    for category in top_level + children:
        parent_id = categories[category["parent"]]["id"] if category["parent"] is not None else None
        out.write(
            "insert into money.categories (id, user_id, title, currency_id, parent_category_id, is_income, color_scheme, is_archived, position) "
            f"select {sql(category['id'])}, {sql(args.user_id)}, {sql(category['title'])}, id, {sql(parent_id)}, "
            f"{sql(category['is_income'])}, {sql(category['color'])}, {sql(category['archived'])}, {category['position']} "
            "from _import_currency;\n")

    for chunk_start in range(0, len(transfers), 500):
        chunk = transfers[chunk_start:chunk_start + 500]
        out.write("insert into money.transfers (id, user_id, time, source_id, source_amount, "
                  "destination_id, destination_amount, memo) values\n")
        out.write(",\n".join(
            f"({sql(t[0])}, {sql(args.user_id)}, {sql(t[1])}, {sql(t[2])}, {t[3]}, {sql(t[4])}, {t[5]}, {sql(t[6])})"
            for t in chunk))
        out.write(";\n")

    out.write("commit;\n")

    print(f"snapshot={snapshot} accounts={len(accounts)} categories={len(categories)} "
          f"transfers={len(transfers)} skipped={skipped}", file=sys.stderr)
    for account in sorted_accounts:
        computed = account["balance"]
        actual = actual_balances.get(account["title"])
        print(f"  account {account['title']!r} ({account['type']}): computed={computed}"
              + (f" actual={actual}" if actual is not None else ""), file=sys.stderr)


if __name__ == "__main__":
    main()
