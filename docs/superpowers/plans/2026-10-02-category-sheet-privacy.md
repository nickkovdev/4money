# Category sheet from Overview + privacy mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a category on Overview opens a 1Money-style category sheet; an eye button in every tab header turns on a persisted privacy mode that masks headline amounts and shows the rest as percentages.

**Architecture:** Pure logic in `privacy/logic/PrivacyAmounts.kt` (percent text, mask decisions). One CompositionLocal `LocalPrivacyMode` provided by `MoneyAppTheme` (every activity) from `PrivacyPreferences`; the amount layer (`AnimatedAmountText`, `ViewAmountFormat.formatOrPrivate`) consumes it, screens only say *what total* an amount is a share of. The category sheet is a new bottom-sheet route in `overview/view` built on `GetCategoryAmountsBySubcategoryUseCase`, `GetOverviewStatsUseCase` and a new count query.

**Tech Stack:** Kotlin, Jetpack Compose (foundation + Compose Unstyled + repo `uikit/`), Koin, PowerSync SQLite, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md`, section "F2". Design system: `docs/redesign/BRIEF.md`.

## Global Constraints

- GPL header on new files (copy from an existing file; author line `Copyright 2025 Oleg Koretsky` as in the neighbours).
- Use `MoneyTheme.colors` / `MoneyTheme.typography` / `MoneyShapes` / `MoneySpacing` tokens and `uikit/` components (ListGroup, ListRow, ListDivider, SheetHandle, MoneyButton, MoneyIconButton, ActionTile, ItemLogo). No Material 3. No hardcoded colours.
- Icons only via `tools/icons/import_tabler.py` (Tabler 3.31.0, MIT). Never hand-write SVG paths.
- Mask string exactly `•••` (U+2022 ×3), no currency symbol. "No share" exactly `—` (U+2014).
- The transfer sheet keypad/amount being typed stays visible. No `FLAG_SECURE`.
- No server/schema/sync-config changes. Behaviour with privacy OFF must be identical to today.
- Battery: no infinite animations.
- Repo is public: no personal data in code, tests or docs.
- Build/test (Git Bash, from the worktree root):
  `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest` (only `SternBrocotTreeSearchTest > extensiveTest` may fail) and `./gradlew.bat assembleDebug`.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PnZC8W3VU3YDKW6kKJsF7t
  ```

## Rulings (made by the controller, the owner was not available mid-run)

- R1. Overview "All categories" (the "More…" row) no longer jumps to the Categories tab: it expands the list inline to all categories ("Show less" collapses). Every row, top or expanded, opens the category sheet. (Also fixes BRIEF problem 7.)
- R2. Sheet header total is in the category's own currency (as the Categories action sheet). Share-of-period % and the period total row come from Overview stats (primary currency), so they match the Overview top list.
- R3. Subcategories have no icons in the data model: their rows use a letter `ItemLogo` in the category colour; the "No subcategory" bucket uses the category's own icon and is titled "No subcategory". Rows sorted by amount desc, zero rows hidden.
- R4. "N transactions" = transfers of the category and its subcategories in the period (new `getCategoryTransferCountFlow`, same SQL as the subcategory amounts). Singular "1 transaction".
- R5. "Expense"/"Income" button = `TransfersNavigator.proceedToTransfer(category)` (the same path as tapping a category tile: last used account, otherwise the account picker), popping the sheet.
- R6. "Transactions" button = the Categories action sheet "Activity" mechanism: `HomeViewModel.filterActivityByCounterparty(TransferCounterparty.Category(category))` then navigate to `ActivityScreenRoute` popping `OverviewScreenRoute` inclusive. The filter chip with clear appears there.
- R7. Privacy state: SharedPreferences file `privacy`, boolean key `is_enabled`, default false, `PrivacyPreferencesOnPrefs` (same pattern as `ThemePreferencesOnPrefs`), global single in `themeModule`. `LocalPrivacyMode` is provided by `MoneyAppTheme`, so Home, Inbox and every other activity react.
- R8. Percent = half-up integer of |part| / |total|; total null or 0 → `—`; non-zero part rounding to 0 → `<1%`; part 0 → `0%`. No cap above 100.
- R9. Transactions rows: share of the whole period's total (not the filter's) of that direction, in the primary currency. A row whose currency is not the primary one shows `•••`; account→account transfers `•••`; the secondary (other currency) line is hidden while private.
- R10. Day headers have no sums in the redesign (PROGRESS "Open"), so nothing to mask there.
- R11. Categories: ring centre totals masked; grid tiles show share of the period total of the current mode; their separate currency-symbol line is hidden. Categories action sheet (Categories tab): header total masked, subcategory rows show share of the category total.
- R12. Notification while private: title is the payee only ("Fuelstop"), no amount; notifications already shown are not re-posted on toggle.
- R13. Account action sheet balance and In/Out tiles, Transactions In/Out tiles (account filter), accounts "≈ primary" line: masked.
- R14. Payee rules range thresholds (`< 10 €`) stay visible (rule settings, not money data).
- R15. Eye button: `ic_tabler_eye` when amounts are visible (tap hides), `ic_tabler_eye_off` when private; content descriptions "Hide amounts" / "Show amounts"; tint `ink2`, `accent` while private.
- R16. Overview bar chart: labels list replaced with nulls while private (bars stay); top-category trailing amounts hidden, the `%` subtitle stays.

