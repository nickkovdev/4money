# Self-hosted 4Money + auto-categorization from bank notifications

Status: A and B implemented 2026-10-01; C and D designed, not started.

## Goal

Replace 1Money with a personal fork of 4Money backed by my own server, keep the whole
1Money history, and stop entering card expenses by hand: a bank push notification
should become an expense in the right category automatically, or land in an inbox
to be categorized manually, teaching the app a rule on the way.

Single user. Phone: Samsung S25+ (Android, no root). Battery efficiency is a hard requirement.

## Sub-projects

| | What | State |
|---|---|---|
| A | Backend: `money` schema in the shared self-hosted Supabase, PowerSync, client pointed at it | done |
| B | 1Money history import | done |
| C | Bank notification listener inside the app | designed |
| D | Payee → category rules, inbox UI | designed |

## A. Backend

Runs on the Azure VM next to the already existing self-hosted Supabase, which is shared
with other projects. 4Money is isolated in its own Postgres schema.

- Schema and RPCs: `supabase/migrations/20261001000000_money_schema.sql`.
  Reconstructed from the client because upstream doesn't publish it:
  - Tables `currencies` (global, seeded), `accounts`, `categories`, `transfers`,
    `sync_errors` (per user, `user_id default auth.uid()`, RLS), `daily_prices` (global).
  - RPCs the client calls: `atomic_crud` (adapted from the author's gist, restricted to
    user tables), `transfer`, `transfer_edit`, `transfer_revert` (balances change on the server
    at the moment of sync; `transfer` is idempotent by ID), `get_daily_prices_array`.
  - PowerSync: `powersync_role` (replication, read-only) and the `powersync` publication on `money.*` only.
- PostgREST exposes `money` (`PGRST_DB_SCHEMAS`); the client sets it as the default schema
  via the `supabaseSchema` local property → `BuildConfig.SUPABASE_SCHEMA`.
- PowerSync: `deploy/powersync/` (compose, `service.yaml`, `sync-config.yaml`).
  Postgres bucket storage in the `powersync_storage` DB of the same Postgres, no MongoDB.
  Auth by the Supabase HS256 JWT secret. Published only on the Tailscale IP.
- Network: the phone reaches Supabase (`:8000`) and PowerSync (`:8080`) only via Tailscale.
  Cleartext HTTP is allowed for that single IP in `network_security_config.xml`,
  WireGuard encrypts the traffic.
- Auth: the app's current start screen signs in by email/password with
  `authTempCredential` used as both. A confirmed user was created with a random long credential.
  Phrase (Ed25519) auth edge functions are not deployed yet — not needed for a single user.
- Not done: `update-pairs` (daily prices) edge function + cron, so cross-currency totals
  have no rates yet. Everything is in EUR so far.

Client `app/local.properties` keys: `supabaseUrl`, `supabaseAnonKey`, `supabaseSchema`,
`powerSyncUrl`, `authTempCredential`. Never committed.

## B. 1Money import

`tools/onemoney-import/onemoney_to_4money.py` reads the 1Money backup file directly
(it is a SQLite DB with daily snapshots, not CSV) and emits SQL for the `money` schema.
The table semantics are documented in the script. Notable decisions:

- Latest snapshot only; scheduled (future) transactions skipped.
- Real timestamps (1Money stores epoch ms), converted to local `Europe/Riga` wall-clock time,
  which is what the client stores.
- Deterministic UUIDv5 IDs, so the import is repeatable.
- Colors mapped to the nearest 4Money color scheme; icons are not mapped.
- Account balances: 1Money keeps opening balances elsewhere, so the history sum is wrong.
  Pass actual balances with `--balance "Title=123.45"` or set them in the app.

## C. Notification listener (in the 4Money fork)

Chosen over a separate listener app + server rules (two apps, network on every payment,
duplicating the balance logic) and Tasker (paid, fragile):

- `NotificationListenerService` in the app. The system binds it and wakes it only on
  notifications: no foreground service, no wakelock, no polling, Doze-friendly.
- First check is `packageName` against the configured bank app(s); everything else returns immediately.
- A bank-specific parser (regex per notification template) extracts amount, currency,
  payee and card/account hint. Unparsed bank notifications are kept raw in the inbox
  rather than dropped.
- All work is a local SQLite write within milliseconds; no network calls.
  Upload happens through the existing PowerSync sync (on app open or the 30 min
  `BackgroundPowerSyncWorker`), so a payment offline is fine.
- Dedup by a hash of (package, notification key, posted time, text), as banks re-post/update notifications.

## D. Rules and inbox

New tables, synced by PowerSync like the rest (per user, RLS):

- `payee_rules`: `id, payee_pattern (normalized), match_type (exact|contains), category_id,
  account_id, hits, last_used_at`.
- `inbox_items`: `id, received_at, source_package, raw_text, amount, currency_code,
  payee, account_id?, status (pending|done|dismissed), transfer_id?`.

Flow: notification → parse → normalize payee (case, whitespace, trailing city/terminal IDs) →
rule lookup from an in-memory map (loaded once, refreshed on rule change) →
- match: create the expense via the existing `TransferFundsUseCase`
  (same atomic balance update + `transfer` RPC upload), inbox item `done` with `transfer_id`;
- no match: inbox item `pending`.

UI: an Inbox screen (badge on Home) where a pending item opens the regular transfer sheet
prefilled with amount/payee; on save, a "remember for <payee>" toggle (on by default) creates
a rule. A Rules screen lists/edits/deletes rules. Auto-created expenses carry the payee as memo
and can be undone from the inbox.

Open inputs for C/D: bank app package name(s) and 3–5 sample notification texts.

## Testing

- Server: SQL contract test of every RPC as an authenticated user over PostgREST
  (create, double transfer idempotency, edit, revert, RLS rejection) — done manually at A.
- Import: counts and per-category monthly sums against 1Money.
- C/D: unit tests for parsers (from real sample texts), payee normalization and rule matching;
  manual end-to-end with a real payment.
