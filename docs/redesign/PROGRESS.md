# Redesign: progress (state as of 2026-10-05)

Branch `feature/redesign` (from `main` at 50aa715). Not merged. Every commit builds (`assembleDebug`)
and passes `testDebugUnitTest` (195 tests at the time; only the pre-existing upstream
`SternBrocotTreeSearchTest > extensiveTest` fails). The F4 branch `feature/autobook-sources-inbox-tab`
head builds, passes `lintDebug` and `testDebugUnitTest` (412 tests, the same single upstream failure).
Brief: [BRIEF.md](BRIEF.md).

**Nothing on this branch has been checked on the phone yet.**

## Done (oldest first)

| Commit | What |
|---|---|
| f1fe765 | Phase 1, tokens: `uikit/theme` MoneyColors (Midnight, Ember, Paper, Aurora + legacy aliases), MoneyTypography (Onest variable font `res/font/onest.ttf`, OFL in `assets/licenses/onest-OFL.txt`), MoneyShapes/MoneySpacing. `theme/view/MoneyAppTheme` picks the palette from `ThemeMode` (Ember, Aurora added). New Tabler icons via `tools/icons/import_tabler.py`. |
| 66f7de7 | Phase 2, components in `uikit/`: MoneyButton (Filled/Tonal/Danger/Text), MoneyIconButton, MoneyChip, SegmentedControl, MoneyTextField/MoneyPickerField/FieldLabel, ListGroup/ListRow/ListDivider/SectionHeader/IconTile/RowChevron, MoneyDialog, SheetScaffold/SheetHandle, KeypadKey, MoneySwitch/SelectionMark (emoji toggles removed), PressOverlayIndication, EmptyState; PeriodBar. |
| 018f940 | Bottom bar (accent pill, nothing highlighted on Settings), profile button in each tab header (`home/view/HomeProfileButton.kt`, `HomeTabHeader.kt`), Accounts + account action sheet, amount keyboard. |
| 6b5dd42 | Categories: ring on top, Expenses/Income segmented switch, even 4-column grid; category sheet. |
| 2ac763f | Transfer sheet (IME/status-bar fix, colours by kind), counterparty picker, Compose date picker (`transfers/view/DatePickerDialog.kt`). |
| f16f90e | Transactions: grouped rows, day headers, filter chip with clear, In/Out tiles, themed revert dialog, all-caps memo softening (`MemoCasing.kt`). |
| 7770e91 | Overview. |
| e1c8b4a | Settings: title + back, list groups, theme rows (System/Paper/Midnight/Ember/Aurora), sign-out dialog. |
| ae75a7d | Inbox (app amount format) and payee rules. |
| c3175d9 | Editors, account type sheet, currency list (sorted), logo picker, passcode keypad, archived accounts, auth screens; old outlined `TextButton` deleted; item colours keep their shade per theme. |
| 5b60431 | Phase 4: Inbox swipe cards (`inbox/view/InboxCardsScreen.kt`, `InboxCardsViewModel.kt`, logic `InboxCardSuggester`, `InboxCardAcceptance` + tests), "Sort as cards" in the Inbox. |
| 1cbf772 | Phase 5 data: migration `supabase/migrations/20261005000000_money_payee_rule_ranges.sql` (**not applied**), sync-config, DbSchema, `saveRangeRule`, `PayeeRule.amountRange/action`, range-aware `PayeeRuleMatcher` (narrowest range containing the amount, else the plain rule), `AutoExpenseResolver` AskRequested. |
| f130a2a | Phase 5 UI: rules grouped per payee with range rows, range editor (From inclusive / Under exclusive; target Ask me / category / picker). |
| 3313f76 | Phase 5: "Payments to sort" notification (`inbox/ask/PaymentQuestionNotifier.kt`, `PaymentQuestionReceiver.kt`): up to 3 category buttons record the expense without opening the app; POST_NOTIFICATIONS asked when an Ask range is saved. |
| 77593b1 | Owner decision: a card suggested from history (no rule yet) has a Remember toggle: on when the payee's history has one category, off when mixed, with a "set up amount rules" link. Undo removes only a rule created by that accept (`LearnedRule`, `CardRememberTest`). |
| F2 (branch `feature/category-sheet-privacy`) | Category sheet from Overview: tap a category (top list or the inline-expanded "All categories") → sheet in the category colour with N transactions, total, share of the period and the period total, subcategory shares, Expense/Income and Transactions actions. Privacy mode: eye button next to the profile button in every tab header (stored in prefs `privacy`), headline amounts `•••`, the rest as % of the relevant total, no amount in the "Payments to sort" notification. Plan with rulings: `docs/superpowers/plans/2026-10-02-category-sheet-privacy.md`. |
| F3 (branch `feature/localization-ru`) | Localization: English base + complete Russian, Settings → Language (System / English / Русский) via per-app locales, locale-aware dates (nominative month headers, genitive day lines) and amounts (ru: "1 234,56 €", comma keypad), plurals, notifications. Plan with rulings: `docs/superpowers/plans/2026-10-03-localization-ru.md`. |
| F4 (branch `feature/autobook-sources-inbox-tab`) | Inbox tab and user-configured notification sources: the Inbox is the centre tab of the bottom bar (badge with the pending count; the "Payments to sort" notification opens it; `InboxActivity` removed). Settings → Auto-booking (`AutoBookActivity`): Sources (apps with a notification access switch, SEB Latvia as a built-in preset), a setup wizard (add app → pick a sample notification → teach a template by tapping words as amount / currency / payee / card / varies → test against recent notifications → card accounts → behaviour switches → Done), Cards and accounts, Payee rules, Test text. Templates are synced rows (`money.notification_templates`); the listener only reacts to packages in the active set (cached in prefs); recent money-like notifications are buffered on the device only (`noBackupFilesDir`, 50 entries, 7 days). Plan with rulings: `docs/superpowers/plans/2026-10-05-autobook-sources-inbox-tab.md`. **Needs a migration and a sync-config update before the install, see below.** |
| F5 (branch `feature/home-widget`) | Home screen widget "Quick entry" (Glance, 3×1, resizable horizontally): three round buttons, ↑ income, the app logo with a badge = pending Inbox count (tap → Inbox tab), ↓ expense. Income/expense open `QuickTransferActivity`, a translucent activity in its own task: category grid → transfer sheet, default account = last used for the category, else the most used; ✓ saves and returns to the launcher. Badge updated on inbox changes, sign-out and theme change (debounced, no polling, no services); colours follow the app theme. Locked/signed-out states go through the passcode/sign-in. EN + RU. No migration or sync-config change. Plan with rulings: `docs/superpowers/plans/2026-10-05-home-widget.md`. |

