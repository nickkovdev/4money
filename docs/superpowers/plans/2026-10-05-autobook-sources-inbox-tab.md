# Inbox tab and user-configured notification sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Inbox a main tab and let the user teach the app any bank app's notifications (SEB stays a built-in preset), with a 6-step wizard, a Sources screen and synced templates.

**Architecture:** A synced `notification_templates` table holds user templates built by a pure tokenizer/builder; a session-scoped `NotificationSourceRegistry` combines the SEB preset and enabled templates and replaces the hard-coded parser list in `ProcessBankNotificationUseCase` and the package check in the listener. The listener also feeds a device-only recent-notification buffer used by the wizard. UI: a new Inbox tab in `HomeActivity`, a new `AutoBookActivity` hosting Sources, wizard, card accounts, rules and the test-text screen.

**Tech Stack:** Kotlin, Jetpack Compose (foundation + Compose Unstyled + the repo `uikit/` components and `MoneyTheme` tokens), Koin, PowerSync, kotlinx.serialization, JUnit4. Supabase Postgres schema `money`.

**Spec:** `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md`, sections C, D, C/D decisions, F2 (privacy), F3 (localization), **F4** (this feature). Mockups (Russian copy, layout): `C:\Users\PC\AppData\Local\Temp\claude\E--projects-4money\a31bb744-57a1-4c06-b137-f69a7c31767b\scratchpad\artifact-files\gen\gen.py`, artboards `Inbox-Tab`, `Setup-1-Intro` … `Setup-6-Account`, `Sources` (ignore `Inbox-Banner`).

## Global Constraints

- Repo is PUBLIC: no real card digits, no real merchant names from the owner, no owner account/category names in code, tests, docs or commit messages. Test fixtures use invented names (COFFEE POINT, EXAMPLE SIA, Fuelstop, card `0000`/`1234`).
- Every new `.kt` file starts with the GPL header copied from an existing file.
- Colors only from `MoneyTheme.colors` tokens; components from `app/src/main/java/ua/com/radiokot/money/uikit/` (MoneyButton, MoneyIconButton, MoneyChip, SegmentedControl, ListGroup/ListRow/SectionHeader/IconTile, MoneySwitch, EmptyState, SheetScaffold…). No hardcoded `Color(0x…)`.
- Every user-visible string in `res/values/strings.xml` AND `res/values-ru/strings.xml` (same key, `<area>_<meaning>`, plurals as `<plurals>` with ru one/few/many/other). `lintDebug` must pass (`MissingTranslation` is an error).
- Privacy mode (`privacy/view/LocalPrivacyMode.kt`, `privacy/logic/PrivacyAmounts.kt`): every amount shown on the new screens is masked `•••` while private.
- Battery: the listener does no I/O and no DB work for a non-source package except one cheap regex and, on a hit, one small file append on the IO dispatcher. No network anywhere in this feature.
- Notification texts, payees, amounts and card digits are never logged.
- The SEB parser `inbox/logic/SebLatviaNotificationParser.kt` keeps its behaviour; `SebLatviaNotificationParserTest` must pass unchanged.
- Build/test (Git Bash, from the worktree root):
  `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'`
  then `./gradlew.bat testDebugUnitTest` (only `SternBrocotTreeSearchTest > extensiveTest` may fail), `./gradlew.bat assembleDebug`, `./gradlew.bat lintDebug`.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PnZC8W3VU3YDKW6kKJsF7t
  ```
- Never apply migrations to any server; never touch `E:\projects\4money` (main checkout); never use adb.

## Rulings (owner unavailable; decided by the controller)

1. **InboxActivity removed.** Its only other user was the "Payments to sort" notification; that now opens `HomeActivity` with `EXTRA_OPEN_INBOX` and the tab is selected (also on `onNewIntent`).
2. **"Recorded today", not "Recorded automatically".** Inbox items do not record whether they were auto-recorded or sorted by hand, and adding a column is not worth a migration; the section lists today's done items (local date of `receivedAt` = today), each with Undo. Subtitle "M recorded today" counts the same list.
3. **Profile dot** keeps only sync errors; pending inbox items are shown by the tab badge.
4. **Template input = `title + "\n" + text`** (title empty when null). Some apps put the payee in the title. The SEB preset keeps parsing `text` only.
5. **Required marks:** amount, currency, payee. Card is optional. A fifth mark **"Varies"** (any text) exists for variable words such as a payment purpose — without it an account-payment kind could never match a second notification. Payee must be one contiguous run of tokens.
6. **Payee and "Varies" are lazy `(.+?)` / `.+?` captures**, so other payees of any length match; adjacent tokens of the same mark merge.
7. **Card digits** capture `(\d{2,6})`; the last 4 digits are used.
8. **hasTimestamp** of a template = the sample contains a time token (`HH:MM`); then dedup ignores the post time like SEB card payments.
9. **Template name** = the sample title when present, else the first 3 literal words; not editable in this PR. SEB preset kinds have localized names.
10. **Template order:** preset first, then enabled templates by `created_at`, then by id.
11. **Source switch:** for a template source toggles `is_enabled` of all its templates; a preset is toggled in device-local prefs (`autobook` prefs, key `preset_disabled_<package>`), default enabled. A source with only disabled templates stays listed (switch off) and is not listened to.
12. **Active package cache:** the registry persists the active package set to the `autobook` prefs whenever it changes, and the listener reads that synchronous cache, so a notification right after a process start is not missed while templates load.
13. **Buffer storage:** JSON file `noBackupFilesDir/recent_money_notifications.json`, device only, max 50 entries, 7 days, newest first. Own app's notifications are never buffered. Cleared on wizard Done and when the app finds notification access not granted (Sources screen / wizard resume, and `onListenerDisconnected` when access is no longer granted).
14. **Active notifications** are taken from the connected listener instance (a `@Volatile` static reference set in `onListenerConnected`, cleared in `onListenerDisconnected`/`onDestroy`), filtered by the same money heuristic.
15. **Behaviour toggles are global** (device-local, `autobook` prefs, default true): record known payees (off → `PendingReason.AutoRecordDisabled`, no question notification), ask in the notification (off → `PaymentQuestionNotifier.ask` does nothing), learn from history (off → `InboxCardSuggester` ignores history; the question notification's category buttons are unchanged). Wizard step 6 and the Cards and accounts screen edit the same values.
16. **No-card account:** stored per source package in the existing card preferences (`bank_cards` prefs, key `source_account_<package>`). Resolution: card mapping → (no card) source account → rule account → most used.
17. **Sources live in a new `AutoBookActivity`** (Sources, wizard, Cards and accounts, Payee rules, Test text). The Rules screen is also reachable from the Inbox tab inside `HomeActivity`.
18. **App icons:** real launcher icons from `PackageManager`; fallback a letter tile in `MoneyTheme` accent tint (the mockup's brand-coloured letters are not reproduced: no hardcoded colors). `<queries>` for `MAIN/LAUNCHER` added to the manifest for package visibility.
19. **Settings:** the "Bank notifications" section becomes "Auto-booking" with one row (subtitle: access state and the number of active sources) opening `AutoBookActivity`; notification access moved to the Sources screen.
20. **Wizard entry:** "Add app" starts at step 1; "Set up"/"Teach another kind" of an existing source starts at step 3 for that package; step 1 shows "Next" instead of "Grant" when access is granted.

## Review Focus

1. A source notification posted right after the process starts (templates not loaded yet) must still be processed — Task 3 test with the cached package set.
2. Amount formats: `1 500,00`, `1.500,00`, `1,500.00`, `1 500,00` with NBSP/U+202F, `3.4`, `-3,40`, `€3.40`, `3,40€`, `1.234` (thousands) must parse to the right value — Task 2 tests.
3. A second notification with a longer/shorter payee or different numbers/dates than the sample must match; a notification of another kind must not — Task 2 tests.
4. The owner's SEB flow must not change: SEB texts processed via the registry give the same Outcome as before — Task 3 test with the three SEB samples.
5. Undo/accept from the Inbox tab and the notification tap reaching the tab — Task 5 (VM test for accept/undo and manual check list).

## File Structure

- `supabase/migrations/20261006000000_money_notification_templates.sql` — table, RLS, grants, publication, atomic_crud.
- `deploy/powersync/sync-config.yaml` — select.
- `app/.../powersync/DbSchema.kt` — table.
- `app/.../inbox/templates/data/NotificationTemplate.kt`, `TemplateFields.kt`, `NotificationTemplateRepository.kt`, `PowerSyncNotificationTemplateRepository.kt`.
- `app/.../inbox/templates/logic/SampleTokenizer.kt`, `TemplateAmounts.kt`, `TemplateBuilder.kt`, `TemplateMatcher.kt`, `MoneyTextHeuristic.kt`.
- `app/.../inbox/sources/logic/NotificationSourcePreset.kt` (+ `SebLatviaPreset`), `NotificationSourceRegistry.kt`, `BankNotificationParsing.kt`.
- `app/.../inbox/sources/data/AutoBookPreferences.kt` (+ `OnPrefs`), `RecentNotificationBuffer.kt`, `RecentNotification.kt`, `ActiveNotificationsSource.kt`, `AppInfoSource.kt`.
- `app/.../inbox/view/InboxTabScreen.kt`, `InboxTabScreenNavigation.kt` (reworked from `InboxScreen*`), view model extended.
- `app/.../inbox/sources/view/AutoBookActivity.kt`, `SourcesScreen*.kt`, `TestTextScreen*.kt`, `CardAccountsScreen*.kt`, `setup/SourceSetup*.kt`.

---

### Task 1: Server migration, sync config, client table and repository

**Model:** sonnet

**Files:**
- Create: `supabase/migrations/20261006000000_money_notification_templates.sql`
- Modify: `deploy/powersync/sync-config.yaml`
- Modify: `app/src/main/java/ua/com/radiokot/money/powersync/DbSchema.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/data/NotificationTemplate.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/data/TemplateFields.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/data/NotificationTemplateRepository.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/data/PowerSyncNotificationTemplateRepository.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/inbox/InboxModule.kt` (bind the repository in `sessionScope`)
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/templates/data/TemplateFieldsTest.kt`

