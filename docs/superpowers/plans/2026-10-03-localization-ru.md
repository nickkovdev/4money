# Russian localization and app language setting — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** English (base) + complete Russian UI, a Settings → Language choice (System / English / Русский)
through the AndroidX per-app language API, locale-aware dates and amounts.

**Architecture:** All user-visible text moves to `res/values/strings.xml` (+ `res/values-ru/strings.xml`),
Compose reads it with `stringResource`/`pluralStringResource`; view models that build text emit a small
`ViewText` value resolved in the UI. Dates go through one pure helper (`ViewDateFormats`, java.time with the
app `Locale`), amounts through the existing `ViewAmountFormat` (already locale-driven from
`LocalConfiguration`). The language is stored by AppCompat (`setApplicationLocales`, `autoStoreLocales`).

**Tech Stack:** Kotlin, Compose (foundation + Compose Unstyled), AppCompat 1.8, Koin, java.time, JUnit4.

**Spec:** owner-approved design in the controller brief, recorded as section F3 of
`docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` (written in Task 9).

## Global Constraints

- Base language English (`res/values`), Russian in `res/values-ru`; every key in both (lint `MissingTranslation` = error).
- `resConfigs "en", "ru"`; `androidResources { generateLocaleConfig = true }` + `app/src/main/res/resources.properties` with `unqualifiedResLocale=en`.
- Language switch: `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`, empty tag = System; `AppLocalesMetadataHolderService` with `autoStoreLocales=true` in the manifest.
- Language names are shown in their own language: "English", "Русский" (`translatable="false"`).
- Do NOT translate user data (account/category titles, memos, payees) nor the SEB texts the parser matches (`inbox/logic/SebLatvia*` untouched). Log/exception/`error()`/`require()` messages stay English literals. `@Preview` / PreviewParameterProvider sample data may stay literal.
- No string concatenation for sentences: format args (`%1$s`, `%1$d`) and `<plurals>` for counts (ru needs `one`, `few`, `many`, `other`).
- Keys: `<area>_<meaning>` snake_case (`settings_title`, `accounts_total`, `inbox_card_skip`); shared ones `common_*` (Task 1).
- XML escaping: `'` → `\'`, a literal `%` in a string with format args → `%%`, `&` → `&amp;`, `<` → `&lt;`. Non-breaking/narrow spaces may stay literal.
- Theme names Paper / Midnight / Ember / Aurora are proper names: same in both languages; their subtitles are translated.
- Privacy texts `•••`, `—`, `<1%`, `N%` are language-neutral and stay as they are.
- GPL header on new files (copy from an existing file). Keep code style around.
- Build/test (Git Bash): `export JAVA_HOME='E:\dev\jdk-21.0.12.1+1' ANDROID_HOME='E:\dev\android-sdk'`, then
  `./gradlew.bat testDebugUnitTest` (only pre-existing `SternBrocotTreeSearchTest > extensiveTest` may fail),
  `./gradlew.bat assembleDebug`, `./gradlew.bat lintDebug`.

### Russian glossary (use consistently)

| en | ru | en | ru |
|---|---|---|---|
| Accounts | Счета | Save | Сохранить |
| Categories | Категории | Cancel | Отмена |
| Transactions / History | История (tab), операции (items) | Delete | Удалить |
| Overview | Обзор | Edit | Изменить |
| Expense / Expenses | Расход / Расходы | Undo | Отменить |
| Income | Доход / Доходы | Back | Назад |
| Transfer | Перевод | Archive / Archived | В архив / Архив |
| Inbox | Входящие | Subcategory | Подкатегория |
| Rules / payee rules | Правила / правила получателей | Payee | Получатель |
| Settings | Настройки | Amount | Сумма |
| Balance | Баланс | Note / memo | Заметка |
| Total | Итого | Today / Yesterday | Сегодня / Вчера |
| Primary currency | Основная валюта | Passcode | Код-пароль |
| Ask me | Спросить | Skip | Пропустить |
| Remember | Запомнить | Sign out | Выйти |
| transaction (count noun) | операция / операции / операций | Language / System | Язык / Как в системе |