## Install order (important)

Builds from **1cbf772 on** write the new `payee_rules` columns. Installing one before the server has
them makes rule uploads fail into `money.sync_errors`. Order:

1. Owner OK, then apply `supabase/migrations/20261005000000_money_payee_rule_ranges.sql` (command in
   [../HANDOFF.md](../HANDOFF.md), Infrastructure).
2. Copy `deploy/powersync/sync-config.yaml` to `~/4money-powersync/`, `docker restart 4money-powersync`,
   check `pg_replication_slots` has exactly one active `powersync_*` slot.
3. Only then install a build of `feature/redesign`.

To check phases 1–4 on the phone before the migration, build commit **5b60431** (e.g. `git worktree add
../4money-verify 5b60431`, copy both `local.properties` files into it, build there).

### Install order for F4

Builds of `feature/autobook-sources-inbox-tab` read and write the new table `money.notification_templates`.
Installing one before the server has the table and the sync rule breaks template sync: the uploads of
taught templates fail and are dead-lettered into `money.sync_errors`, so they never reach other devices,
and the next sync checkpoint then removes those locally taught templates from the phone too (the server
has no such rows), so the wizard's work is lost. The owner applies both steps; nothing here is run
automatically. Order:

1. Owner OK, then apply `supabase/migrations/20261006000000_money_notification_templates.sql` (command in
   [../HANDOFF.md](../HANDOFF.md), Infrastructure). It creates the table and adds it to the `atomic_crud`
   whitelist. The earlier `20261005000000_money_payee_rule_ranges.sql` must already be applied.