## Review Focus

- Zero totals (empty month, all income zero): every share shows `—`, no division by zero, progress bars at 0 — tested in Task 1 (`zero total`), Task 6 sheet with an empty period.
- Toggling privacy while a screen is open must recompose immediately (Accounts, Overview, sheet open on top) — CompositionLocal read inside the composables, not cached in `remember` without the flag as a key — checked in Task 3/4 reviews.
- Privacy OFF must render byte-identical strings to today (`formatOrPrivate` falls through to `invoke`) — Task 3 test `formatOrPrivate off equals invoke` can't run without Compose, so `ViewAmountFormat.privateText` is unit-tested and reviewers check the OFF branch.
- Negative values (account balances, Overview balance < 0) with privacy on: still `•••`, colour rule unchanged — tested in Task 1 (`mask ignores sign`).
- Category with only uncategorized transfers / archived category opened from Overview: sheet shows the single "No subcategory" row and still works — Task 6 test for `CategoryStatsCalculator.subcategoryRows`.

---

### Task 1: Pure privacy logic (model: haiku)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/privacy/logic/PrivacyAmounts.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/privacy/logic/PrivacyAmountsTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface PrivateAmountDisplay { data object Mask; data class ShareOf(val total: BigInteger?) }`
  - `object PrivacyAmounts { const val MASK = "•••"; const val NO_SHARE = "—"; fun sharePercent(part: BigInteger, total: BigInteger?): Int?; fun shareText(part: BigInteger, total: BigInteger?): String; fun shareFraction(part: BigInteger, total: BigInteger?): Float; fun textOf(value: BigInteger, display: PrivateAmountDisplay): String; fun forTransfer(isExpense: Boolean, isIncome: Boolean, isInTotalsCurrency: Boolean, expenseTotal: BigInteger?, incomeTotal: BigInteger?): PrivateAmountDisplay }`

- [ ] **Step 1: Write the failing test** (`PrivacyAmountsTest.kt`, GPL header, package `ua.com.radiokot.money.privacy.logic`)

```kotlin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

class PrivacyAmountsTest {
    private fun b(v: Long) = BigInteger.valueOf(v)

    @Test
    fun `share rounds half up`() {
        assertEquals(33, PrivacyAmounts.sharePercent(b(1), b(3)))
        assertEquals(67, PrivacyAmounts.sharePercent(b(2), b(3)))
        assertEquals(50, PrivacyAmounts.sharePercent(b(1), b(2)))
        assertEquals(1, PrivacyAmounts.sharePercent(b(5), b(1000)))
        assertEquals("33%", PrivacyAmounts.shareText(b(1), b(3)))
    }

    @Test
    fun `zero total`() {
        assertNull(PrivacyAmounts.sharePercent(b(5), b(0)))
        assertNull(PrivacyAmounts.sharePercent(b(5), null))
        assertEquals("—", PrivacyAmounts.shareText(b(5), b(0)))
        assertEquals("—", PrivacyAmounts.shareText(b(0), null))
        assertEquals(0f, PrivacyAmounts.shareFraction(b(5), b(0)))
    }

    @Test
    fun `tiny and zero parts`() {
        assertEquals("<1%", PrivacyAmounts.shareText(b(1), b(1000)))
        assertEquals("0%", PrivacyAmounts.shareText(b(0), b(1000)))
    }

    @Test
    fun `uses absolute values and no cap`() {
        assertEquals("25%", PrivacyAmounts.shareText(b(-25), b(100)))
        assertEquals("150%", PrivacyAmounts.shareText(b(150), b(100)))
        assertEquals(0.25f, PrivacyAmounts.shareFraction(b(-25), b(100)))
        assertEquals(1f, PrivacyAmounts.shareFraction(b(150), b(100)))
    }

    @Test
    fun `mask ignores sign`() {
        assertEquals("•••", PrivacyAmounts.textOf(b(-1234), PrivateAmountDisplay.Mask))
        assertEquals("•••", PrivacyAmounts.textOf(b(0), PrivateAmountDisplay.Mask))
        assertEquals("10%", PrivacyAmounts.textOf(b(10), PrivateAmountDisplay.ShareOf(b(100))))
        assertEquals("—", PrivacyAmounts.textOf(b(10), PrivateAmountDisplay.ShareOf(null)))
    }

    @Test
    fun `transfer display`() {
        assertEquals(
            PrivateAmountDisplay.ShareOf(b(200)),
            PrivacyAmounts.forTransfer(isExpense = true, isIncome = false, isInTotalsCurrency = true, expenseTotal = b(200), incomeTotal = b(900)),
        )
        assertEquals(
            PrivateAmountDisplay.ShareOf(b(900)),
            PrivacyAmounts.forTransfer(isExpense = false, isIncome = true, isInTotalsCurrency = true, expenseTotal = b(200), incomeTotal = b(900)),
        )
        // Account to account.
        assertEquals(
            PrivateAmountDisplay.Mask,
            PrivacyAmounts.forTransfer(isExpense = false, isIncome = false, isInTotalsCurrency = true, expenseTotal = b(200), incomeTotal = b(900)),
        )
        // Foreign currency row.
        assertEquals(
            PrivateAmountDisplay.Mask,
            PrivacyAmounts.forTransfer(isExpense = true, isIncome = false, isInTotalsCurrency = false, expenseTotal = b(200), incomeTotal = b(900)),
        )
        // Totals not loaded yet → share of nothing → "—".
        assertEquals(
            PrivateAmountDisplay.ShareOf(null),
            PrivacyAmounts.forTransfer(isExpense = true, isIncome = false, isInTotalsCurrency = true, expenseTotal = null, incomeTotal = null),
        )
    }
}
```