Tone: short, calm, finance-app; imperative buttons ("Сохранить", not "Сохраните").

## Review Focus

1. A device language other than en/ru with "System" (e.g. Latvian): strings fall back to English, amounts/dates keep the device format; nothing crashes. (Task 1 test: `AppLanguage.fromTags("lv")` → `System`.)
2. Russian amounts: grouping is a (no-break) space and the decimal is a comma; the keypad's decimal key shows "," and typed "1234,5" parses back to the same value. (Task 3 tests.)
3. Russian month names: standalone nominative in headers ("Октябрь 2026", capitalised), genitive in day lines ("1 октября"). (Task 2 tests.)
4. Counts in Russian: 1 операция, 3 операции, 5 операций, 21 операция. (Task 5 uses `<plurals>` with all four ru quantities; lint `MissingQuantity` checks.)
5. Switching the language recreates every open screen (all activities are `AppCompatActivity`: `MoneyAppActivity` + `AuthActivity`), and notifications posted afterwards use the new language (Task 8 builds notification text from `context.getString` at post time, not cached).

---

### Task 1: Language plumbing, Settings → Language, `ViewText`, lint gate

**Files:**
- Modify: `app/build.gradle` (`resConfigs "en", "ru"`, `androidResources { generateLocaleConfig = true }`, `lint { error += "MissingTranslation" }` — Groovy: `lint { error "MissingTranslation" }`)
- Create: `app/src/main/res/resources.properties` (`unqualifiedResLocale=en`)
- Create: `app/src/main/res/values-ru/strings.xml` (Russian for the 5 existing translatable strings + everything added here)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml` (service below)
- Create: `app/src/main/java/ua/com/radiokot/money/preferences/logic/AppLanguage.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/uikit/ViewText.kt`
- Modify: `preferences/view/PreferencesScreen.kt`, `PreferencesScreenViewModel.kt` (Language section only)
- Test: `app/src/test/java/ua/com/radiokot/money/preferences/logic/AppLanguageTest.kt`

**Interfaces — Produces:**
```kotlin
enum class AppLanguage(val tag: String) {   // tag "" = follow the system
    System(""), English("en"), Russian("ru");
    companion object {
        /** From AppCompatDelegate.getApplicationLocales().toLanguageTags(), e.g. "ru-RU,en". First tag wins, by language prefix. */
        fun fromTags(tags: String): AppLanguage
    }
}
@Immutable sealed interface ViewText {
    data class Plain(val text: String) : ViewText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : ViewText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : ViewText
}
@Composable fun ViewText.resolve(): String            // args that are ViewText are resolved recursively
fun ViewText.resolve(context: Context): String         // for Toasts / non-Compose
```
Common keys: `common_back`, `common_cancel`, `common_save`, `common_delete`, `common_edit`, `common_close`, `common_done`, `common_undo`.

- [ ] **Step 1: Failing test** `AppLanguageTest`: `fromTags("")`→System, `"en"`→English, `"en-GB"`→English, `"ru-RU,en"`→Russian, `"lv"`→System, `"uk,ru"`→System (first tag only).
- [ ] **Step 2:** `./gradlew.bat testDebugUnitTest --tests '*AppLanguageTest'` → fails (no class).
- [ ] **Step 3: Implement** `AppLanguage` (`tags.split(',').firstOrNull()?.substringBefore('-')?.lowercase()` matched against `English.tag`/`Russian.tag`, else System) and `ViewText`.
- [ ] **Step 4: Manifest** inside `<application>`:
```xml
<service
    android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
    android:enabled="false"
    android:exported="false">
    <meta-data android:name="autoStoreLocales" android:value="true" />