2. Copy `deploy/powersync/sync-config.yaml` (new `notification_templates` query in the user bucket) to
   `~/4money-powersync/`, `docker restart 4money-powersync`, check `pg_replication_slots` has exactly one
   active `powersync_*` slot.
3. Only then install the new APK. After the first sync the Sources screen shows the SEB Latvia preset
   (it works without any template rows).

## Next steps

1. Phone check of phases 1–4 (build of 5b60431): screenshot every screen in Midnight and Paper; check
   the Onest font everywhere, account list while reordering, transfer sheet with the keyboard open, date
   picker over the sheet, subcategory dialog, inbox card drag and stamp. Fix, commit. Restore the phone's
   theme to System afterwards.
2. Migration + PowerSync (above), then install the branch head and check phase 5: amount ranges,
   Ask notification buttons, Remember toggle, Undo.
3. Merge `feature/redesign` into `main` when the owner is happy.

## Open

- Decided (owner, 2026-10-02): the "Payments to sort" notification fires only for "Ask me" rules;
  payees without a rule just wait in the Inbox.
- Transactions: no daily totals in day headers (needs a per-day sum).
- Overview bar chart has no axis/gridlines.
- Privacy mode does not mask free-text memos: foreign-currency Inbox prefills write "Payee · 18,90 USD"
  into the memo (`inbox/logic/InboxTransferPrefill.kt`), which stays visible in Transactions while private.
- Ember/Aurora: the first frame shows the Midnight window colour from XML before `MoneyAppTheme` repaints.

## Backlog (owner ideas, later)

- Privacy mode: done in F2 (see above).
- Home screen widget: done in F5 (see above).

## F2 device checklist

- Overview: tap a top category → sheet; "All categories" expands inline, "Show less" collapses; mode switch collapses.
- Sheet: header tint readable in Paper and Midnight; count, total, % bar, period row; subcategory rows sorted, "No subcategory" bucket.
- Sheet "Expense"/"Income" → transfer sheet with the category preselected (or the account picker); "Transactions" → History tab with the category chip, clearing works.
- Eye button on all four tabs; toggle persists across app restarts; Inbox screens follow it.
- Private: balances/totals/averages `•••`; categories tiles and transactions as %, transfers between accounts `•••`; Overview day labels and top amounts hidden; transfer keypad still shows the amount being typed.
- "Payments to sort" notification while private: title is the payee only.

## F3 device checklist

- Settings → Language: switch System / English / Русский and back; every open screen recreates in the new language.
- In Russian walk all four tabs, Settings, Inbox list and cards, Rules, the transfer sheet, the date picker and the category sheet: nothing left in English except user data and theme names.
- Month headers read "Октябрь 2026", day lines like "1 октября".
- Amounts show "1 234,56 €" (comma decimal); the keypad decimal key is "," and typing "1234,5" gives 1 234,50.
- Plurals: 1 / 3 / 5 / 21 → операция / операции / операций / операция (category sheet transaction count, Rules rule count, Settings Inbox row, inbox card reasons).
- "Payments to sort" notification arrives in Russian.
- Privacy mode in Russian: `•••` and % as before.
- System with a device language other than English or Russian (e.g. Latvian): English strings, no crash.

## F4 device checklist

Do the install order above first. Use only invented/test data in anything you screenshot or commit.

