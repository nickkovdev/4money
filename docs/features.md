# Feature history

What this fork added on top of upstream [4Money](https://github.com/Radiokot/4money), newest first.
Each stage was designed in [the spec](superpowers/specs/2026-10-01-self-hosted-4money-design.md) and
built from a plan in [docs/superpowers/plans](superpowers/plans/). Stage letters (A, B, C, ...) are the ones
used there. The dates are the days the stage was built (the F6 pull request was merged later, on
2026-10-19). Migrations live in `supabase/migrations`; apply them in file-name order before installing a
build that needs them (see [self-hosting.md](self-hosting.md)).

## 0.33.0 (versionCode 52): open-source readiness, 2026-10-19

- New application id `io.github.nickkovdev.money` (debug: `io.github.nickkovdev.money.debug`). It installs
  as a separate app: sign in again, grant notification access again, add the widget again; device-only
  settings (theme, language, privacy, passcode, auto-booking behaviour) do not carry over. The data is on
  your server, so nothing is lost.
- Cleartext HTTP hosts are no longer hardcoded: list them in `cleartextHosts` in `app/local.properties`
  (comma-separated); empty means HTTPS only.
- CI builds and tests the project on GitHub.
- Documentation: self-hosting, development, 1Money import, this history. Store texts for F-Droid style
  metadata (`fastlane/metadata/android/en-US`).
- Migration: no server change.
- Plan: [open-source readiness](superpowers/plans/2026-10-19-open-source-readiness.md).

## F6: archived subcategories and smart "Remember", 2026-10-06

- Saving a category no longer un-archives its subcategories (bug fix).
- Archived subcategories are hidden from the transfer sheet, suggestions, auto-booking, "Payments to sort"
  buttons and rule pickers (they fall back to the parent category); category sheets show them as one
  "Archived" row when the sum is non-zero; the category editor can archive and unarchive them.
- "Remember" shows the payee's words as chips. The whole payee makes an exact rule, a part makes a
  `contains` rule, and the default part is learned from other payees of the same category.
- Undo of an accepted item removes the rule that accept created.
- Migration: no server change.
- Links: [spec F6](superpowers/specs/2026-10-01-self-hosted-4money-design.md#f6-archived-subcategories-and-smart-payee-remember-designed-2026-10-06-approved-by-the-owner),
  [plan](superpowers/plans/2026-10-06-archived-subcategories-smart-remember.md).

## F5: home screen widget, 2026-10-05

- "Quick entry" widget, 3x1, resizable horizontally: income button, app logo with a badge, expense button.
- The badge is the number of Inbox items waiting for a category; tapping it opens the Inbox tab.
- Income and expense open a small entry flow (category grid, then the keypad sheet) without opening the
  main app; the default account is the one last used for the category.
- Colours follow the app theme; no polling and no background service; the passcode and sign-in still apply.
- Migration: no server change.
- Links: [spec F5](superpowers/specs/2026-10-01-self-hosted-4money-design.md#f5-home-screen-widget-designed-2026-10-05-approved-by-the-owner),
  [plan](superpowers/plans/2026-10-05-home-widget.md).

## F4: Inbox tab and your own notification sources, 2026-10-05

- Inbox is a tab of the bottom bar with a badge; the "Payments to sort" notification opens it.
- Any app can be a source: a wizard builds a template from a sample notification (tap the words that are
  amount, currency, payee, card digits), tests it on recent notifications and maps cards to accounts.
  The SEB Latvia parser stays as a built-in preset.
- Settings, Auto-booking: sources, cards and accounts, payee rules, a test field, and switches to record
  known payees immediately, ask in a notification, and learn from history.
- Recent money-looking notifications are kept in a buffer on the device only, for the wizard.
- Migration: `20261006000000_money_notification_templates.sql` (table `money.notification_templates`);
  also update `deploy/powersync/sync-config.yaml`.
- Links: [spec F4](superpowers/specs/2026-10-01-self-hosted-4money-design.md#f4-inbox-tab-and-user-configured-notification-sources-designed-2026-10-04-approved-by-the-owner),
  [plan](superpowers/plans/2026-10-05-autobook-sources-inbox-tab.md).

## F3: Russian localization and app language, 2026-10-03

- Complete Russian translation next to English; Settings, Language: System, English, Russian.
- Dates and amounts follow the app language (for example "1 234,56 EUR" style grouping and a comma keypad in
  Russian), plurals and notifications are localized.
- Missing translations fail lint.
- Migration: no server change.
- Links: [spec F3](superpowers/specs/2026-10-01-self-hosted-4money-design.md#f3-localization-en--ru-and-app-language),
  [plan](superpowers/plans/2026-10-03-localization-ru.md).

## F2: category sheet and privacy mode, 2026-10-02

- Tap a category in Overview to open a sheet: transaction count, total, share of the period, subcategory
  shares, and shortcuts to add a transaction or filter the Transactions tab.
- Privacy mode (eye button in every tab header): headline amounts show a mask, other amounts show a share
  of the relevant total, and the "Payments to sort" notification shows no amounts.
- Version 0.32.0 (51).
- Migration: no server change.
- Links: [spec F2](superpowers/specs/2026-10-01-self-hosted-4money-design.md#f2-category-sheet-from-overview--privacy-mode-designed-2026-10-02-approved-by-the-owner),
  [plan](superpowers/plans/2026-10-02-category-sheet-privacy.md).

## F: full redesign, Inbox cards, amount-range rules, 2026-10-02 to 2026-10-05

- New design system: four themes (Paper, Midnight, Ember, Aurora), Onest font, redesigned components,
  sheets, pickers and every screen.
- Inbox as swipe cards: accept the suggested category, pick another, or skip.
- Payee rules with amount ranges (for example "under 10" and "from 10" go to different categories) and an
  "Ask me" action: a "Payments to sort" notification with category buttons records the payment without
  opening the app.
- Server behaviour on par with upstream: failed sync uploads are recorded and shown, account positions are
  healed, transfers are safer.
- Migrations: `20261004000000_money_upstream_parity.sql`, `20261005000000_money_payee_rule_ranges.sql`;
  update `deploy/powersync/sync-config.yaml`.
- Links: [brief](redesign/BRIEF.md), [progress](redesign/PROGRESS.md),
  [plan (UI)](superpowers/plans/2026-10-02-ui-1money-style.md),
  [plan (rules and inbox)](superpowers/plans/2026-10-02-bank-notifications-rules-inbox.md).

## E: 1Money-style interface, 2026-10-02

- Light, dark and system theme; Tabler line icons instead of emoji.
- Categories around a donut ring with expense and income totals; Overview tab with a stacked daily bar
  chart, averages and top categories.
- Swipe left or right to change the period; animated numbers and screen transitions; swipe a transaction
  to edit or delete (with undo).
- Transfer sheet with a split header (account and category) and a calculator keypad with currency and
  date keys.
- Migration: no server change.
- Links: [spec E](superpowers/specs/2026-10-01-self-hosted-4money-design.md#e-ui-in-the-style-of-1money),
  [plan](superpowers/plans/2026-10-02-ui-1money-style.md).

## C/D: bank notifications, payee rules, Inbox, 2026-10-02

- A notification listener (no foreground service, no polling) reads payment notifications of a configured
  bank app and processes them on the device.
- A known payee becomes an expense in its category at once; an unknown one lands in the Inbox, where
  accepting it can create a rule. Foreign-currency payments always wait for you. Notifications are
  deduplicated.
- Rules and Inbox screens; auto-booked expenses can be undone.
- Migrations: `20261002000000_money_rules_inbox.sql` (tables `money.payee_rules`, `money.inbox_items`),
  `20261003000000_money_inbox_direction.sql` (incoming payments become incomes).
- Links: [spec C](superpowers/specs/2026-10-01-self-hosted-4money-design.md#c-notification-listener-in-the-4money-fork),
  [spec D](superpowers/specs/2026-10-01-self-hosted-4money-design.md#d-rules-and-inbox),
  [plan](superpowers/plans/2026-10-02-bank-notifications-rules-inbox.md).

## A and B: self-hosted backend and 1Money import, 2026-10-01

- A: the `money` schema (accounts, categories, transfers, currencies, sync errors, daily prices) with RLS
  and the RPCs the app calls, a PowerSync replication role and publication, PowerSync deployment files, and
  the client configured through `app/local.properties` (`supabaseUrl`, `supabaseAnonKey`,
  `supabaseSchema`, `powerSyncUrl`, `authTempCredential`).
- B: `tools/onemoney-import` turns a 1Money backup into SQL ([guide](importing-from-1money.md)).
- Migration: `20261001000000_money_schema.sql`.
- Links: [spec A](superpowers/specs/2026-10-01-self-hosted-4money-design.md#a-backend),
  [spec B](superpowers/specs/2026-10-01-self-hosted-4money-design.md#b-1money-import).
  A and B were built directly from the spec, without a separate plan file.