</service>
```
- [ ] **Step 5: Settings** — a "Language" `SectionHeader` + `ListGroup` right after Appearance with 3 rows built like the theme rows (selection mark): `settings_language_system` ("Follow the system" / "Как в системе"), `language_english` = "English", `language_russian` = "Русский" (both `translatable="false"`, values only). VM: `val language: StateFlow<AppLanguage>` initialised from `AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())`; `onLanguageClicked(lang)` sets the state and calls `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))` (main thread; AppCompat recreates the activities). Strings of this section only go to resources here; the rest of Settings is Task 9.
- [ ] **Step 6:** `values-ru/strings.xml`: ru for `app_name` (keep "4Money"? → mark `app_name` `translatable="false"`), `template_category_subcategory` (non-translatable too: it is `%1$s (%2$s)`), shortcuts ("Добавить операцию", "Добавить доход", "Добавить расход"), `bank_notification_listener_label` ("Банковские платежи в расходы"), common_* and the language keys.
- [ ] **Step 7:** `testDebugUnitTest`, `assembleDebug`, `lintDebug` pass (MissingTranslation now an error). Note any pre-existing lint errors unrelated to this task in the report instead of silencing them.
- [ ] **Step 8: Commit** "Add the app language setting and the Russian resources skeleton".

### Task 2: Locale-aware dates (`ViewDateFormats`)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/transfers/view/ViewDateFormats.kt`
- Modify: `transfers/view/ViewDate.kt`, `transfers/view/TransferList.kt` (HeaderItem), `transfers/history/view/ViewHistoryPeriod.kt`, `transfers/view/DatePickerDialog.kt` (month header + weekday initials + "Previous/Next month"), any other `localDate.toString()`/`"${date} $time"` shown to the user (e.g. `inbox/view/InboxCardsViewModel.kt` ~l.308 date line — return `ViewText`, or move formatting to the screen).
- Test: `app/src/test/java/ua/com/radiokot/money/transfers/view/ViewDateFormatsTest.kt`

**Interfaces — Produces:**
```kotlin
object ViewDateFormats {
    fun monthYear(date: kotlinx.datetime.LocalDate, locale: Locale): String   // "October 2026" / "Октябрь 2026" ("LLLL yyyy", first char titlecased in locale)
    fun weekdayDay(date: LocalDate, locale: Locale): String                  // "Wednesday, 1" / "Среда, 1"   ("EEEE, d", titlecased)
    fun dayMonth(date: LocalDate, locale: Locale, today: LocalDate): String  // "1 October" / "1 октября"; other year: "1 October 2025" / "1 октября 2025"
    fun time(dateTime: LocalDateTime, locale: Locale): String                // "HH:mm"
}
@Composable fun rememberAppLocale(): Locale = LocalConfiguration.current.locales[0]
```
Resource keys: `date_today`, `date_yesterday`, `date_today_at` ("Today %1$s"), `history_period_entire_time` ("The entire time" / "За всё время"), `date_picker_previous_month`, `date_picker_next_month`. `ViewDate.getText()` null branch → `dayMonth`.

- [ ] **Step 1: Failing tests** (Locale.ENGLISH and `Locale.forLanguageTag("ru")`, date 2026-10-01 a Thursday — verify the weekday with java.time when writing the test):
  `monthYear` → "October 2026" / "Октябрь 2026"; `weekdayDay` → "Thursday, 1" / "Четверг, 1"; `dayMonth(today=2026-10-03)` → "1 October" / "1 октября"; `dayMonth(2025-12-31, today=2026-10-03)` → "31 December 2025" / "31 декабря 2025"; `time(...14:05)` → "14:05".
- [ ] **Step 2:** run `--tests '*ViewDateFormatsTest'` → fails.
- [ ] **Step 3:** implement with `DateTimeFormatter.ofPattern(pattern, locale)` on `toJavaLocalDate()`, cache formatters per locale is unnecessary (keep simple, call sites `remember(locale)`); remove `DayOfWeekNames.ENGLISH_FULL` / `MonthNames.ENGLISH_FULL` usages.
- [ ] **Step 4:** tests pass; `assembleDebug`, `lintDebug`.
- [ ] **Step 5: Commit** "Format dates in the app language".