**Interfaces — Produces:**
```kotlin
package ua.com.radiokot.money.inbox.templates.data

data class NotificationTemplate(
    val id: String,
    val sourcePackage: String,
    val name: String,
    val direction: Direction,
    val pattern: String,
    val fields: TemplateFields,
    val sampleText: String,
    val isEnabled: Boolean,
    val createdAt: kotlinx.datetime.LocalDateTime,
) {
    enum class Direction(val value: String) {
        Outgoing("outgoing"), Incoming("incoming");
        companion object { fun fromValue(value: String): Direction = entries.first { it.value == value } }
    }
}

@kotlinx.serialization.Serializable
data class TemplateFields(
    val amount: Int,          // capture group numbers in `pattern`, 1-based
    val currency: Int,
    val payee: Int,
    val card: Int? = null,
    val hasTimestamp: Boolean = false,
) {
    fun toJson(): String
    companion object { fun fromJson(json: String): TemplateFields } // Json { ignoreUnknownKeys = true }
}

interface NotificationTemplateRepository {
    fun getTemplatesFlow(): kotlinx.coroutines.flow.Flow<List<NotificationTemplate>> // ordered created_at, id
    suspend fun getTemplates(): List<NotificationTemplate>
    suspend fun addTemplates(templates: List<NotificationTemplate>)          // one write transaction
    suspend fun setEnabledForPackage(sourcePackage: String, isEnabled: Boolean)
    suspend fun deleteTemplate(id: String)
}
```

- [ ] **Step 1: Migration.** Model it on `20261002000000_money_rules_inbox.sql` (table + index + RLS policy `notification_templates_own` + grants to `authenticated`, `service_role`, `select` to `powersync_role` + `alter publication powersync add table money.notification_templates`). Columns:
  ```sql
  create table money.notification_templates
  (
      id             uuid primary key,
      user_id        uuid      not null default auth.uid() references auth.users (id) on delete cascade,
      source_package text      not null check (source_package <> ''),
      name           text      not null,
      direction      text      not null default 'outgoing' check (direction in ('outgoing', 'incoming')),
      -- Generated regex (Java/Kotlin syntax), matched against title + "\n" + text.
      pattern        text      not null check (pattern <> ''),
      -- Capture group numbers: {"amount":1,"currency":2,"payee":3,"card":4,"hasTimestamp":true}.
      fields         jsonb     not null,
      sample_text    text      not null,
      is_enabled     boolean   not null default true,
      -- Local wall-clock time, no time zone, like transfers.time.
      created_at     timestamp not null
  );
  create index notification_templates_user_idx on money.notification_templates (user_id);
  ```
  Then `create or replace function money.atomic_crud` with the body copied **verbatim** from `20261004000000_money_upstream_parity.sql` (lines 73–186: inner block, dead-letter logging into `money.sync_errors`, `raise warning`) and only the whitelist line changed to
  `if table_name not in ('accounts', 'categories', 'transfers', 'payee_rules', 'inbox_items', 'notification_templates') then`.
  Header comment: purpose, "apply before installing a build of this branch", and that `CREATE OR REPLACE` keeps grants.
