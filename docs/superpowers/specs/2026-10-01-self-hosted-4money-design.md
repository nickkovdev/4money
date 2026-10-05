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

## F2. Category sheet from Overview + privacy mode (designed 2026-10-02, approved by the owner)

Plan: `docs/superpowers/plans/2026-10-02-category-sheet-privacy.md` (rulings recorded there).

**1. Category sheet from Overview (reference: 1Money).** Tapping a category in the Overview top list
(and in its "More…" expanded list) opens a bottom sheet for that category and the current period/mode
(expense or income):
- Header in the category colour with its icon badge (like the transfer sheet header style), category
  title, "N transactions" (count in period), total amount, a progress bar = share of the period's total
  expense (or income) with the % text, then a row "<Period label, e.g. October 2026>" + the period's
  total of that direction.
- List of the category's subcategories (plus "no subcategory" bucket if non-zero) with icon, title,
  amount and a % bar (share of the category total), sorted desc.
- Bottom actions: "Expense" (or "Income" for income categories) → opens the regular transfer sheet with
  this category preselected as destination (source for income), using the existing transfer sheet
  route/params; "Transactions" → switches to the Transactions tab with the existing category filter chip
  applied (reuse the filter chip mechanism).
- Reuse existing data: `GetCategoryAmountsBySubcategoryUseCase` /
  `HistoryStatsRepository.getCategoryAmountsBySubcategoryFlow`, transfer counts (count query added),
  primary-currency conversion helpers already used by Overview.

**2. Privacy (anonymity) mode.**
- Toggle: an eye / eye-off icon button in the tab header next to the profile button (all four tabs),
  state persisted in preferences (like `ThemePreferences`), reactive app-wide through one central place
  (a CompositionLocal consumed by the amount formatting layer: `AnimatedAmountText` / `ViewAmountFormat`).
- Mask `•••` (no currency symbol) for headline/absolute numbers: account balances and the accounts
  total, Overview Balance / Expenses / Income cards, day/week/month averages and totals, category-sheet
  header amounts, account action sheet balance, Inbox/notification amounts.
- Everywhere else amounts become a percentage of the relevant total: categories grid/ring = share of the
  period total of that direction; transactions rows (and day header sums, if any) = share of the period
  total of the same direction (expense/income; transfers between accounts show •••); subcategory rows =
  share of the category total; Overview bar chart axis labels hidden (bars stay); top categories already
  show % — their amounts are hidden.
- "Payments to sort" notification shows no amounts while privacy is on.
- The keypad/amount being typed in the transfer sheet stays visible. No `FLAG_SECURE` (the point is to
  allow sharing screenshots).
- Pure, unit-tested logic for percentage computation/rounding (0 total → "—") and mask decisions.

## F3. Localization (en + ru) and app language

Plan: `docs/superpowers/plans/2026-10-03-localization-ru.md` (branch `feature/localization-ru`).

**What.** English is the base language, Russian is complete. Settings has a Language section: System /
English / Русский (language names shown in their own language). The choice goes through
`AppCompatDelegate.setApplicationLocales`; an empty tag means System. `AppLocalesMetadataHolderService`
(`autoStoreLocales=true`) persists it, `androidResources { generateLocaleConfig = true }` with
`res/resources.properties` (`unqualifiedResLocale=en`) feeds the system per-app language screen, and
`resConfigs "en", "ru"` trims the rest. Changing the language recreates every open screen (all activities
are `AppCompatActivity`).

**Where.**
- All user-visible text lives in `res/values/strings.xml` and `res/values-ru/strings.xml`, same keys in
  both (`<area>_<meaning>`, shared `common_*`), counts as `<plurals>` (ru has one/few/many/other). Lint
  `MissingTranslation` is an error, so `lintDebug` gates a missing Russian string.
- View models that build text emit `ViewText` (`Plain`, `Res`, `Plural`, `Dynamic`), resolved in the UI
  or with a context at show time.
- Dates: `ViewDateFormats` with the app locale (nominative "Октябрь 2026" in headers, genitive
  "1 октября" in day lines). Amounts: `ViewAmountFormat` by the app locale (ru: "1 234,56 €", comma on
  the keypad too). Amount ranges: `describeRange`.
