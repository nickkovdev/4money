# 4Money UI redesign: brief for a design agent

## Goal
The app works but looks dated: emoji instead of icons, thin grey outlines around every
control, underlined text instead of tabs, old system dialogs, weak typography.
Make it look like a modern, calm finance app (reference feel: 1Money / Revolut / Monzo —
dark-first, soft surfaces, clear hierarchy, big confident amounts) without changing behaviour.

## Inputs
- Screenshots of every screen (before the redesign) and an `INDEX.md` with the problems per screen
  exist only on the owner's laptop (`dev/4money-ui-screens/`): they contain real financial data and
  never go into the repo or any public place. The top problems are summarised below.
- This repo is a PUBLIC GitHub fork (GPLv3). Read `CLAUDE.md` and `docs/HANDOFF.md` first;
  progress so far: [PROGRESS.md](PROGRESS.md).

## Stack (what draws the UI)
- Jetpack Compose, `foundation` only — **no Material 3** today.
- Primitives from Compose Unstyled (`com.composeunstyled`: `Text`, `Icon`, buttons, sheets).
- Own design system in `app/src/main/java/ua/com/radiokot/money/uikit/`:
  `theme/MoneyTheme.kt`, `theme/MoneyColors.kt` (color tokens, light + dark),
  `TextButton.kt` (the outlined button used everywhere), toggles, `chart/`.
- Screens live per feature: `accounts/view`, `categories/view`, `transfers/view` (transfer sheet,
  keypad, counterparty selection), `transfers/history/view` (Transactions, `PeriodBar.kt`),
  `overview/view`, `preferences/view`, `inbox/view`, `home/view/HomeActivity.kt` (bottom bar).
- Icons: Tabler Icons (MIT), imported as vector drawables by `tools/icons/import_tabler.py`
  (`ic_tabler_*`, `*_itemicon`). Add new icons through that script, never hand-copy SVGs
  from unknown sources. No 1Money assets (proprietary).
- Navigation: navigation-compose; DI: Koin.

First decision to make with the owner: keep building on Compose Unstyled with a proper token
system in `uikit/` (smaller dependency, full control), or add Material 3 and theme it.
Recommend one with trade-offs before writing code.

## Top problems to solve (from INDEX.md)
1. Emoji as icons/controls (❌ ✔️ ⭕ 🔽 ▶️ ➕ ✏️ ⚖️ 📃 📩 📨 ↔️ 👛 🐹, ⭕/🔴 radios) → Tabler icons.
2. Outlined buttons, keypad keys, chips, pills, square text fields → filled/tonal surfaces,
   rounded shapes; enabled buttons must not look disabled in dark theme.
3. Color semantics: expense amounts shown in income green; income and account→account
   transfers share one teal; light theme category circles jump to full saturation.
4. Old dialogs (square grey cards, ALL-CAPS YES/NO, date picker) → themed dialogs/sheets.
5. Underlined text used as tabs/titles (Accounts/Total, Icon/Color, picker titles) → real
   segmented control/tabs.
6. Insets: transfer sheet header under the status bar with the keyboard open; picker and logo
   grids under the navigation bar; income categories scattered around the ring.
7. Navigation context: Settings has no title/back and keeps a bottom tab highlighted;
   "More…" on Overview jumps to Categories; Activity filters clear only with system BACK.
8. Typography: repeated month/year in day headers, italic notes, ALL-CAPS payee names
   dominating rows, "Category (Subcategory)" colliding with amounts, truncated labels.
9. Inconsistent sheets/pickers: cream account sheet in light theme, solid colored block in the
   category sheet, near-identical color swatches, unsorted currency list, bank-formatted inbox
   amounts ("18,90 EUR" → app amount format).

## Theme mockups (approved direction to pick from)
Canvas: https://claude.ai/artifact/KEA2TwZvtvC6U6qHwGgCzo — ONE component system, four palettes
(Midnight, Ember, Paper, Aurora), each showing Accounts, Categories, the expense sheet, the new
Inbox cards and the amount-rules screen. The owner picks a palette (or a mix); the components
stay identical across themes, so a theme is only a `MoneyColors` token set. Implement the chosen
ones as selectable themes in Settings → Appearance (light = Paper-style, dark = the chosen dark).