- [ ] **Step 2: Optional local validation** (throwaway only, never the existing `satchel-wiki-postgres` container): `docker run -d --name money-mig-test -e POSTGRES_PASSWORD=x -p 127.0.0.1:55432:5432 postgres:16`, create stubs (`create schema auth; create table auth.users(id uuid primary key); create function auth.uid() returns uuid language sql as 'select null::uuid'; create role authenticated; create role anon; create role service_role; create role powersync_role;`), apply all migrations in order with `psql -v ON_ERROR_STOP=1 -1 -f`, then `docker rm -f money-mig-test`. If a pre-existing migration needs more stubs, add them; if it cannot be made to run in 15 minutes, skip and say so in the report.
- [ ] **Step 3: sync-config.** Append to `user_data.data`:
  ```yaml
      - >-
        SELECT id, source_package, name, direction, pattern, fields, sample_text, is_enabled, created_at
        FROM money.notification_templates AS notification_templates
        WHERE user_id = bucket.user_id
  ```
- [ ] **Step 4: DbSchema.** Constants `NOTIFICATION_TEMPLATES_TABLE = "notification_templates"`, `NOTIFICATION_TEMPLATE_SOURCE_PACKAGE`, `_NAME`, `_DIRECTION`, `_PATTERN`, `_FIELDS` (JSON text), `_SAMPLE_TEXT`, `_IS_ENABLED` (integer 0/1), `_CREATED_AT` (text `LocalDateTime.toString()`); a `Table(...)` like `getPowerSyncPayeeRulesTable()` and add it to the schema's table list. Check how booleans (`is_archived`) are stored/read elsewhere in DbSchema and repositories and do the same.
- [ ] **Step 5: TemplateFields test first** (`TemplateFieldsTest`): round-trip with and without `card`; `fromJson("{\"amount\":1,\"currency\":2,\"payee\":3,\"extra\":5}")` ignores unknown keys; server-style JSON with spaces `{"amount": 1, "currency": 2, "payee": 3, "card": 4, "hasTimestamp": true}` parses. Run `./gradlew.bat testDebugUnitTest --tests '*TemplateFieldsTest'` → FAIL, implement, → PASS.
- [ ] **Step 6: Repository** modelled on `inbox/data/PowerSyncPayeeRuleRepository.kt` (same `database` access, `watch` for flows, `writeTransaction` for writes, row mapper). A row whose `fields` fails to parse or whose `direction` is unknown is skipped (logged without content), never crashes.
- [ ] **Step 7:** `./gradlew.bat testDebugUnitTest assembleDebug`, commit `Add notification templates table, sync config and repository`.

---

### Task 2: Template tokenizer, builder and matcher (pure)

**Model:** opus

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/logic/SampleTokenizer.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/logic/TemplateAmounts.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/logic/TemplateBuilder.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/logic/TemplateMatcher.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/templates/logic/MoneyTextHeuristic.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/templates/logic/SampleTokenizerTest.kt`, `TemplateAmountsTest.kt`, `TemplateBuilderTest.kt`, `TemplateMatcherTest.kt`, `MoneyTextHeuristicTest.kt`

**Interfaces — Consumes:** `TemplateFields`, `NotificationTemplate` (Task 1), `ParsedBankNotification` (`inbox/data`).
**Produces:**
```kotlin
package ua.com.radiokot.money.inbox.templates.logic

data class SampleToken(
    val index: Int,
    val text: String,
    val start: Int,          // offsets into the normalized sample
    val end: Int,            // exclusive
    val kind: Kind,
    val spaceBefore: Boolean, // whitespace (incl. "\n") between the previous token and this one
) { enum class Kind { Word, Amount, Number, Date, Time, Currency, Punctuation } }

object SampleTokenizer {
    /** NFC, NBSP/U+202F → space, trimmed. Used for samples and for matching. */
    fun normalize(text: String): String
    /** [composeInput] = title (or "") + "\n" + text, normalized. */
    fun composeInput(title: String?, text: String): String
    fun tokenize(normalizedSample: String): List<SampleToken>
}

enum class TokenRole { Amount, Currency, Payee, Card, Varies }

object TemplateAmounts {
    /** Group-free amount regex fragment (no capture groups inside). */
    const val AMOUNT_PATTERN: String
    /** ISO 3-letter code or € $ £ (group-free). */
    const val CURRENCY_PATTERN: String
    /** "1 500,00" → 1500.00; "1.234" → 1234; "3,4" → 3.4; leading -/−/+ ignored; null when invalid. */
    fun parseAmount(text: String): java.math.BigDecimal?
    /** "€" → "EUR", "$" → "USD", "£" → "GBP", "eur" → "EUR"; null when not a currency. */
    fun parseCurrency(text: String): String?
}

object TemplateBuilder {
    /** Amount + adjacent currency token found on the sample, or empty. */
    fun detect(tokens: List<SampleToken>): Map<Int, TokenRole>

    sealed interface Result {
        data class Built(val pattern: String, val fields: TemplateFields) : Result
        data class Invalid(val problem: Problem) : Result
    }
    enum class Problem { MissingAmount, MissingCurrency, MissingPayee, PayeeNotContiguous, SeveralAmounts, SeveralCurrencies, SeveralCards }

    fun build(tokens: List<SampleToken>, marks: Map<Int, TokenRole>): Result

    /** Sample title or the first 3 literal words, max 32 chars. */
    fun defaultName(title: String?, tokens: List<SampleToken>, marks: Map<Int, TokenRole>): String
}

object TemplateMatcher {
    /** Null when the template does not match or the amount/currency are not valid; never throws. */
    fun match(
        pattern: String,
        fields: TemplateFields,
        isIncoming: Boolean,
        title: String?,
        text: String,
    ): ParsedBankNotification.Payment?

    fun match(template: NotificationTemplate, title: String?, text: String): ParsedBankNotification.Payment? =
        match(template.pattern, template.fields, template.direction == NotificationTemplate.Direction.Incoming, title, text)
}

