# Redesign: progress (state as of 2026-10-02)

Branch `feature/redesign` (from `main` at 50aa715). Not merged. Every commit builds (`assembleDebug`)
and passes `testDebugUnitTest` (195 tests; only the pre-existing upstream
`SternBrocotTreeSearchTest > extensiveTest` fails). Brief: [BRIEF.md](BRIEF.md).

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

## F2 device checklist

- Overview: tap a top category → sheet; "All categories" expands inline, "Show less" collapses; mode switch collapses.
- Sheet: header tint readable in Paper and Midnight; count, total, % bar, period row; subcategory rows sorted, "No subcategory" bucket.
- Sheet "Expense"/"Income" → transfer sheet with the category preselected (or the account picker); "Transactions" → History tab with the category chip, clearing works.
- Eye button on all four tabs; toggle persists across app restarts; Inbox screens follow it.
- Private: balances/totals/averages `•••`; categories tiles and transactions as %, transfers between accounts `•••`; Overview day labels and top amounts hidden; transfer keypad still shows the amount being typed.
- "Payments to sort" notification while private: title is the payee only.