## New features to build together with the redesign
1. **Inbox as swipeable cards (Tinder-like)**, a dedicated screen for sorting pending bank items:
   - one card per pending item: payee, amount (expense/income colour), time + account, the
     suggested category with the reason ("Up to 10 € at the fuel station → Food", "Remembered payee"),
     2–3 alternative category chips (most used for this payee);
   - swipe right = accept the suggestion, left = skip (stays pending, goes to the end),
     up or the grid button = open the full category picker; buttons duplicate every gesture
     (accessibility), haptic tick on commit, Undo snackbar for the last action;
   - card tilts with the drag and shows a stamp of the action it will commit; progress bar;
     "All sorted" empty state. Uses `CompleteInboxItemUseCase`/`InboxTransferPrefill`; an accepted
     card creates the transfer directly when the amount is in the account currency, otherwise
     opens the prefilled transfer sheet.
2. **Payee rules by amount** (a fuel station: a snack vs. fuel for the car or the motorbike):
   - a payee can have several rules with amount ranges (`min`/`max`, inclusive/exclusive edges)
     and a target: a category, or **Ask**;
   - matching picks the range containing the amount; no range → the payee's plain rule;
   - needs a server migration on `money.payee_rules` (e.g. `min_amount`, `max_amount`,
     `action text check (action in ('record','ask'))`), the sync-config select, `DbSchema`,
     `PowerSyncPayeeRuleRepository`, `PayeeRuleMatcher` + tests. The owner applies migrations
     to the shared production DB himself or OKs them first — never apply without asking.
     Upload order: migration before installing the build that writes the new columns.
   - rules screen: per payee, the ranges as rows (`< 10 € → Food`, `10–35 € → Ask me`,
     `> 35 € → Car`), "Add range".
3. **Ask in the notification**: for an "Ask" match (or no rule) post the app's own notification
   "Fuelstop · −18.40 €" with up to 3 action buttons = the payee's most used categories; tapping
   one records the expense via a `BroadcastReceiver` without opening the app (local write only,
   no network, notification channel "Payments to sort", low importance by default). The inbox
   item stays pending if the notification is dismissed. Requires `POST_NOTIFICATIONS` on 13+.

## Constraints
- Behaviour, data and sync logic stay untouched; this is presentation only. If a fix needs
  logic (e.g. sorting), keep it minimal and covered by a unit test.
- Both light and dark themes, plus "follow system". Respect edge-to-edge insets.
- Keep the GPL header on new files (copy from an existing file). Match the code style around.
- Battery matters: no infinite animations, no heavy blur on every frame.
- Work in phases, each buildable and installable:
  1. Tokens: colors, typography scale, shapes, spacing, elevation — in `uikit/theme`.
  2. Components: button (filled/tonal/text), icon button, chip, segmented tabs, text field,
     list row, section header, dialog, sheet scaffold, keypad key — in `uikit/`, with previews.
  3. Screens one by one, replacing ad-hoc styling with the components. Order: bottom bar +
     Accounts → Categories → Transfer sheet → Transactions → Overview → Settings → Inbox → edit screens.
- Show the owner a mockup/plan for phase 1–2 and get approval before restyling screens.

## Build and check on the phone
Build setup per machine: [../HANDOFF.md](../HANDOFF.md), "Build setup".
```bash
export JAVA_HOME=<jdk 17+> ANDROID_HOME=<android sdk>
./gradlew.bat testDebugUnitTest   # only SternBrocotTreeSearchTest > extensiveTest fails (pre-existing)
./gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/ua.com.radiokot.money.debug-*-debug.apk
adb shell monkey -p ua.com.radiokot.money.debug -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > shot.png
```
Keep before/after screenshots outside the repo (they show real data) and compare.

Phone safety (real data, synced to a shared production server): never save, delete, archive,
revert or swipe rows; never log out, set a passcode, change the primary currency or
notification access. Check `dumpsys window | grep mCurrentFocus` before every screenshot.
Never commit `local.properties`, `app/local.properties`, `schema.sql`, screenshots or personal data.