object MoneyTextHeuristic {
    /** Cheap: an amount next to an ISO code (upper case, from a fixed set of common codes) or € $ £. */
    fun looksLikeMoney(text: String): Boolean
}
```

**Algorithm (must hold):**
- Tokenizer, after normalize: scan left to right; recognise in this priority: Date `\d{1,4}[./-]\d{1,2}[./-]\d{1,4}`, Time `\d{1,2}:\d{2}(?::\d{2})?`, Amount (`AMOUNT_PATTERN`, i.e. digits with thousands separators space/dot/comma and/or a decimal part — a run like `1 500,00` is ONE token; plain integer `2026` is Number), Number `\d+`, Currency symbol `€ $ £` (one char), Word (`[\p{L}\p{M}][\p{L}\p{M}\d'’-]*` — an ISO code like `EUR` is a Word whose `parseCurrency` is non-null; give it kind `Currency` when it is 3 upper-case letters in `java.util.Currency.getAvailableCurrencies()`), Punctuation (any other non-space char run, e.g. `...`, `…`, `.`, `:`). A space inside an amount token only joins groups of exactly 3 digits (`1 500,00` yes, `2 12` no).
- Builder regex: `^\s*` + per token + `\s*$`, flags applied at match time: IGNORE_CASE, DOT_MATCHES_ALL. Between tokens: `\s+` when `spaceBefore`, else `\s*`. Unmarked Word/Currency/Punctuation → `Regex.escape`-equivalent literal (use `Pattern.quote` free form: escape each regex metachar so the pattern stays readable). Unmarked Amount → `(?:AMOUNT_PATTERN)`, Number → `\d+`, Date → `\d{1,4}[./-]\d{1,2}[./-]\d{1,4}`, Time → `\d{1,2}:\d{2}(?::\d{2})?`. Marked Amount → `([-−+]?(?:AMOUNT_PATTERN))`, Currency → `(CURRENCY_PATTERN)`, Card → `(\d{2,6})`, Payee run → `(.+?)` (the whole contiguous run incl. its inner spaces), Varies run → `.+?`. Unmarked Number/Amount tokens adjacent to a payee are still wildcards. Only capture groups are the marked ones; group numbers in order of appearance go into `TemplateFields`. `hasTimestamp` = any Time token in the sample.
- Amount parsing: strip sign chars and all spaces; let `lastSep` = last `.` or `,`; if digits after it are 1–2 → decimal separator, remove every other `.`/`,`; else (exactly 3 digits) all separators are thousands; reject when a group after a thousands separator is not 3 digits. Must be > 0.
- Matcher: `SampleTokenizer.composeInput(title, text)`; `Regex(pattern, setOf(IGNORE_CASE, DOT_MATCHES_ALL)).matchEntire(input)`; a `PatternSyntaxException` → null. Payee trimmed of surrounding spaces and trailing `.`; empty payee → null. Card = last 4 digits of the group (or null when shorter than 4). Currency upper-cased via `parseCurrency`. Compile cache: a small `LinkedHashMap` LRU (32) keyed by pattern.

- [ ] **Step 1: Write the failing tests.** At least these cases (invent everything; never real merchants):
  ```kotlin
  // TemplateAmountsTest
  assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1 500,00"))
  assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1\u00A0500,00".let(SampleTokenizer::normalize)))
  assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1.500,00"))
  assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1,500.00"))
  assertEquals(BigDecimal("3.4"), TemplateAmounts.parseAmount("3,4"))
  assertEquals(BigDecimal("3.40"), TemplateAmounts.parseAmount("-3,40"))
  assertEquals(BigDecimal("1234"), TemplateAmounts.parseAmount("1.234"))
  assertEquals(BigDecimal("1234567.89"), TemplateAmounts.parseAmount("1 234 567.89"))
  assertNull(TemplateAmounts.parseAmount("0,00"))
  assertNull(TemplateAmounts.parseAmount("1.23.4"))
  assertEquals("EUR", TemplateAmounts.parseCurrency("€")); assertEquals("USD", TemplateAmounts.parseCurrency("$"))
  assertEquals("GBP", TemplateAmounts.parseCurrency("£")); assertEquals("EUR", TemplateAmounts.parseCurrency("eur"))
  assertNull(TemplateAmounts.parseCurrency("par"))
  ```
  ```kotlin
  // SampleTokenizerTest: SEB card sample
  val s = SampleTokenizer.composeInput("Jauna rezervācija", "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 COFFEE POINT .")
  val t = SampleTokenizer.tokenize(s)
  // texts: Jauna, rezervācija, Jūs, samaksājāt, 3,40, EUR, par, 04/10/2026, 09:12, karte, ..., 1234, COFFEE, POINT, .
  // kinds:  W W W W Amount Currency W Date Time W Punct Number W W Punct; "..." has spaceBefore=false, "1234" spaceBefore=false
  // "€3.40" → Currency "€", Amount "3.40" (spaceBefore=false); "3,40€" → Amount, Currency; "1 500,00 EUR" → one Amount token
  // "Paid 2 12 times" → Number "2", Number "12" (not an amount)
  ```
  ```kotlin
  // TemplateBuilderTest + TemplateMatcherTest
  // 1) detect() on the SEB sample marks 3,40 → Amount and EUR → Currency.
  // 2) Marks: amount, currency, card=1234, payee=COFFEE+POINT; Built; then match():
  //    same sample → Payment(3.40, "EUR", "1234", "COFFEE POINT", isIncoming=false, hasTimestamp=true)
  //    "Jūs samaksājāt 1 024,15 EUR par 05/10/2026 18:40 karte...1234 SOME LONGER SHOP NAME RIGA ." → 1024.15, payee "SOME LONGER SHOP NAME RIGA"
  //    "Jūs samaksājāt 7,80 USD par 2/10/2026 22:05 karte...0000 BOLT ." → 7.80 USD, card 0000, payee BOLT
  //    another kind "EXAMPLE SIA samaksāja 1000,00 EUR par Darba alga." → null
  // 3) Revolut-like, payee in title: title "Coffee Point", text "Paid €3.40 with card ·1234"
  //    marks payee=title words, currency=€, amount=3.40, card=1234 → matches title "Fuelstop Riga", text "Paid €18.40 with card ·1234".
  // 4) Varies: "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance: 120,00 EUR" with payee=EXAMPLE SIA,
  //    Varies=parking. → matches "... 12,50 EUR OTHER COMPANY SIA par rēķins 42 oktobris. Konta bilance: 1 020,00 EUR"
  //    (balance is an unmarked Amount → wildcard).
  // 5) Incoming direction → Payment.isIncoming = true.
  // 6) Invalid: no amount → MissingAmount; no currency → MissingCurrency; no payee → MissingPayee;
  //    payee on tokens 3 and 5 with 4 unmarked → PayeeNotContiguous; two amounts marked → SeveralAmounts.
  // 7) Special chars in literals (`(`, `+`, `*`, `?`, `[`, `|`, `$` as a word char) are escaped: a sample "Pirkums (POS) 3,40 EUR SHOP" builds and matches itself.
  // 8) Case: literal "Jūs" matches "JŪS"; NBSP between amount groups in the incoming text still matches.
  // 9) match() with a broken pattern "(" returns null, does not throw.
  // 10) defaultName: title "Jauna rezervācija" → "Jauna rezervācija"; null title → first 3 literal words.
  ```
  ```kotlin
  // MoneyTextHeuristicTest
  assertTrue(looksLikeMoney("Jūs samaksājāt 3,40 EUR par ...")); assertTrue(looksLikeMoney("Paid €3.40"))
  assertTrue(looksLikeMoney("−18,40 €")); assertFalse(looksLikeMoney("Your code is 123456"))
  assertFalse(looksLikeMoney("Meeting at 10:30")); assertFalse(looksLikeMoney("3 new messages from EUROPE"))
  ```
- [ ] **Step 2:** run `./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.inbox.templates.*'` → FAIL (unresolved).
- [ ] **Step 3:** implement the five files per the algorithm above; KDoc on the public functions.
- [ ] **Step 4:** run the same tests → PASS; then the full `testDebugUnitTest`.
- [ ] **Step 5:** commit `Add the notification template tokenizer, builder and matcher`.

---

### Task 3: Source registry, SEB preset, processing and listener with the recent buffer

**Model:** opus

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/sources/logic/NotificationSourcePreset.kt` (interface + `SebLatviaPreset`)
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/sources/logic/BankNotificationParsing.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/sources/logic/NotificationSourceRegistry.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/sources/data/AutoBookPreferences.kt`, `AutoBookPreferencesOnPrefs.kt` (preset switches + active package cache only in this task; Task 4 adds the behaviour toggles to the same class)
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/sources/data/RecentNotification.kt`, `RecentNotificationBuffer.kt`, `FileRecentNotificationBuffer.kt`, `ActiveNotificationsSource.kt`
- Modify: `inbox/logic/BankNotificationParser.kt` (remove `BankNotificationSources`), `inbox/logic/ProcessBankNotificationUseCase.kt`, `inbox/listener/BankNotificationListenerService.kt`, `inbox/InboxModule.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/sources/logic/NotificationSourceRegistryTest.kt`, `.../sources/data/FileRecentNotificationBufferTest.kt`, update `inbox/logic/ProcessBankNotificationUseCaseTest.kt`

**Interfaces — Consumes:** Task 1 repository/model, Task 2 `TemplateMatcher`, `MoneyTextHeuristic`.
**Produces:**
```kotlin
package ua.com.radiokot.money.inbox.sources.logic

interface NotificationSourcePreset {
    val packageName: String
    val kinds: List<Kind>                     // for chips in Sources
    fun parse(title: String?, text: String): ParsedBankNotification   // never throws
    enum class Kind { CardPayment, AccountPayment, IncomingPayment }
}
object SebLatviaPreset : NotificationSourcePreset   // delegates to SebLatviaNotificationParser()
object BuiltInPresets { val all: List<NotificationSourcePreset> = listOf(SebLatviaPreset) }

fun interface BankNotificationParsing {
    /** null = the package is not an active source (ignore); Unrecognized = source, no template matched. */
    suspend fun parse(packageName: String, title: String?, text: String): ParsedBankNotification?
}

class NotificationSourceRegistry(
    templateRepository: NotificationTemplateRepository,
    autoBookPreferences: AutoBookPreferences,
    scope: kotlinx.coroutines.CoroutineScope,   // session scope's coroutine scope; collects the template flow
) : BankNotificationParsing {
    data class Source(
        val packageName: String,
        val preset: NotificationSourcePreset?,
        val templates: List<NotificationTemplate>,   // all, enabled or not, ordered
        val isEnabled: Boolean,                      // preset switch, or any template enabled
    )
    val sourcesFlow: StateFlow<List<Source>>        // presets first, then packages by first template created_at
    suspend fun setSourceEnabled(packageName: String, isEnabled: Boolean)
    /** Waits for the first template load (max 2 s) before deciding, so early notifications are not lost. */
    override suspend fun parse(packageName: String, title: String?, text: String): ParsedBankNotification?
    /** Pure helper used by parse, the wizard test step and Test text: preset first, then enabled templates in order. */
    companion object {
        fun parseWith(preset: NotificationSourcePreset?, templates: List<NotificationTemplate>, title: String?, text: String): ParsedBankNotification
    }
}
```
```kotlin
package ua.com.radiokot.money.inbox.sources.data

interface AutoBookPreferences {
    fun isPresetEnabled(packageName: String): Boolean           // default true
    fun setPresetEnabled(packageName: String, isEnabled: Boolean)
    fun getPresetEnabledFlow(): Flow<Map<String, Boolean>>
    /** Synchronous cache of active source packages, read by the listener on the main thread. */
    fun getCachedActivePackages(): Set<String>                   // default: enabled presets
    fun setCachedActivePackages(packages: Set<String>)
}

data class RecentNotification(val packageName: String, val postTimeMillis: Long, val title: String?, val text: String)

interface RecentNotificationBuffer {
    fun add(notification: RecentNotification)   // dedup same package+title+text; keep newest 50 within 7 days
    fun getAll(): List<RecentNotification>      // newest first, pruned to 7 days
    fun clear()
}
class FileRecentNotificationBuffer(file: java.io.File, now: () -> Long = System::currentTimeMillis) : RecentNotificationBuffer
// kotlinx.serialization JSON, synchronized, a corrupt file reads as empty and is overwritten.

interface ActiveNotificationsSource {
    /** Money-like notifications currently shown, from the connected listener; empty when not connected. */
    fun getActive(): List<RecentNotification>
}
```
Listener changes (`BankNotificationListenerService`):
- `processNotification(sbn)`: skip own package; skip `IGNORED_FLAGS`; extract title/text (as now). If `packageName in autoBookPreferences.getCachedActivePackages()` → process as now but via the registry (session scope required, unchanged). Independently, if `MoneyTextHeuristic.looksLikeMoney(title + " " + text)` → `buffer.add(...)` on the IO scope. Text extraction happens once; nothing else for other packages.
- `onListenerConnected`: set `instance`; process the active notifications as now (only sources), do NOT buffer them (the wizard reads them live).
- `onListenerDisconnected` / `onDestroy`: clear `instance`; if `!NotificationAccess.isGranted(this)` → `buffer.clear()`.
- `ActiveNotificationsSource` implementation reads `instance?.activeNotifications` (catch `SecurityException`), maps like `processNotification` and keeps money-like ones (own package and ignored flags excluded).
- `ProcessBankNotificationUseCase`: constructor `parsers: List<BankNotificationParser>` → `parsing: BankNotificationParsing`; `parse(...) ?: return Outcome.Ignored`; the rest unchanged. Koin: `NotificationSourceRegistry` scoped in `sessionScope` (bound also as `BankNotificationParsing`); `AutoBookPreferencesOnPrefs` single on prefs `"autobook"`; `FileRecentNotificationBuffer(File(androidContext().noBackupFilesDir, "recent_money_notifications.json"))` single; `ActiveNotificationsSource` single. The listener gets device singletons with `get()` (KoinComponent) and the registry only from the session scope.
- Registry keeps `setCachedActivePackages` in sync on every sources change.

- [ ] **Step 1: Tests first.**
  - `NotificationSourceRegistryTest` (fake repository with a `MutableStateFlow`, fake prefs, `runTest` + `backgroundScope`): SEB preset is a source by default and parses the three SEB samples from `SebLatviaNotificationParserTest` identically; disabling the preset → `parse` returns null for SEB; a template package with one enabled template matching → Payment; with all templates disabled → null and listed with `isEnabled=false`; preset first then templates (a template for `se.seb.latvia` matching an unrecognised SEB text is used when the preset returns Unrecognized); two templates where both match → the older `created_at` wins; a source package text matching nothing → `Unrecognized`; `parse` called before the repository emitted waits for the first emission; cached active packages updated after a template is added/disabled.
  - `FileRecentNotificationBufferTest` (`TemporaryFolder`, fake clock): newest first; 51st add drops the oldest; entries older than 7 days dropped on `getAll`; duplicate text not added twice; `clear` empties and survives a new instance; a corrupt file reads empty.
  - `ProcessBankNotificationUseCaseTest`: construct with `BankNotificationParsing { pkg, title, text -> NotificationSourceRegistry.parseWith(SebLatviaPreset.takeIf { pkg == it.packageName }, emptyList(), title, text).takeIf { pkg == SebLatviaPreset.packageName } }` (or an equivalent helper in the test file) so every existing expectation stays the same; add a case where a template package is processed via a template into an AutoRecorded outcome.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** run the full `testDebugUnitTest` (all existing inbox tests pass) and `assembleDebug`.
- [ ] **Step 5:** commit `Route bank notifications through user templates and the SEB preset; buffer recent money notifications`.

---

### Task 4: Behaviour preferences and the no-card source account

**Model:** sonnet

**Files:**
- Modify: `inbox/sources/data/AutoBookPreferences.kt`, `AutoBookPreferencesOnPrefs.kt`
- Modify: `inbox/data/CardAccountPreferences.kt`, `CardAccountPreferencesOnPrefs.kt`, `inbox/logic/CardAccountResolver.kt`
- Modify: `inbox/logic/AutoExpenseResolver.kt`, `inbox/logic/ProcessBankNotificationUseCase.kt`, `inbox/ask/PaymentQuestionNotifier.kt`, `inbox/ask/PaymentQuestion.kt` (if `shouldAsk` needs the new reason), `inbox/logic/InboxCardSuggester.kt`, `inbox/view/InboxCardsViewModel.kt`, `inbox/InboxModule.kt`, any `when` over `PendingReason` (strings for the new reason in en + ru)
- Test: `AutoExpenseResolverTest`, `DefaultCardAccountResolverTest`, `InboxCardSuggesterTest`, `ProcessBankNotificationUseCaseTest`, `PaymentQuestionTest`

**Produces:**
```kotlin
// AutoBookPreferences additions (default true, device-local)
var isRecordKnownPayeesEnabled: Boolean   // or get/set pairs, matching the file's style
var isAskInNotificationEnabled: Boolean
var isLearnFromHistoryEnabled: Boolean
fun getBehaviourFlow(): Flow<AutoBookBehaviour>
data class AutoBookBehaviour(val recordKnownPayees: Boolean, val askInNotification: Boolean, val learnFromHistory: Boolean)

// CardAccountPreferences additions
fun getAccountIdForSource(sourcePackage: String): String?
fun setAccountIdForSource(sourcePackage: String, accountId: String)
fun getSourceAccountsFlow(): Flow<Map<String, String>>

// CardAccountResolver.resolve gets `sourcePackage: String?` (default null keeps old callers compiling)
// order: card mapping → (cardLast4 == null) source account → rule account → most used

// AutoExpenseResolver.PendingReason.AutoRecordDisabled; resolve(..., recordKnownPayees: Boolean = true)
// checked right after the Ask check: a Record rule with recordKnownPayees=false → Pending(AutoRecordDisabled)

// InboxCardSuggester: a `useHistory: Boolean = true` parameter; false → history entries are ignored
```
- [ ] **Step 1: Tests first:** resolver returns `AutoRecordDisabled` for a matched record rule when off, Ask still `AskRequested`; `DefaultCardAccountResolver`: no card + source account → source account even with a rule account; card present → card mapping wins and the source account is ignored; archived/unknown source account skipped; suggester ignores history when `useHistory=false` but keeps rule suggestions; `PaymentQuestion.shouldAsk(AutoRecordDisabled) == false`; `ProcessBankNotificationUseCase` with the toggle off produces `Pending(AutoRecordDisabled)` (pass behaviour via a constructor lambda `behaviour: () -> AutoBookBehaviour`).
- [ ] **Step 2:** FAIL. **Step 3:** implement; `PaymentQuestionNotifier.ask` returns early when ask is off; `InboxCardsViewModel` passes `useHistory` from the preferences. Add the pending reason string (en + ru) wherever reasons are rendered (grep `PendingReason.`).
- [ ] **Step 4:** full tests + assembleDebug. **Step 5:** commit `Add auto-booking behaviour switches and a per-source account for payments without a card`.

---

### Task 5: Inbox as the centre tab

**Model:** sonnet

**Files:**
- Modify: `home/view/HomeActivity.kt` (5 tabs, badge, inbox routes, counterparty selection routing, `EXTRA_OPEN_INBOX`), `home/view/HomeViewModel.kt` (profile dot = sync errors only; expose `pendingInboxCount: StateFlow<Long>`)
- Rework: `inbox/view/InboxScreen.kt` → `InboxTabScreen.kt`, `InboxScreenNavigation.kt` → tab route `InboxTabRoute` (keep file names if simpler, but the composable is the tab), `InboxScreenViewModel.kt` (suggestion per pending item, accept chip, today's done list, counts)
- Delete: `inbox/view/InboxActivity.kt` and its manifest entry
- Modify: `inbox/ask/PaymentQuestionNotifier.kt` (content intent → `HomeActivity` with `EXTRA_OPEN_INBOX`)
- Modify: `preferences/view/PreferencesScreen*.kt` — remove the Inbox row and `onProceedToInbox` (the Auto-booking row comes in Task 6; leave the existing notification access rows until then)
- Strings en + ru; new Tabler icons only via `tools/icons/import_tabler.py` if one is missing (check `res/drawable/ic_tabler_*` first: inbox, cards, arrow-back-up/undo, list-check/rules)
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/view/InboxTabItemsTest.kt` (pure helpers)

**Behaviour (mockup `Inbox-Tab`):**
- Header row: the shared profile + privacy buttons (`LocalHomeProfileButton`), spacer, rules `MoneyIconButton` → Rules screen. Title "Inbox"/"Входящие"; subtitle plural "N payments wait for a category" + " · " + plural "M recorded today" (ru from mockup: «3 платежа ждут категорию · 5 записано сегодня»).
- Primary button 64 dp, radius 20: cards icon + "Sort as cards" / «Разобрать карточками», trailing "N →"; hidden when N = 0 → `InboxCardsScreenRoute`.
- "Waiting" / «Ждут категорию» group: per pending item IconTile (category of the suggestion or inbox icon), payee display name (`PayeeNormalizer.displayName`), meta "time · account" (`ReceivedAtText`), amount in expense/income colour (privacy-masked); chips: suggested category (category colour tint) → accept; "Other…" / «Другая…» → existing prefilled transfer sheet (`InboxTransferPrefill`, as the current pending click). Unrecognized items (no amount): raw text two lines and only "Other…" (opens as today's click does) plus the existing dismiss action if the current screen has one.
- Accept = the same logic as accepting a card in `InboxCardsViewModel` (`InboxCardAcceptance` + `CompleteInboxItemUseCase`/transfer, rule remembering per the card's Remember default): extract the shared code into a reusable class (e.g. `inbox/logic/AcceptInboxSuggestionUseCase.kt`) used by both view models rather than copying. Suggestions come from `InboxCardSuggester` with `useHistory` from `AutoBookPreferences`.
- "Recorded today" / «Записано сегодня» group: today's done items, amount + Undo `MoneyIconButton` (existing `UndoInboxItemUseCase`, error toast as now).
- Empty state (`EmptyState`) when nothing is pending: "All sorted" style copy, with today's list still shown below if any.
- Bottom bar: Accounts, Categories, **Inbox** (centre, `ic_tabler_inbox`), History, Overview; numeric badge (accent) on the Inbox entry when pending > 0 ("99+" cap); Inbox route in `bottomNavigationRoutes`.
- In `HomeActivity` add `inboxCardsScreen`, `rulesScreen` destinations and route `transferCounterpartySelectionSheet` results the way `InboxActivity` did (transfer sheet / rules / cards / inbox tab), keeping the existing Home behaviour for other callers.
- `EXTRA_OPEN_INBOX`: in `onCreate` and `onNewIntent` (check the activity's launch mode in the manifest; use `singleTop` semantics if needed) navigate to the Inbox tab.
- Pure helpers with tests: `isToday(receivedAt, today)` filter and the subtitle counts; accept routing decision is already tested by `InboxCardAcceptanceTest`.

- [ ] Steps: write `InboxTabItemsTest` (done items of other days excluded; pending count) → FAIL → implement helpers → PASS; build the UI; `testDebugUnitTest assembleDebug lintDebug`; commit `Make the Inbox the centre tab`.

---

### Task 6: AutoBookActivity, Sources, Test text, Cards and accounts, Settings entry

**Model:** sonnet

**Files:**
- Create: `inbox/sources/view/AutoBookActivity.kt` (NavHost: `SourcesScreenRoute` start, `TestTextScreenRoute`, `CardAccountsScreenRoute`, `rulesScreen`, `transferCounterpartySelectionSheet` for rules and account picking; Task 7 adds the wizard route), manifest entry (`requiresUnlocking/requiresSession` like other activities)
- Create: `inbox/sources/view/SourcesScreen.kt`, `SourcesScreenNavigation.kt`, `SourcesScreenViewModel.kt`
- Create: `inbox/sources/view/TestTextScreen.kt`, `TestTextScreenNavigation.kt`, `TestTextScreenViewModel.kt`
- Create: `inbox/sources/view/CardAccountsScreen.kt` (+ navigation + view model): card → account rows (cards from `InboxRepository.getKnownCardLast4Flow()` + mapped ones), per-source "Without a card" → account rows, the three behaviour switches. The composable for the account rows + behaviour block is reusable by wizard step 6 (`AccountMappingSection`, `BehaviourSection`).
- Create: `inbox/sources/data/AppInfoSource.kt` (+ Android implementation): `fun getLabel(pkg): String?`, `fun getIcon(pkg): android.graphics.drawable.Drawable?`, `fun getLaunchableApps(): List<AppInfo>` (`data class AppInfo(val packageName: String, val label: String)`, sorted by label, own package excluded); manifest `<queries><intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent></queries>`. An `AppIcon` composable (drawable → bitmap via `toBitmap()`, remembered; fallback letter in an `IconTile`-like accent-tint circle).
- Modify: `inbox/data/InboxRepository.kt` + `PowerSyncInboxRepository.kt` (+ test fake): `fun getSourceStatsFlow(since: LocalDateTime): Flow<Map<String, SourceStats>>`, `data class SourceStats(val recognizedCount: Int, val lastReceivedAt: LocalDateTime?)` (recognized = amount not null, since = first day of the current month).
- Modify: `preferences/view/PreferencesScreen.kt`, `PreferencesScreenNavigation.kt`, `PreferencesScreenViewModel.kt`: section "Auto-booking" / «Автоучёт» with one row → `AutoBookActivity` (subtitle: access granted/not + plural active sources); remove the old notification access rows.
- Strings en + ru (copy from the `Sources` artboard).
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/sources/view/SourceCardTextsTest.kt` for the pure mapping of a `NotificationSourceRegistry.Source` + stats to the card model (kinds → chip labels: preset kinds localized, templates by name; meta "Last: … · N recognized this month"; "No kinds yet").

**Behaviour (mockup `Sources`):** top bar "Auto-booking" with back; access row (granted ✓ / not granted → tap opens `NotificationAccess.getSettingsIntent`, with the fallback intent as today; on resume re-check and, when not granted, `RecentNotificationBuffer.clear()`); "Sources" caption; a card per `registry.sourcesFlow` item: app icon, label (`AppInfoSource`, fallback package name), meta, `MoneySwitch` → `registry.setSourceEnabled`, kind chips, recognized count line, tonal "Teach another kind" (template sources and presets) → wizard step 3 for the package (wired in Task 7; until then a no-op callback); tonal "Add app" → wizard step 1; "More": "Cards and accounts", "Payee rules" (subtitle plural rule count), "Test text".
**Test text:** a multi-line `MoneyTextField` (title optional field + text field), results list: for each source and each kind/template, matched → payee, amount (privacy-masked), currency, card, direction; not matched rows collapsed under "No match: N". Uses `NotificationSourceRegistry.parseWith` per preset and per single template.

- [ ] Steps: `SourceCardTextsTest` → FAIL → implement → PASS; UI; settings; `testDebugUnitTest assembleDebug lintDebug`; commit `Add the auto-booking Sources screen, test text and card accounts`.

---

### Task 7: Wizard steps 1–3 (intro, app, sample)

**Model:** sonnet

**Files:**
- Create: `inbox/sources/view/setup/SourceSetupRoute.kt` (`@Serializable data class SourceSetupRoute(val packageName: String? = null)`; null → step 1, set → step 3), `SourceSetupViewModel.kt`, `SourceSetupScreen.kt` (one route, steps inside via `AnimatedContent`, top bar back = previous step or close, the 6-segment step indicator from the mockup), `SourceSetupIntroStep.kt`, `SourceSetupAppStep.kt`, `SourceSetupSampleStep.kt`
- Create: `inbox/sources/logic/DetectedApps.kt` (pure): `fun detectApps(notifications: List<RecentNotification>, now: Long, excludePackages: Set<String>): List<DetectedApp>`; `data class DetectedApp(val packageName: String, val count: Int, val todayCount: Int, val isDuplicateWallet: Boolean)` sorted by count desc; `GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"`.
- Modify: `AutoBookActivity.kt` (route), `SourcesScreen*` (wire "Add app" / "Teach another kind")
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/sources/logic/DetectedAppsTest.kt`, `app/src/test/java/ua/com/radiokot/money/inbox/sources/view/setup/SourceSetupStateTest.kt` (pure step transitions if the VM state machine is extracted as a pure reducer — recommended: `SourceSetupState` + `fun SourceSetupState.next()/back()`)

**View model state (shared with Task 8):**
```kotlin
data class SourceSetupState(
    val step: Step,                         // Intro, App, Sample, Teach, Test, Accounts
    val packageName: String?,
    val samples: List<RecentNotification>,  // buffer + active for packageName, newest first, distinct
    val selectedSample: RecentNotification?,
    val drafts: List<NotificationTemplate>, // built in step 4, kept across "Teach another kind"
    val startedAtSample: Boolean,
)
```
Notifications for steps 2/3/5 = `RecentNotificationBuffer.getAll()` + `ActiveNotificationsSource.getActive()`, de-duplicated by package+title+text, loaded when the wizard opens and on resume.

**Steps (copy from artboards Setup-1..3, en + ru):**
1. Intro: icon tile (bolt), «Расходы запишутся сами», explanation, the 3 example rows (notification → known payee recorded → new one to Inbox; use the mockup's invented examples), privacy note with shield icon, primary «Дать доступ к уведомлениям» (opens settings intent; on resume, if granted → step 2) or «Дальше» when granted; text button «Позже» closes. On API 33+ add the hint: "If Android says the setting is restricted: App info → ⋮ → Allow restricted settings" (ru equivalent).
2. App: explanation; "Looks like a bank" group of `detectApps` with app icon, label, "N notifications with an amount this week" (plural; "today" variant when all are today), radio mark, Wallet warning line in `colors.warn` (or the closest existing warning token); "Not in the list" → «Выбрать из всех приложений» opening a full-screen list of `AppInfoSource.getLaunchableApps()` with a search field; packages that are already sources are marked. "Next" enabled with a selection.
3. Sample: «Выберите уведомление о покупке…»; notification cards (app icon, app · time, title, text) for the package, selectable; empty state hint «Нет нужного? Оплатите что-нибудь картой — уведомление появится здесь.» (refresh on resume); «Использовать это».
- [ ] Steps: tests → FAIL → implement → PASS; `testDebugUnitTest assembleDebug lintDebug`; commit `Add the source setup wizard: intro, app and sample steps`.

---

### Task 8: Wizard steps 4–6 (teach, test, accounts) and saving

**Model:** sonnet

**Files:**
- Create: `inbox/sources/view/setup/SourceSetupTeachStep.kt`, `SourceSetupTestStep.kt`, `SourceSetupAccountsStep.kt`
- Create: `inbox/sources/logic/TemplateTestRun.kt` (pure): `fun runTest(preset: NotificationSourcePreset?, templates: List<NotificationTemplate>, notifications: List<RecentNotification>): TestRunResult`; `data class TestRunResult(val matched: List<Pair<RecentNotification, ParsedBankNotification.Payment>>, val unmatched: List<RecentNotification>)`
- Create: `inbox/sources/logic/SaveSourceSetupUseCase.kt`: saves drafts via `addTemplates` (enabled), stores card/source accounts and behaviour, clears the buffer.
- Modify: `SourceSetupViewModel.kt`, `SourceSetupScreen.kt`
- Test: `TemplateTestRunTest.kt`, `SaveSourceSetupUseCaseTest.kt`, extend `SourceSetupStateTest.kt`

**Steps (copy from artboards Setup-4..6):**
4. Teach: «Нажмите на слова, чтобы отметить их. Сумму и валюту мы нашли сами — проверьте.» The sample in a notification card, tokens as tappable chips in a `FlowRow` (marked tokens tinted by role: amount = expense colour, currency/payee/card/varies = distinct `MoneyTheme` category/accent tokens), tapping a token shows a role chooser row (Amount, Currency, Payee, Card, Varies, Clear); initial marks from `TemplateBuilder.detect`. Legend rows: Amount, Currency, Payee, Card («необяз.» when empty) with the extracted values (amount masked under privacy). Segmented Expense/Income. Live preview row: payee display name, "This is how it will look · card …1234", signed amount in the direction colour (masked under privacy) — computed by building and matching the template against the sample. Builder problems shown as a hint line (localized per `Problem`). «Проверить на других» enabled only for `Built`; adds/replaces the current draft (id = UUID, name = `TemplateBuilder.defaultName`, createdAt = now, sampleText = composed input).
5. Test: big "X of Y" (`runTest` over the package's notifications with preset + existing enabled templates + drafts), "recent notifications of <App> understood correctly"; rows: matched (check tile, payee, time · card, amount masked under privacy) and unmatched (x tile, title or first line, «Другой вид уведомления»); tonal «Научить ещё одному виду» → step 3 keeping drafts (the used samples stay selectable); hint about kinds; «Дальше».
6. Accounts: card rows for the distinct cards in matched results + mapped cards (account chip → account picker: the existing counterparty selection sheet with `showAccounts = true` inside `AutoBookActivity`, or an in-step account list sheet), "Without a card number" row → source account; Behaviour switches (reuse `BehaviourSection` from Task 6); note «Незнакомые получатели и платежи в другой валюте всегда попадают во Входящие.»; «Готово» → `SaveSourceSetupUseCase` → close to Sources.
- [ ] Steps: `TemplateTestRunTest` (SEB preset + a draft; a draft for an unknown package; empty list → 0 of 0 handled in UI), `SaveSourceSetupUseCaseTest` (templates saved enabled, accounts stored, buffer cleared) → FAIL → implement → PASS; UI; `testDebugUnitTest assembleDebug lintDebug`; commit `Add the source setup wizard: teach, test and accounts steps`.

---

### Task 9: Docs

**Model:** controller (no subagent)

- `docs/redesign/PROGRESS.md`: F4 row, install order (migration + sync-config before installing), F4 device checklist.
- `docs/HANDOFF.md`: migrations list, decision "only SEB" superseded, sources/wizard pointers.
- Commit `Document F4`.