### Task 3: Locale-aware amounts

**Files:**
- Test: `app/src/test/java/ua/com/radiokot/money/currency/view/ViewAmountFormatTest.kt` (extend), `AmountInputStateTest.kt` (extend if it builds the state from a format)
- Modify: `inbox/view/AmountRangeText.kt` (`describeRange` gets `locale: Locale` and a `RangeTexts` of format strings, see below), its callers and its test; `inbox/ask/PaymentQuestionNotifier.kt` amount text (format with `NumberFormat`/`ViewAmountFormat` for `context.resources.configuration.locales[0]`, not `toPlainString`). Only the amount formatting here; the notification's words are Task 8.
- `currency/view/rememberViewAmountFormat.kt` already uses `LocalConfiguration` — keep; make sure nothing else formats user-visible amounts with `toPlainString()`/`toString()` (grep `toPlainString`).

**Interfaces — Produces:**
```kotlin
class RangeTexts(val between: String /* "%1$s–%2$s" */, val upTo: String /* "Up to %1$s" */, val under: String,
                 val from: String, val over: String, val any: String)
fun describeRange(range: AmountRange, currencyCode: String?, locale: Locale, texts: RangeTexts): String
@Composable fun rememberRangeTexts(): RangeTexts   // from resources inbox_range_*
```
Keys: `inbox_range_between`, `inbox_range_up_to`, `inbox_range_under`, `inbox_range_from`, `inbox_range_over`, `inbox_range_any` (ru: "до %1$s", "меньше %1$s", "от %1$s", "больше %1$s", "Любая сумма"). Numbers in ranges: `NumberFormat.getNumberInstance(locale)` with `maximumFractionDigits = 2`.