- [ ] **Step 2: Run, expect compile FAIL** — `./gradlew.bat testDebugUnitTest --tests "*PrivacyAmountsTest*"`

- [ ] **Step 3: Implement** (`PrivacyAmounts.kt`, GPL header)

```kotlin
package ua.com.radiokot.money.privacy.logic

import java.math.BigInteger

/**
 * How an amount is shown while the privacy mode is on.
 */
sealed interface PrivateAmountDisplay {
    /**
     * A headline/absolute number: hidden completely.
     */
    data object Mask : PrivateAmountDisplay

    /**
     * Shown as a percentage of the [total], "—" when the total is unknown or zero.
     */
    data class ShareOf(val total: BigInteger?) : PrivateAmountDisplay
}

/**
 * Privacy mode texts and decisions. Pure.
 */
object PrivacyAmounts {
    const val MASK = "•••"
    const val NO_SHARE = "—"
    private val HUNDRED = BigInteger.valueOf(100)

    // BigInteger.TWO needs API 33.
    private val TWO = BigInteger.valueOf(2)

    /**
     * @return |[part]| of |[total]| in percent rounded half-up, null for a missing or zero total.
     */
    fun sharePercent(part: BigInteger, total: BigInteger?): Int? {
        val whole = total?.abs()
            ?.takeIf { it.signum() != 0 }
            ?: return null
        return ((part.abs() * HUNDRED + whole / TWO) / whole).toInt()
    }

    fun shareText(part: BigInteger, total: BigInteger?): String {
        val percent = sharePercent(part, total)
            ?: return NO_SHARE
        return if (percent == 0 && part.signum() != 0)
            "<1%"
        else
            "$percent%"
    }

    /**
     * @return share for a progress bar, within 0..1, 0 for a missing or zero total.
     */
    fun shareFraction(part: BigInteger, total: BigInteger?): Float {
        val whole = total?.abs()
            ?.takeIf { it.signum() != 0 }
            ?: return 0f
        return (part.abs().toDouble() / whole.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun textOf(value: BigInteger, display: PrivateAmountDisplay): String = when (display) {
        PrivateAmountDisplay.Mask -> MASK
        is PrivateAmountDisplay.ShareOf -> shareText(value, display.total)
    }

    /**
     * A transaction row: share of the period total of its direction,
     * masked for transfers between accounts and amounts not in the totals currency.
     */
    fun forTransfer(
        isExpense: Boolean,
        isIncome: Boolean,
        isInTotalsCurrency: Boolean,
        expenseTotal: BigInteger?,
        incomeTotal: BigInteger?,
    ): PrivateAmountDisplay = when {
        !isInTotalsCurrency -> PrivateAmountDisplay.Mask
        isExpense -> PrivateAmountDisplay.ShareOf(expenseTotal)
        isIncome -> PrivateAmountDisplay.ShareOf(incomeTotal)
        else -> PrivateAmountDisplay.Mask
    }
}
```

- [ ] **Step 4: Run test, expect PASS.**
- [ ] **Step 5: Commit** `git add` both files; message `Add pure privacy mode amount logic` + trailer.

---

### Task 2: Stats data — category transfer count and all ranked categories (model: haiku)

**Files:**
- Modify: `app/src/main/java/ua/com/radiokot/money/transfers/history/data/HistoryStatsRepository.kt` (add method)
- Modify: `app/src/main/java/ua/com/radiokot/money/transfers/history/data/PowerSyncHistoryStatsRepository.kt` (implement)
- Modify: `app/src/main/java/ua/com/radiokot/money/overview/logic/OverviewStats.kt`, `OverviewStatsCalculator.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/overview/logic/OverviewStatsCalculatorTest.kt` (add a test)

