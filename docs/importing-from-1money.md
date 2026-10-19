# Importing history from 1Money

`tools/onemoney-import/onemoney_to_4money.py` converts a 1Money backup into one SQL file for the
`money` schema of your own backend (see [self-hosting.md](self-hosting.md)). You apply that file with
`psql`, and PowerSync then delivers the rows to the app like any other data.

The tool is a one-shot migration helper written for one person's backup. It reads 1Money's internal
database layout, which is not a public format, so a newer 1Money version may break it. Always check the
counts it prints (see [Check the result](#check-the-result)).

## 1. Get the backup file

The only fact verified here is that 1Money's backup feature produces a file named like
`1Money_BACKUP_<something>.bin`. The exact menu steps to create it are not documented here, because they
vary by 1Money version. Copy the file to the computer where you will run the script.

The `.bin` file is a SQLite database that keeps daily snapshots, not a CSV. The script opens it read-only
and uses only the latest snapshot.

## 2. Prerequisites

- The backend is installed and all migrations are applied (the script inserts into `money.accounts`,
  `money.categories`, `money.transfers` and reads `money.currencies`).
- You know the `auth.users` id (UUID) of the app user, for example from
  `select id, email from auth.users;`
- Python 3.10 or newer (the script uses `zoneinfo`; on Windows run `pip install tzdata` if the time zone
  is not found).
- Run the script from a checkout of this repository: it reads the colour palette from
  `HardcodedItemColorSchemeRepository.kt` under `app/src`.

## 3. Run the script

```
python tools/onemoney-import/onemoney_to_4money.py 1Money_BACKUP_example.bin \
    --user-id 00000000-0000-0000-0000-000000000000 \
    --currency-code EUR \
    --tz Europe/Riga \
    --balance "Main card=1234.56" --balance "Cash=80.00" \
    > import.sql
```

| Argument | Meaning |
|---|---|
| `backup` | Path to the `1Money_BACKUP_*.bin` file. |
| `--user-id` | Required. `auth.users.id` of the app user; it becomes `user_id` of every row. |
| `--currency-code` | Required. Code of the 1Money main currency as it is in `money.currencies` (for example `EUR`). If it is not found, the generated SQL raises `Currency not found` and nothing is imported. |
| `--precision` | Decimal places of the currency, default `2`. Amounts are stored as integers in minor units. |
| `--tz` | IANA time zone, default `Europe/Riga`. Set your own, see [Time zone](#time-zone). |
| `--balance "Title=123.45"` | Repeatable. The real current balance of the account with this exact title. |

The SQL goes to stdout; a summary goes to stderr:

```
snapshot=... accounts=... categories=... transfers=... skipped=...
  account 'Main card' (regular): computed=123456 actual=123456
```

(`computed` and `actual` are in minor units.)

## 4. Apply the SQL

Back up the database first (at least `pg_dump --schema=money`). Then run the file as a privileged user
(for example `postgres`) against the Supabase database:

```
psql "postgresql://postgres:<password>@<host>:5432/postgres" -v ON_ERROR_STOP=1 -f import.sql
```

The file is wrapped in `begin; ... commit;`, so an error leaves the database unchanged. It inserts the
rows directly, without the `transfer` RPC, so balances come from the file and are not recalculated from the
transfers. After PowerSync replicates the rows, open the app and let it sync.

## What is imported

- Accounts: regular and savings accounts, with title, archived flag, order and colour. The 1Money
  "All accounts" entry is not an account and is ignored.
- Categories: expense and income categories with subcategories and archived flags. Subcategories inherit
  the colour and the income/expense kind of their parent, as the app does.
- Transactions: expenses, incomes and transfers between accounts, with the exact time, the comment as memo
  and both amounts (a transfer between currencies keeps both sides).
- Colours are mapped to the nearest colour scheme of 4Money; an account or category without a colour
  falls back to the `Blue3` scheme.

## What is skipped

- Scheduled (future) transactions.
- Transactions that point to an account or category the script cannot find; they are counted in `skipped`.
- Everything except the latest snapshot in the file.
- Icons: accounts and categories get the default icon; assign icons in the app.
- Anything else 1Money stores that has no table in the script (it reads accounts, categories and
  transactions only).
- Multiple currencies: every imported account and category gets the single `--currency-code` currency.
  Change the currency of an account in the app afterwards if needed.

## Balances

1Money keeps opening balances elsewhere, so the sum of the transaction history is usually not the real
balance. For each account the script writes the balance given by `--balance "Title=..."`; without it, the
sum computed from the history. Compare the `computed=` value in the stderr summary with the balance shown
in 1Money and pass `--balance` for every account where they differ. The title must match the 1Money title
exactly: a `--balance` title that matches no account is silently ignored (only `computed=` appears in the
summary). You can also fix a balance in the app after the import.

## Time zone

1Money stores epoch milliseconds. 4Money stores the transfer time as local wall-clock time without a zone,
so the script converts every timestamp to `--tz`. Use the zone you lived in when you entered the data;
transactions made while travelling are shifted by the difference.

## Deterministic IDs and re-running

All ids are UUIDv5 of the 1Money ids under a fixed namespace, so the same backup always yields the same ids
and the file can be regenerated. It is not an upsert: applying it a second time fails on duplicate keys. To
import again, first delete that user's rows from `money.transfers`, `money.categories` and
`money.accounts` (after a backup).

Warning: deleting those rows also deletes everything you created in the app after the import.

## Check the result

- The `accounts / categories / transfers / skipped` counts in stderr (they are also in the first comment
  lines of `import.sql`) against what 1Money shows.
- Monthly totals of a few categories in the app against 1Money.
- Account balances in the app.

Design notes: section B of
[the design spec](superpowers/specs/2026-10-01-self-hosted-4money-design.md#b-1money-import).