- Not translated: user data (account/category titles, memos, payees), the SEB texts the parser matches,
  logs and exception messages.

**Decisions.**
- System with a device language other than en/ru: English strings, device number and date formats.
- The "Payments to sort" notification channel name/description and the question texts are built with
  `getString` at post time. On API 26-32 a process cold-started by a receiver may not know the app
  language yet and then uses the device language until the app is opened; API 33+ is exact.
- The listener label is a manifest label (`@string/bank_notification_listener_label`) resolved by the
  system in the system/device language, not by the app at runtime.
- Theme names Paper / Midnight / Ember / Aurora stay as proper names in both languages (their subtitles
  are translated); privacy symbols (`•••`, `—`) and `N%` stay as they are.
- `app_name` and the pure-format `template_category_subcategory` are `translatable="false"`.

## F4. Inbox tab and user-configured notification sources (designed 2026-10-04, approved by the owner)

Plan: `docs/superpowers/plans/2026-10-05-autobook-sources-inbox-tab.md` (branch `feature/autobook-sources-inbox-tab`).
Supersedes the C/D decision "only SEB Latvia is a source": SEB stays as a built-in preset, any other app can be
taught by the user.

**A. Inbox as a main tab.** The bottom bar has five tabs: Accounts, Categories, **Inbox** (centre, badge = pending
count), History, Overview. The tab: title, "N payments wait for a category · M recorded today", a big primary
"Sort as cards (N)" button (the existing cards screen), "Waiting" (pending items: payee, time · account, amount, the
suggested category as a one-tap chip and an "Other…" chip opening the prefilled transfer sheet), "Recorded today"
(today's done items with an Undo icon button, existing undo use case), a rules icon button in the header, an empty
state when nothing waits. `InboxActivity`, the Settings Inbox row and the inbox part of the profile dot are gone; the
"Payments to sort" notification opens the Inbox tab.

**B. Sources.**
- Table `money.notification_templates` (per user, RLS, synced): `id, user_id default auth.uid(), source_package,
  name, direction ('outgoing'|'incoming'), pattern (generated regex), fields jsonb (capture group numbers of amount,
  currency, payee, card; whether the text has a time), sample_text, is_enabled, created_at` (local wall-clock
  timestamp like `transfers.time`). Migration `supabase/migrations/20261006000000_money_notification_templates.sql`
  (grants, `powersync` publication, `atomic_crud` whitelist), select in `deploy/powersync/sync-config.yaml`.
- A source is a package with at least one template, or a built-in preset. SEB Latvia (`se.seb.latvia`) is a preset
  with three kinds (card payment, account payment, incoming payment) using the existing parser unchanged; it shows
  as configured and can be switched off (device-local). The listener handles enabled presets plus packages with at
  least one enabled template; for a package the preset is tried first, then its enabled templates by `created_at`;
  first match wins; no match → a raw pending inbox item as before.
- Template builder (pure): the sample `title + "\n" + text` is tokenized (words, numbers incl. thousands
  separators, dates, times, punctuation); the user marks amount, currency, payee (one contiguous run, matched
  lazily so other payees of any length fit), card digits (optional) and "varies" (any text, e.g. a purpose);
  unmarked words are literal (regex-escaped, case-insensitive), whitespace is flexible, unmarked numbers/dates/times
  become wildcards. Amount accepts space/NBSP/U+202F/dot/comma thousands separators and a decimal comma or dot
  (the last separator followed by 1-2 digits is the decimal one); currency is an ISO code or € $ £. Amount and
  currency are auto-detected on the sample. A template yields the existing `ParsedBankNotification.Payment`
  (direction from the template, `hasTimestamp` when the sample has a time), so rules, dedup, inbox, auto-expense
  and the question notification are unchanged.
- Recent-notification buffer, device only (a private no-backup file, never synced or logged): the last 50
  notifications of the last 7 days from any app whose text looks like money (amount next to a currency), used by
  the wizard; plus the listener's active notifications when the wizard opens. Cleared when notification access is
  found revoked and when a source is set up. Battery: one cheap regex per posted notification of a non-source app.
- Wizard (6 steps): intro + notification access (Android 13+ restricted settings hint) → choose app (detected
  money-like apps with counts, Google Wallet duplicate warning, all launchable apps) → pick a sample → mark tokens +
  Expense/Income + live preview → test against the buffer ("4 of 5", misses listed, "Teach another kind" loops to
  the sample step for the same app) → accounts (card last 4 → account, no card → account) and behaviour → Done saves
  the templates enabled.
- Behaviour (device-local, global, default on = previous behaviour): record known payees immediately (off → a rule
  match waits in the Inbox), ask in the notification (off → no "Payments to sort" notification), learn from history
  (off → suggestions only from rules).
- Settings: "Bank notifications" becomes "Auto-booking" → Sources screen (access status, source cards with kinds as
  chips, enable switch, "Set up"/"Teach another kind", "Add app", Cards and accounts, Payee rules, Test text).
- All strings en + ru; privacy mode masks amounts on the new screens; theme tokens and uikit components only.

**Decisions.** Recorded in the plan's Rulings section.

## F5. Home screen widget (designed 2026-10-05, approved by the owner)

Plan: `docs/superpowers/plans/2026-10-05-home-widget.md` (branch `feature/home-widget`).

**Widget.** "Quick entry" / "Быстрый ввод": a Glance app widget, default 3×1, resizable horizontally, with no
container background and three round 48 dp buttons spread evenly across the width: left "↑ income" (income
colour), centre the app logo with a badge, right "↓ expense" (expense colour). Icons are the Tabler
`ic_tabler_arrow_up/down`. `updatePeriodMillis = 0`: no periodic updates. Picker preview: `previewLayout` (API 31+)
and a drawable `previewImage` (older), no bitmaps. Code under `widget/` (`logic/`: badge text, palette, debounced
trigger, updater, badge starter; `view/`: `QuickEntryWidget`, its receiver; `HomeWidgetModule`).

**Badge.** The pending Inbox count on the centre button: hidden at 0, the number for 1..99, "99+" above. No
signed-in session → no badge. Tap on the centre → `HomeActivity` on the Inbox tab (`EXTRA_OPEN_INBOX`).

**Updates (no polling, no services).** A root-scope updater with a 500 ms debounce is triggered by: the pending
count flow collected in a session-scoped coroutine scope (started by a `UserSessionScopeListener`, cancelled when
the scope closes), the session scope closing (the badge disappears after sign-out), the notification listener
right after it stores a pending item, and a theme mode change. Count read is a single `COUNT(*)`; errors mean no
badge and are logged, never crash the widget.

**Quick entry.** Income/expense start `QuickTransferActivity` (`transfers/view/`), a translucent activity in its
own task (`taskAffinity` `.quickentry`, excluded from Recents, not exported, started with NEW_TASK + CLEAR_TASK so
a second tap restarts the flow and finishing returns to the launcher). Flow: the category grid (the existing
counterparty selection sheet, categories only, for the chosen direction) → the transfer sheet with the keypad. Default
account: last used for the category, else the most used account, else the account selection sheet (optional
`fallbackAccount` of `TransfersNavigator`, the main app is unchanged). The checkmark saves and finishes; back or a
tap outside finishes without saving. Unlike the launcher shortcuts it is not incognito: it requires a session
(no session → the sign-in screen in the app's own task) and unlocking (passcode) like other entry points, and the
sheets follow privacy mode like the normal transfer sheet.

**Theme.** The exact palette of the app theme: day colours = `moneyColorsOf(mode, isSystemDark = false)`, night
colours = the same with `isSystemDark = true` through Glance day/night colour providers, so System follows the
launcher's night mode and explicit modes are fixed. Translucent tint tokens are not used.

**Other.** Strings en + ru (label, description, accessibility descriptions); glance-appwidget 1.2.0 added. No
migration and no sync-config change.

**Decisions.** Recorded in the plan's Rulings section (`docs/superpowers/plans/2026-10-05-home-widget.md`).