**Interfaces:**
- Produces:
  - `HistoryStatsRepository.getCategoryTransferCountFlow(categoryId: String, isIncome: Boolean, period: HistoryPeriod): Flow<Int>`
  - `OverviewStats.categories: List<OverviewCategoryShare>` — every category with a positive total, ranked (amount desc, then id); `topCategories` stays = `categories.take(topCategoryCount)`.

- [ ] **Step 1: Failing test** — add to `OverviewStatsCalculatorTest` (reuse its existing helpers/fixtures style; read the file first):

```kotlin
@Test
fun `categories lists every positive category ranked, top is its head`() {
    val d = LocalDate(2026, 10, 1)
    val stats = OverviewStatsCalculator.calculate(
        amountsByCategoryId = mapOf(
            "a" to mapOf(d to BigInteger.valueOf(10)),
            "b" to mapOf(d to BigInteger.valueOf(40)),
            "c" to mapOf(d to BigInteger.valueOf(30)),
            "d" to mapOf(d to BigInteger.valueOf(20)),
            "z" to mapOf(d to BigInteger.ZERO),
        ),
        firstDay = d,
        lastDay = d,
        today = d,
    )
    assertEquals(listOf("b", "c", "d", "a"), stats.categories.map { it.categoryId })
    assertEquals(listOf(40, 30, 20, 10), stats.categories.map { it.percent })
    assertEquals(stats.categories.take(3), stats.topCategories)
}
```

- [ ] **Step 2: Run, expect FAIL** (`categories` unresolved).
- [ ] **Step 3: Implement.** In `OverviewStats` add after `topCategories`:
  ```kotlin
  /**
   * Every category with a positive total, ranked like [topCategories] (which is its head).
   */
  val categories: List<OverviewCategoryShare>,
  ```
  In `OverviewStatsCalculator.calculate` build `val shares = rankedIds.map { categoryId -> OverviewCategoryShare(categoryId, totalsByCategoryId.getValue(categoryId), percentOf(...)) }` and pass `topCategories = shares.take(topCategoryCount), categories = shares`.
  In `HistoryStatsRepository` add with KDoc "@return number of transfers of the category and its subcategories within the [period]". In `PowerSyncHistoryStatsRepository`:
  ```kotlin
  override fun getCategoryTransferCountFlow(
      categoryId: String,
      isIncome: Boolean,
      period: HistoryPeriod,
  ): Flow<Int> =
      database
          .watch(
              sql =
                  if (isIncome)
                      SELECT_FOR_INCOME_CATEGORY
                  else
                      SELECT_FOR_EXPENSE_CATEGORY,
              parameters = listOf(
                  categoryId,
                  categoryId,
                  period.startInclusive.toDbDayString(),
                  period.endExclusive.toDbDayString(),
              ),
              mapper = { _ -> Unit },
          )
          .map { rows -> rows.size }
          .flowOn(Dispatchers.Default)
  ```
  (If the `mapper` lambda type needs the cursor param, use `mapper = { sqlCursor -> sqlCursor.getString(TRANSFER_SELECTED_AMOUNT) }`.) Fix any other `HistoryStatsRepository` implementors (grep; there are none in tests today).
- [ ] **Step 4: Run all unit tests, expect PASS; `assembleDebug` OK.**
- [ ] **Step 5: Commit** `Count category transfers and rank all Overview categories`.

---

### Task 3: Privacy plumbing — preferences, CompositionLocal, amount layer, eye button (model: sonnet)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/privacy/data/PrivacyPreferences.kt`, `PrivacyPreferencesOnPrefs.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/privacy/view/LocalPrivacyMode.kt`, `PrivacyModeButton.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/theme/ThemeModule.kt` (register single), `theme/view/MoneyAppTheme.kt` (provide local)
- Modify: `app/src/main/java/ua/com/radiokot/money/currency/view/ViewAmountFormat.kt` (`privateText`), `AnimatedAmountText.kt` (`privateAs` param), create `currency/view/FormatOrPrivate.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/home/view/HomeActivity.kt` (eye button next to the profile button via `LocalHomeProfileButton`)
- Modify: `tools/icons/import_tabler.py` (add `"eye", "eye-off"` to `UI_ICONS`), generated `app/src/main/res/drawable/ic_tabler_eye.xml`, `ic_tabler_eye_off.xml`, `tools/icons/LICENSE-tabler-icons.txt` if touched
- Test: `app/src/test/java/ua/com/radiokot/money/currency/view/ViewAmountFormatTest.kt` (add test)

