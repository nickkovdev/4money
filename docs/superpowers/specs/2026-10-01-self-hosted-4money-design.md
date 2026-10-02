# Self-hosted 4Money + auto-categorization from bank notifications

Status: A and B implemented 2026-10-01; C, D and E designed 2026-10-02, not started.

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
| E | UI in the style of 1Money (theme, icons, ring, Overview) | designed |

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

### C/D decisions (2026-10-02)

- **Only SEB Latvia (`se.seb.latvia`) is a source.** Google Wallet (`com.google.android.apps.walletnfcrel`)
  posts a duplicate for the same payment and is ignored, as are all other packages.
- Sample card payment (title / text), card digits masked:
  ```
  Jauna rezervācija
  Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .
  ```
  - Amount uses a decimal comma; currency is an ISO code; payee is the merchant name truncated by the bank.
  - The date/time in the text is not local time (merchant/UTC); the notification post time is used instead.
  - `karte...NNNN` gives the card's last 4 digits, mapped to an account in settings (default: the account used most).
- **Foreign currency** (currency ≠ mapped account's currency): never auto-created, always goes to the inbox
  as pending with the original amount shown; the user enters the account-currency amount.
- Templates not yet sampled (EUR payment, outgoing/incoming transfer, refund) must not crash the parser:
  an SEB notification that matches no template becomes a pending inbox item with the raw text.

## E. UI in the style of 1Money

Inspired by 1Money's layout and features; **no 1Money assets are copied** (proprietary).
Icons come from Tabler Icons (MIT), attributed in the repo.

- **E1 Theme and icons.** There is no theme system today (≈56 hardcoded colors in 11 files, white window).
  Introduce `MoneyTheme` color tokens via CompositionLocal, dark by default, following the system,
  with a light/dark/system preference. Avatars in dark mode: circle, dark tint of the category color as
  background, saturated icon/letter in the category color. Add ≈150 Tabler line icons as
  `*_itemicon` vector drawables so the existing picker shows them. Bottom bar emoji replaced by line icons.
  A one-off SQL script assigns icons to the imported categories by title.
- **E2 Categories with a ring.** 4 columns: one regular row, then two rows with two categories on each side
  of a 2×2 donut ring (segments are category shares in their colors; center shows the expense and income
  totals, tap toggles), then regular rows.
- **E3 Overview tab.** Tabs become Accounts / Categories / Transactions / Overview; More moves to a profile
  icon in the top-left. Overview: period balance (income − expense), Expenses/Income cards (select mode),
  per-day bar chart stacked by the top categories (rest grey), day avg / week avg / month total,
  top 3 categories with % plus "More…". Built on `getCategoryDailyAmountsFlow`, no chart library.
- **E4 Motion and gestures** (the 1Money flow must be preserved):
  - Horizontal swipe on screen content switches the period (previous/next month) on
    Categories, Transactions and Overview, with a slide animation in the swipe direction;
    tabs switch only by tapping the bottom bar. Vertical scrolling must not be hijacked.
  - Numbers animate on change (totals, balances); ring segments and bars animate when the
    period or mode changes.
  - Smooth screen transitions: slide/fade for navigation, animated bottom sheets.
  - Swipe a transaction row to reveal edit / delete (delete with undo).
- **E5 Transfer sheet** (expense/income entry, reference: 1Money):
  - Header split in two halves: "From account" in the account color and "To category" in the category
    color, each with its icon badge; tapping a half changes the counterparty.
  - Subcategories as a single-line, horizontally scrollable row of outlined chips (icon + title) in the
    category color; tap selects/deselects. No wrapping to multiple lines.
  - Amount label ("Expense"/"Income") and the amount in the category color, animated on input.
  - Notes field, then a calculator keypad: ÷ × − + operators, digits, decimal point, currency key,
    backspace, date (calendar) key, and a tall confirm button in the category color; the selected date
    shown under the keypad.
- Out of scope now: drag account → category quick expense, recurring/scheduled transfers, projected balance, search, header with total balance and period picker.

## Testing

- Server: SQL contract test of every RPC as an authenticated user over PostgREST
  (create, double transfer idempotency, edit, revert, RLS rejection) — done manually at A.
- Import: counts and per-category monthly sums against 1Money.
- C/D: unit tests for parsers (from real sample texts), payee normalization and rule matching;
  manual end-to-end with a real payment.