- Notification access: Settings → Auto-booking → Sources shows the access state; grant it (Android 13+: App info → ⋮ → "Allow restricted settings" first), then revoke it in system settings and come back: the screen shows access missing, the recent-notification buffer is cleared and the listener stops (after re-granting, check that it reconnects).
- SEB preset: a real card payment notification still becomes a pending Inbox item (or an auto-recorded expense for a known payee) within a second, card row shown, no network needed; turning the SEB switch off stops it, on resumes it. Other SEB kinds (account payment out/in) still parse; Google Wallet still ignored.
- Wizard, add app: Sources → Add app → pick an installed app (real icons shown) → pick a recent notification (or a pasted sample) → teach: tap words as amount, currency, payee (card optional, "Varies" for changing words) → Test shows the matches with extracted fields → map card(s) / no-card account → behaviour switches → Done. The new source appears with its kinds; Done with nothing taught is blocked.
- Then post a matching notification from that app: it lands in the Inbox with the right amount, payee and account. A second, different payee of the same kind also matches. Check the Teach FlowRow / role chooser layout and the account-pick round trip from the wizard.
- Teach another kind on an existing source starts at step 3; Set up on a source without kinds too. Source switch off: the app's notifications are no longer processed; on again: they are.
- Inbox tab: centre tab with a badge equal to the pending count (placement and visibility at 1 and 12); the "Payments to sort" notification opens `HomeActivity` on the Inbox tab, also when the app is already open (singleTop). Rules reachable from the tab. Insets correct on the Inbox and Rules screens.
- Accept a pending item with Remember, then Undo from the Inbox tab: the transaction is removed and the rule learned by that accept is gone (a rule that existed before stays). "Recorded today" lists the day's done items, each with Undo.
- Privacy mode (eye button on): amounts in the Inbox tab, Sources, wizard legend/preview/test rows and Cards and accounts are `•••`. Known limit: the raw sample text in the wizard's sample and teach steps is shown unmasked on purpose.
- Test text (Auto-booking → Test text): paste a notification (check IME and scrolling), see which source and kind match and the extracted fields; no inbox item is created.
- Cards and accounts: card → account mapping, the no-card account per source, the three behaviour switches (record known payees, ask in the notification, learn from history); same values as wizard step 6. Resolution order when recording: card mapping, no-card source account, rule account, most used.
- Sign out and back in (without killing the app): no crash; sign-out clears the recent-notification buffer (a device-wide file, cleared by the sign-out use case) and closes the session scope, which stops the old source registry; sign-in creates the new registry right away (off the main thread), so the cached active packages follow the new account's templates, including ones synced from another device, before any screen is opened; the listener keeps working after sign-in.
- Launcher icons of installed apps appear in the app picker (package visibility via `<queries>`), letter tiles otherwise.
- RU locale: the whole flow in Russian (Settings → Language), plurals for source and kind counts and the badge, nothing left in English except app names and user data; then switch back.

## F5 device checklist

- Add the widget from the launcher's widget picker: label "Quick entry" / "Быстрый ввод", description and preview shown; default 3×1, resize horizontally to 2 and 4+ cells: circles spread, nothing clipped.
- Theme: System (toggle the phone's dark mode, the widget follows), Paper, Midnight, Ember, Aurora: colours change within a second of switching in Settings → Appearance.
- Badge: equals the Inbox tab badge; hidden at 0; post a test notification from a source, the badge increments within ~1 s without opening the app; sorting in the app decrements it; 100+ shows "99+" (only if such data exists, else skip).
- Centre tap: the app opens on the Inbox tab (cold start and app already open).
- ↓: dimmed launcher, Expense category grid; pick a category, the transfer sheet opens with that category and its last used account (a category never used: the most used account); type an amount, ✓: saved, back on the launcher; the transfer is in History.
- ↑: Income categories; same flow; the income account is the destination.
- Back / tap outside on the grid and on the sheet: back on the launcher, nothing saved; the quick entry does not show in Recents; opening the app afterwards shows the app's own last screen, not the quick entry.
- Passcode on, app locked (background > threshold): ↓ shows the unlock screen first, then the grid; cancelling unlock returns to the launcher.
- Signed out: badge hidden; ↓ opens the sign-in screen; after signing in the badge reappears.
- Privacy mode on: category amounts and balances in the grid/sheet masked as in the app; the keypad shows the typed amount.
- Russian: widget label, description and TalkBack descriptions ("Добавить расход", "Открыть «Входящие», ждут 3 платежа").
- Battery: no new periodic work (`adb shell dumpsys jobscheduler | grep radiokot` shows only the existing BackgroundSync/CurrencyPricesUpdate) and no persistent service.