**Interfaces:**
- Consumes: Task 1 `PrivateAmountDisplay`, `PrivacyAmounts`.
- Produces:
  - `interface PrivacyPreferences { val isPrivacyModeEnabled: MutableStateFlow<Boolean> }` (package `ua.com.radiokot.money.privacy.data`)
  - `val LocalPrivacyMode: ProvidableCompositionLocal<Boolean>` = `compositionLocalOf { false }` (package `ua.com.radiokot.money.privacy.view`)
  - `ViewAmountFormat.privateText(text: String, value: BigInteger, customColor: Color? = null): AnnotatedString` — same colour rule as `invoke` (customColor ?: sign colour), no currency symbol.
  - `@Composable fun ViewAmountFormat.formatOrPrivate(amount: ViewAmount, customColor: Color? = null, privateAs: PrivateAmountDisplay = PrivateAmountDisplay.Mask): AnnotatedString` (package `ua.com.radiokot.money.currency.view`) — returns `invoke(amount, customColor)` when `LocalPrivacyMode.current` is false.
  - `AnimatedAmountText(amount, modifier, style, customColor, maxLines, privateAs: PrivateAmountDisplay = PrivateAmountDisplay.Mask)` — when private, renders `privateText(PrivacyAmounts.textOf(amount.value, privateAs), amount.value, customColor)` with no counting animation.
  - `@Composable fun PrivacyModeButton()` — `MoneyIconButton` toggling `PrivacyPreferences.isPrivacyModeEnabled` (R15).

- [ ] **Step 1: Failing test** in `ViewAmountFormatTest` (read it first for its locale setup):
  ```kotlin
  @Test
  fun privateTextHasNoCurrencyAndKeepsColorRule() {
      val format = ViewAmountFormat(Locale.US, positiveColor = Color.Green, negativeColor = Color.Red, zeroColor = Color.Gray)
      val text = format.privateText("•••", BigInteger.valueOf(-5))
      assertEquals("•••", text.text)
      assertEquals(Color.Red, text.spanStyles.single().item.color)
      val custom = format.privateText("12%", BigInteger.ONE, customColor = Color.Blue)
      assertEquals("12%", custom.text)
      assertEquals(Color.Blue, custom.spanStyles.single().item.color)
  }
  ```
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
  - `privateText`: `buildAnnotatedString { withStyle(SpanStyle(color = customColor ?: when (value.signum()) { 1 -> positiveColor; -1 -> negativeColor; else -> zeroColor })) { append(text) } }`.
  - `PrivacyPreferencesOnPrefs(sharedPreferences)` mirrors `ThemePreferencesOnPrefs` exactly (lazy `MutableStateFlow(sharedPreferences.getBoolean("is_enabled", false))`, `drop(1).collect { putBoolean }` on an IO scope).
  - `themeModule`: `single { PrivacyPreferencesOnPrefs(androidApplication().getSharedPreferences("privacy", Context.MODE_PRIVATE)) } bind PrivacyPreferences::class`.
  - `MoneyAppTheme`: `val privacyPreferences = koinInject<PrivacyPreferences>()`, `val isPrivate by privacyPreferences.isPrivacyModeEnabled.collectAsState()`, wrap `MoneyTheme(...)` in `CompositionLocalProvider(LocalPrivacyMode provides isPrivate)`. Previews use `MoneyTheme` directly and keep the default `false`.
  - `AnimatedAmountText`: read `LocalPrivacyMode.current`; if true, plain `Text(amountFormat.privateText(...))` (skip `animateAmountValueAsState`); else unchanged code.
  - Icons: add `"eye", "eye-off"` to `UI_ICONS`, run `python tools/icons/import_tabler.py` (network to registry.npmjs.org works). Check `git status`: only the two new drawables (plus possibly license/names unchanged) may appear; if the script rewrites unrelated files with different content, revert those and keep only the two new ones, note it in the report.
  - `PrivacyModeButton`: `koinInject<PrivacyPreferences>()`, collect state, `MoneyIconButton(icon = if (isPrivate) R.drawable.ic_tabler_eye_off else R.drawable.ic_tabler_eye, contentDescription = if (isPrivate) "Show amounts" else "Hide amounts", onClick = { flow.value = !flow.value }, tint = if (isPrivate) colors.accent else colors.ink2)`. Check `MoneyIconButton`'s signature in `uikit/MoneyButton.kt`.
  - `HomeActivity`: `LocalHomeProfileButton provides { Row(verticalAlignment = CenterVertically) { ProfileButton(...); PrivacyModeButton() } }` so all four tab headers (Accounts uses `HomeProfileButton()` directly, the others via `HomeTabHeader`) get it.
- [ ] **Step 4: Run all unit tests + `assembleDebug`, expect PASS.**
- [ ] **Step 5: Commit** `Add the privacy mode toggle and the private amount layer`.

---

### Task 4: Mask headline amounts (model: sonnet)

**Files (modify):**
- `accounts/view/AccountsScreen.kt` (total header :229, "≈ primary" line :304-309), `accounts/view/AccountList.kt` (:368-377, :439-444 balances), `accounts/view/AccountActionSheet.kt` (:185-187 current balance, In/Out tiles if any)
- `overview/view/OverviewScreen.kt` (balance, Expenses/Income cards, Day/Week/Total stats; chart labels → nulls while private (R16); top-category trailing amount hidden while private)
- `categories/view/CategoriesScreen.kt` (:183, :194 ring centre totals)
- `transfers/history/view/ActivityScreen.kt` (:246 In/Out tiles)
- `inbox/view/InboxScreen.kt` (:320), `inbox/view/InboxCardsScreen.kt` (:670)
- `inbox/ask/PaymentQuestion.kt` (+ pure `notificationTitle`), `inbox/ask/PaymentQuestionNotifier.kt`, `inbox/InboxModule.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/ask/PaymentQuestionTest.kt`