- [ ] **Step 1: Failing tests:** `ViewAmountFormat(Locale.ENGLISH)(123456789 cents, EUR)` text == "1,234,567.89 €"; ru: decimal ',' and grouping `Character.isSpaceChar(...)` (assert via the format's `groupingSeparator`, build the expected string from it); `formatInput`/`parseInput` round-trip "1234,5" in ru → 123450 cents; negative ru value starts with the minus sign; `privateText("12%", ...)` unchanged in ru. `describeRange` en "Up to 10 €", ru "до 10,5 €" (ru texts passed in).
- [ ] **Step 2:** run, see the new `describeRange` signature fail to compile / ru expectations fail.
- [ ] **Step 3:** implement; adjust callers (`RulesScreen`, `InboxCardsViewModel` reason text — pass a `ViewText` or format in UI; keep it compiling, the words are moved in Tasks 7/8).
- [ ] **Step 4:** tests, `assembleDebug`, `lintDebug`.
- [ ] **Step 5: Commit** "Format amounts and ranges in the app language".

### Extraction tasks (4–9): common procedure

For every listed file: replace each user-visible literal (Text, titles, subtitles, labels, placeholders,
buttons, dialog texts, empty states, toasts, `contentDescription`, notification texts, channel names) with
`stringResource(R.string.x, args…)` / `pluralStringResource(R.plurals.x, count, count)` (Compose,
`androidx.compose.ui.res`), `context.getString(...)` outside Compose, or `ViewText` from view models.
Add each key to `values/strings.xml` (grouped under an XML comment per area) AND its Russian to
`values-ru/strings.xml` in the same order. Sentences built by `+`/templates become one format string.
Steps per task:
- [ ] Step 1: list the literals (`grep -nE '"[^"]*[A-Za-z]{2,}' <files>`), decide each (user-visible / log / data).
- [ ] Step 2: extract + translate.
- [ ] Step 3: grep again; only logs/data/keys/previews remain.
- [ ] Step 4: `testDebugUnitTest`, `assembleDebug`, `lintDebug` pass.
- [ ] Step 5: commit "Localize <area>".

### Task 4: uikit, home, accounts, currency, colors
Files: `uikit/*.kt` (default `contentDescription`s etc.), `home/view/*` (bottom bar labels, `HomeTabHeader`, `HomeProfileButton`), `privacy/view/PrivacyModeButton.kt`, `accounts/view/*` (AccountsScreen, AccountList, AccountActionSheet(+ViewModel), EditAccountScreen(+ViewModel), AccountTypeSelectionSheet, ArchivedAccounts*), `currency/view/*` (CurrencySelectionScreen, AmountKeyboard content descriptions), `colors/view/*` (ItemLogoScreen, pickers "Icon"/"Color"). Account type names if shown.

### Task 5: categories, overview
Files: `categories/view/*` (CategoriesScreen, CategoryGrid, CategoryRingGrid, CategoryActionSheet(+VM), EditCategoryScreen(+VM), EditSubcategoryScreen(+VM), SelectableSubcategoryRow), `overview/view/*` (OverviewScreen, CategoryStatsSheet, ViewCategoryStats). Counts use `<plurals name="transaction_count">` (en one/other "%d transaction(s)"; ru one "%d операция", few "%d операции", many "%d операций", other "%d операции"). "No subcategory", "All categories", "Show less", "More…", averages (day/week/month).

### Task 6: transfers and history
Files: `transfers/view/*` (TransferSheet, TransferSheetLabels, TransferCounterpartySelector(+SelectionSheet VM), TransferShortcutActivity, TransferList empty state), `transfers/history/view/*` (ActivityScreen, PeriodBar, filter chip, revert dialog, In/Out tiles). Sheet titles "Expense"/"Income"/"Transfer", "Note", date key labels.

### Task 7: inbox list and cards
Files: `inbox/view/InboxScreen.kt`, `InboxScreenViewModel.kt`, `InboxScreenNavigation.kt` (toasts), `InboxCardsScreen.kt`, `InboxCardsViewModel.kt` (reason texts → `ViewText` with format args; `Event.ShowError`/undo texts → `ViewText`, resolved in `InboxCardsScreenNavigation` via `resolve(context)`), `InboxCardsScreenNavigation.kt`, `ViewInboxCard.kt`, `ViewInboxItem.kt`, `InboxActivity.kt`. Payee names, bank raw texts stay untouched. Counts ("N to sort", progress "3 of 10") → plurals/format args.

### Task 8: payee rules and notifications
Files: `inbox/view/RulesScreen.kt`, `RulesScreenViewModel.kt`, `RulesScreenNavigation.kt`, `ViewPayeeRuleGroup.kt`, `inbox/ask/PaymentQuestionNotifier.kt` (channel name/description, "Bank payment", text, open-app action), `PaymentQuestionReceiver.kt` (any user-visible text), `PaymentQuestion.notificationTitle` → takes the template or stays pure with the separator; `inbox/listener/BankNotificationListenerService.kt` (user-visible only). Notification strings are read with `context.getString` at post time. Do not touch `SebLatvia*`.

### Task 9: settings, auth, lock, sync errors, app; sweep; docs
Files: `preferences/view/*` (rest of Settings: sync error text, Appearance subtitles, Bank notifications, Inbox row with plural "N to categorize", Currency, Security, Account, sign-out dialog), `auth/view/*` (TempAuthScreen, PhraseAuthScreen toasts), `lock/view/*` (UnlockActivity, SetUpPasscodeScreen/Activity, PasscodeInput), `syncerrors/*`, `MoneyApp.kt`/workers (only if user-visible).
Then a whole-app sweep: `grep -rnE '(text|title|subtitle|label|placeholder|contentDescription|message)\s*=\s*"' app/src/main/java` and `Text\("` must find nothing user-visible; fix leftovers in place.
Docs: add section "F3. Localization (en + ru) and app language" to the spec (what/where/rulings) and a row to `docs/redesign/PROGRESS.md` with an F3 device checklist.