**Interfaces:**
- Consumes: Task 3 `formatOrPrivate`, `AnimatedAmountText(privateAs = …)` (default Mask), `LocalPrivacyMode`, `PrivacyPreferences`; Task 1 `PrivacyAmounts.MASK`.
- Produces: `PaymentQuestion.notificationTitle(payee: String, signedAmount: String, isPrivate: Boolean): String` → `"$payee · $signedAmount"` or `payee`.

- [ ] **Step 1: Failing test** in `PaymentQuestionTest`:
  ```kotlin
  @Test
  fun `title hides the amount in privacy mode`() {
      assertEquals("Fuelstop · −18.40 €", PaymentQuestion.notificationTitle("Fuelstop", "−18.40 €", isPrivate = false))
      assertEquals("Fuelstop", PaymentQuestion.notificationTitle("Fuelstop", "−18.40 €", isPrivate = true))
  }
  ```
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
  - `PaymentQuestionNotifier` gets `privacyPreferences: PrivacyPreferences` (constructor + `InboxModule` `get()`), title = `PaymentQuestion.notificationTitle(payee, "$sign${formatAmount(amount)} ${account.currency.symbol}", privacyPreferences.isPrivacyModeEnabled.value)`. Check no other text/action label in the notification carries the amount (setContentText strings don't).
  - Every listed `amountFormat(...)` headline call → `amountFormat.formatOrPrivate(amount = …, customColor = …)` (Mask). For `animateAmountValueAsState` sites (AccountList) keep the animation for the OFF branch and render `amountFormat.privateText(PrivacyAmounts.MASK, value, color)` when `LocalPrivacyMode.current`. For string concatenations like `"Current balance " + amountFormat(balance).text` use the private text when on.
  - Overview: Balance/cards/stats use `AnimatedAmountText` → masked by default, nothing to pass. Labels: `val isPrivate = LocalPrivacyMode.current; val labels = remember(overview, isPrivate) { if (isPrivate) overview.bars.map { null } else … }`. Top categories: `trailing` only when not private.
  - Any `remember(...)` that caches formatted text must include the privacy flag as a key.
- [ ] **Step 4: Unit tests + `assembleDebug` PASS.** Grep `amountFormat(` / `AnimatedAmountText(` across the app and confirm every remaining unmasked site is either the transfer sheet input (must stay), a Task 5 share site, or listed in the report.
- [ ] **Step 5: Commit** `Mask headline amounts and the notification amount in privacy mode`.

---

### Task 5: Shares instead of amounts (model: sonnet)

**Files (modify):**
- `categories/view/ViewCategoryListItem.kt` (+ `amountInPrimaryCurrency: BigInteger?` from `CategoryWithAmount`, include in equals/hashCode), `categories/view/CategoriesScreenViewModel.kt` (expose `val currentModeTotal: StateFlow<BigInteger?>` = `totalInPrimaryCurrency?.value` of the current mode), `categories/view/CategoriesScreen.kt`, `categories/view/CategoryGrid.kt`, `categories/view/CategoryRingGrid.kt` (pass the total down)
- `categories/view/CategoryActionSheet.kt` (header total masked; subcategory rows `ShareOf(statsAmount.value.value)`)
- `transfers/history/view/ActivityViewModel.kt` (+ `GetCategoriesWithAmountsAndTotalUseCase` dependency, `transfers/history/TransfersHistoryModule.kt` wiring), `transfers/history/view/ActivityScreen.kt`, `transfers/view/TransferList.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/transfers/view/ViewPrivacyTotals.kt`

**Interfaces:**
- Consumes: Task 1 `PrivacyAmounts.forTransfer`, `shareText`, `PrivateAmountDisplay.ShareOf`; Task 3 `formatOrPrivate`, `privateText`, `LocalPrivacyMode`.
- Produces:
  - `@Immutable class ViewPrivacyTotals(val currency: ViewCurrency, val expense: BigInteger, val income: BigInteger)`
  - `ActivityViewModel.privacyTotals: StateFlow<ViewPrivacyTotals?>` — from `historyStatsPeriod.flatMapLatest { combine(useCase(isIncome=false, period), useCase(isIncome=true, period)) }`, null without a primary currency. Unfiltered (R9).
  - `TransferList(..., privacyTotals: ViewPrivacyTotals? = null)`; `TransferItem` while private: `amountFormat.formatOrPrivate(amount, amountColor, PrivacyAmounts.forTransfer(isExpense = type == Expense, isIncome = type == Income, isInTotalsCurrency = privacyTotals != null && primary.currency == privacyTotals.currency, expenseTotal = privacyTotals?.expense, incomeTotal = privacyTotals?.income))`; the secondary currency line hidden while private.
- Category grid tile while private: text `PrivacyAmounts.shareText(item.amountInPrimaryCurrency ?: 0, currentModeTotal)` coloured like the zero/non-zero rule there, currency-symbol line not shown.

- [ ] **Step 1:** No new pure logic (covered by Task 1); write no UI tests. Read each file before editing.
- [ ] **Step 2: Implement** as above. Privacy OFF output unchanged.
- [ ] **Step 3: Unit tests + `assembleDebug` PASS.**
- [ ] **Step 4: Commit** `Show shares instead of amounts in privacy mode`.

---

### Task 6: Category sheet from Overview (model: sonnet)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/overview/logic/CategoryStatsCalculator.kt` (pure)
- Create: `app/src/main/java/ua/com/radiokot/money/overview/view/CategoryStatsSheetViewModel.kt`, `CategoryStatsSheet.kt`, `CategoryStatsSheetNavigation.kt`, `ViewCategoryStats.kt`
- Modify: `overview/OverviewModule.kt` (VM), `overview/view/OverviewScreenViewModel.kt` (expand state, row click event, remove `ProceedToCategories`), `overview/view/ViewOverview.kt` (all categories list), `overview/view/OverviewScreen.kt` (clickable rows, expand/collapse R1), `overview/view/OverviewScreenNavigation.kt`, `home/view/HomeActivity.kt` (sheet destination + callbacks R5/R6)
- Test: `app/src/test/java/ua/com/radiokot/money/overview/logic/CategoryStatsCalculatorTest.kt`

**Interfaces:**
- Consumes: Task 2 `getCategoryTransferCountFlow`, `OverviewStats.categories`; Task 1/3 privacy API; `GetCategoryAmountsBySubcategoryUseCase(categoryId, period): Flow<CategoryWithAmountsBySubcategory>`; `GetOverviewStatsUseCase(period): Flow<OverviewData?>`; `TransfersNavigator.proceedToTransfer(category: Category, navOptions)`; `HomeViewModel.filterActivityByCounterparty`.
- Produces:
  - `object CategoryStatsCalculator { data class SubcategoryRow(val subcategory: Subcategory?, val amount: BigInteger, val fraction: Float); fun subcategoryRows(amountBySubcategory: Map<Subcategory?, BigInteger>): List<SubcategoryRow> }` — drops zero amounts, sorted by amount desc then title (null bucket last on ties), `fraction = PrivacyAmounts.shareFraction(amount, total)`.
  - `@Serializable data class CategoryStatsSheetRoute(val categoryId: String, val isIncome: Boolean, private val statsPeriodJson: String)` with a `statsPeriod` getter and a `(categoryId, isIncome, statsPeriod)` constructor (copy `CategoryActionSheetRoute`'s pattern).
  - `fun NavGraphBuilder.categoryStatsSheet(onProceedToTransfer: (Category) -> Unit, onProceedToFilteredActivity: (TransferCounterparty.Category) -> Unit)` using `bottomSheet<CategoryStatsSheetRoute>`.
  - `OverviewScreenViewModel.Event.ProceedToCategoryStats(categoryId: String, isIncome: Boolean, statsPeriod: HistoryPeriod)`; `overviewScreen(homeViewModel, onProceedToCategoryStats: (CategoryStatsSheetRoute) -> Unit)`.

- [ ] **Step 1: Failing test** `CategoryStatsCalculatorTest`:
  ```kotlin
  @Test
  fun `rows sorted desc, zeros dropped, fractions of the total`() {
      val food = Subcategory(title = "Food", position = 1.0, categoryId = "c", id = "s1")
      val fuel = Subcategory(title = "Fuel", position = 2.0, categoryId = "c", id = "s2")
      val idle = Subcategory(title = "Idle", position = 3.0, categoryId = "c", id = "s3")
      val rows = CategoryStatsCalculator.subcategoryRows(
          mapOf(food to BigInteger.valueOf(25), fuel to BigInteger.valueOf(50), idle to BigInteger.ZERO, null to BigInteger.valueOf(25))
      )
      assertEquals(listOf(fuel, food, null), rows.map { it.subcategory })
      assertEquals(listOf(0.5f, 0.25f, 0.25f), rows.map { it.fraction })
  }

  @Test
  fun `only uncategorized`() {
      val rows = CategoryStatsCalculator.subcategoryRows(mapOf(null to BigInteger.TEN))
      assertEquals(1, rows.size)
      assertEquals(1f, rows.single().fraction)
  }

  @Test
  fun `empty period`() {
      assertEquals(emptyList<CategoryStatsCalculator.SubcategoryRow>(), CategoryStatsCalculator.subcategoryRows(emptyMap()))
  }
  ```
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement the calculator, then the sheet.**
  - `ViewCategoryStats` (immutable): `category: Category`, `title`, `colorScheme`, `icon`, `isIncome`, `transferCount: Int`, `total: ViewAmount` (category currency, sum of subcategory amounts), `periodShare: BigInteger` + `periodTotal: ViewAmount` (primary currency, from `OverviewData.expense/income.categories` entry for the id — 0 when absent — and `.total`), `periodShareFraction: Float`, `periodText: String` (`ViewHistoryPeriod.fromHistoryPeriod(period).getText()`), `subcategories: List<ViewCategoryStatsSubcategory(key, title, isUncategorized, amount: ViewAmount, fraction: Float)>`.
  - VM combines `getCategoryAmountsBySubcategoryUseCase(categoryId, period)`, `historyStatsRepository.getCategoryTransferCountFlow(categoryId, isIncome, period)`, `getOverviewStatsUseCase(period)` → `StateFlow<ViewCategoryStats?>` (null while loading; sheet shows only the handle). Events `ProceedToTransfer(category)`, `ProceedToFilteredActivity(TransferCounterparty.Category(category))`.
  - UI (`CategoryStatsSheet.kt`) follows the existing `CategoryActionSheet` layout/background/insets: `SheetHandle()`; header block in the category tint (`itemAccentColor(colorScheme)` / the tinted surface style of `TransferSheet.CounterpartyHalf`, `MoneyShapes.large`): `ItemLogo` 52.dp, title (`typography.title`), "N transactions" (`caption`, `ink3`), total via `AnimatedAmountText(total)` (Mask when private), progress bar (4.dp pill, `surface2` track, accent fill = `periodShareFraction`) with `PrivacyAmounts.shareText(periodShare, periodTotal.value)` text — the % is always visible (it is already a share); then a row `periodText` + `AnimatedAmountText(periodTotal)`. Then `ListGroup` of subcategory rows: leading `ItemLogo(title = row title, colorScheme = category colorScheme, icon = if uncategorized category.icon else null)` 36.dp, title ("No subcategory" for the null bucket, `ink2`), trailing `amountFormat.formatOrPrivate(amount, ink, ShareOf(total.value))`, bar under it (copy the CategoryActionSheet bar). Bottom row of two `MoneyButton`s (weight 1f each): Filled "Expense"/"Income" (icon `ic_tabler_plus`), Tonal "Transactions" (icon `ic_tabler_list_details`) — check `MoneyButton`'s parameters.
  - Overview: `ViewOverview` gets `allCategories: List<ViewOverviewTopCategory>` built from `stats.categories` (same mapping), keep `hasMoreCategories`. VM holds `isExpanded: MutableStateFlow<Boolean>` reset to false when the mode changes; `onMoreCategoriesClicked()` toggles it; `onCategoryClicked(key: String)` emits `ProceedToCategoryStats(key, isIncome.value, historyStatsPeriod.value)`. Screen shows `if (expanded) allCategories else topCategories`, the last row "All categories" / "Show less" (chevron down/up), every category `ListRow` gets `onClick`.
  - `HomeActivity`: `overviewScreen(homeViewModel = viewModel, onProceedToCategoryStats = { navController.navigate(it) })`; add `categoryStatsSheet(onProceedToTransfer = { category -> transfersNavigator.proceedToTransfer(category = category, navOptions = navOptions { popUpTo<CategoryStatsSheetRoute> { inclusive = true } }) }, onProceedToFilteredActivity = { counterparty -> viewModel.filterActivityByCounterparty(counterparty); navController.navigate(route = ActivityScreenRoute, navOptions = navOptions { popUpTo(OverviewScreenRoute) { inclusive = true } }) })`. Remove the now unused `onProceedToCategories`.
  - Koin: `viewModel { parameters -> CategoryStatsSheetViewModel(parameters = parameters.get(), getCategoryAmountsBySubcategoryUseCase = get(), historyStatsRepository = get(), getOverviewStatsUseCase = get()) }` inside `sessionScope` in `overviewModule`.
- [ ] **Step 4: Unit tests + `assembleDebug` PASS.**
- [ ] **Step 5: Commit** `Open a category sheet from Overview`.

---

### Task 7: Docs (controller)

- [ ] Update `docs/redesign/PROGRESS.md`: add the F2 commits, move "Privacy mode" from Backlog to done, add the device checklist items. Commit `Note the category sheet and privacy mode in the redesign progress`.

## Self-review

- Spec coverage: sheet header/count/share/period row (T6), subcategory list (T6), actions (T6, R5/R6), count query (T2), toggle in all four tab headers + persistence + central layer (T3), masks (T4), shares (T5), chart labels/top amounts (T4), notification (T4), keypad visible (no change to transfer sheet), no FLAG_SECURE, pure tested logic (T1, T2, T6, T4 title).
- Types: `PrivateAmountDisplay`, `PrivacyAmounts.*`, `formatOrPrivate`, `privateText`, `LocalPrivacyMode`, `PrivacyPreferences.isPrivacyModeEnabled`, `ViewPrivacyTotals`, `getCategoryTransferCountFlow`, `OverviewStats.categories`, `CategoryStatsSheetRoute` used consistently.
