# Home screen widget (F5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A 3×1 home screen widget (income ↑, app logo with the pending Inbox badge, expense ↓) and a translucent quick-entry activity (category grid → transfer sheet) launched from it.

**Architecture:** A Jetpack Glance widget in a new feature package `ua.com.radiokot.money.widget` reads the pending inbox count (one COUNT query in the session scope, none without a session) and the theme mode when it renders. It never polls (`updatePeriodMillis = 0`): a debounced `HomeWidgetUpdater` calls `updateAll` when the session-scoped pending count flow changes, when the notification listener stores a pending item, when the theme changes and when the session scope closes. `QuickTransferActivity` (a `MoneyAppActivity`, so auth redirect and app lock work like every other entry point) hosts the existing `TransferCounterpartySelectionSheet` (categories only) and `TransferSheet` via navigation-compose, exactly like the existing `TransferShortcutActivity`, with a "most used account" fallback when the category has no last used account.

**Tech Stack:** Kotlin, Jetpack Glance `androidx.glance:glance-appwidget:1.2.0`, Compose (Compose Unstyled + `uikit`), navigation-compose, Koin 4 (session scope), PowerSync.

**Spec:** `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` (section "F5. Home screen widget", added in Task 5) and the owner-approved design in the controller brief, summarised in that section; backlog entry in `docs/redesign/PROGRESS.md`.

## Global Constraints

- PUBLIC repo: no personal data (no real merchant names, card digits, the owner's category/account names) in code, tests, docs or commits. Never commit `local.properties` / `app/local.properties`.
- Every new Kotlin file starts with the GPL header copied from an existing file (e.g. `MoneyApp.kt`), keep the copyright line as in neighbouring files (`Copyright 2025 Oleg Koretsky`).
- Every new string exists in `app/src/main/res/values/strings.xml` AND `app/src/main/res/values-ru/strings.xml` (lint `MissingTranslation` is an error).
- Icons: Tabler only, added through `tools/icons/import_tabler.py` (`UI_ICONS` list). No 1Money assets.
- Colours come from `MoneyColors` tokens via `moneyColorsOf(themeMode, isSystemDark)` (`theme/view/MoneyAppTheme.kt`).
- Battery: no periodic work, no services, no polling; `updatePeriodMillis="0"`.
- No server changes (no migrations, no sync-config changes).
- Build/test from Git Bash in the worktree root:
  `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'`
  then `./gradlew.bat testDebugUnitTest` (only `SternBrocotTreeSearchTest > extensiveTest` may fail), `./gradlew.bat assembleDebug`, `./gradlew.bat lintDebug`.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PnZC8W3VU3YDKW6kKJsF7t
  ```

## Rulings (controller, owner not available mid-run)

1. **Reuse the shortcut pattern.** `QuickTransferActivity` copies the structure of `transfers/view/TransferShortcutActivity.kt` (NavHost with `transferCounterpartySelectionSheet` + `transferSheet`, `MoneyAppModalBottomSheetHost`, finish when the back stack empties). The category grid is the existing `TransferCounterpartySelectionSheet` with `showAccounts = false`, `showCategories = true`. The existing launcher shortcuts stay unchanged.
2. **Lock and session via `MoneyAppActivity`.** `requiresSession = true`, `requiresUnlocking = true` (unlike the shortcut activity, which is incognito and unlocked). No session → `goToAuth()` is overridden to start `AuthActivity` with `FLAG_ACTIVITY_NEW_TASK` (lands in the app's own task, not the quick-entry task) and `finish()`.
3. **Privacy:** `isIncognito = false`; the sheets follow privacy mode through `MoneyAppTheme`'s `LocalPrivacyMode`, same as the normal transfer sheet.
4. **Default account:** last used for the category (`GetLastUsedAccountsByCategoryUseCase`, already in `TransfersNavigator`); else `MostUsedAccountSource.getMostUsedAccountId()` (it already returns "any non-archived account" when there are no transfers); else (no accounts) the existing account selection sheet. Implemented as an optional `fallbackAccount` constructor parameter of `TransfersNavigator` (default `null` = old behaviour), so the main app is unchanged.
5. **Task:** `android:taskAffinity="${applicationId}.quickentry"`, `excludeFromRecents="true"`, `exported="false"`, theme `@style/TransparentActivity` (the sheet host draws the dim scrim), launched with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` so a second tap restarts the flow with the new direction and finishing returns to the launcher.
6. **Widget look:** no container background; three 48 dp circles spaced evenly across the width: income = solid `income` circle with `ic_tabler_arrow_up` tinted `onAccent`; centre = white circle with the launcher foreground (`@drawable/pear_by_francesco_cesqo_stefanini_from_noun_project_cc_by_3_0`, as the adaptive icon does) and a badge pill (`accent` background, `onAccent` text, top-end); expense = solid `expense` circle with `ic_tabler_arrow_down` tinted `onAccent`. Translucent `*Tint` tokens are not used (unreadable on a wallpaper).
7. **Theme:** the widget uses the exact palette of the app theme: day colours = `moneyColorsOf(mode, isSystemDark = false)`, night colours = `moneyColorsOf(mode, isSystemDark = true)` through Glance day/night `ColorProvider`s, so "System" follows the launcher's night mode and explicit modes are fixed. A theme change triggers `updateAll`.
8. **Count read:** `InboxRepository.getPendingCountFlow().first()` (a single `COUNT(*)`), no interface change. No session scope → no badge. Errors → no badge (logged, never crash the widget).
9. **Update triggers:** a root-scope `HomeWidgetUpdater` with a 500 ms debounce, triggered by (a) the session-scoped pending count flow (`distinctUntilChanged`), started by a `UserSessionScopeListener` like `NotificationSourceRegistryStarter`, collected in a session-scoped `CoroutineScope` cancelled on scope close; (b) the scope close itself (badge disappears after sign-out); (c) `BankNotificationListenerService` right after `Outcome.Pending`; (d) theme mode changes in `MoneyApp.initTheme`. Glance's own `updateAll` is a no-op without placed widgets.
10. **Preview:** `android:previewLayout` (API 31+) = a static RemoteViews layout `layout/widget_quick_entry_preview.xml`; `android:previewImage` (< 31) = a layer-list drawable `drawable/widget_quick_entry_preview_image.xml` of the three circles. No bitmaps.
11. **Badge text:** `null` for 0 or less (hidden), the number for 1..99, `"99+"` above.

## Review Focus

1. Sign-out while a widget is placed: the badge must disappear (scope close triggers an update; the render finds no session). Test: `HomeWidgetBadgeTest` covers count ≤ 0 → hidden; Task 4 wires `registerCallback` on the scope close (review it).
2. Many inbox changes in a burst (sync downloads 50 items): exactly one widget update after the burst. Test: `DebouncedTriggerTest.burstCollapsesToOneRun` (Task 4).
3. A category with no last used account and no accounts at all: falls back to the account selection sheet, never crashes. Covered by `TransfersNavigator` code path review (Task 2), and `QuickTransferDirectionTest` for intent parsing.
4. Unknown / missing intent action for `QuickTransferActivity` (process death restore, malicious intent impossible because not exported): defaults to expense, never crashes. Test: `QuickTransferDirectionTest.unknownActionIsExpense` (Task 2).
5. Count above 99, exactly 99, 100: "99", "99+". Test: `HomeWidgetBadgeTest` (Task 1).

---

## File Structure

New package `app/src/main/java/ua/com/radiokot/money/widget/`:
- `logic/HomeWidgetBadge.kt` — pure badge text.
- `logic/HomeWidgetPalette.kt` — pure ThemeMode → day/night widget colours.
- `logic/DebouncedTrigger.kt` — pure debounce helper.
- `logic/HomeWidgetUpdater.kt` — interface + Glance implementation (`updateAll`).
- `logic/HomeWidgetBadgeStarter.kt` — `UserSessionScopeListener`, observes the count flow.
- `view/QuickEntryWidget.kt` — `GlanceAppWidget` + content composable.
- `view/QuickEntryWidgetReceiver.kt` — `GlanceAppWidgetReceiver`.
- `HomeWidgetModule.kt` — Koin module.
- `app/src/main/java/ua/com/radiokot/money/transfers/view/QuickTransferActivity.kt` (+ `QuickTransferDirection`).
- Resources: `res/xml/quick_entry_widget_info.xml`, `res/layout/widget_quick_entry_preview.xml`, `res/drawable/widget_circle.xml`, `res/drawable/widget_quick_entry_preview_image.xml`, icons `ic_tabler_arrow_up.xml`, `ic_tabler_arrow_down.xml` (generated).
- Tests in `app/src/test/java/ua/com/radiokot/money/widget/logic/` and `.../transfers/view/`.

---

### Task 1: Widget pure logic (badge text, palette)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/widget/logic/HomeWidgetBadge.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/widget/logic/HomeWidgetPalette.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/widget/logic/HomeWidgetBadgeTest.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/widget/logic/HomeWidgetPaletteTest.kt`

**Interfaces:**
- Produces: `object HomeWidgetBadge { fun text(pendingCount: Long): String? }`; `data class HomeWidgetColors(val income: Color, val expense: Color, val onButton: Color, val badge: Color, val onBadge: Color)`; `data class HomeWidgetPalette(val day: HomeWidgetColors, val night: HomeWidgetColors) { companion object { fun of(themeMode: ThemeMode): HomeWidgetPalette } }` (`Color` = `androidx.compose.ui.graphics.Color`).

- [ ] **Step 1: Write the failing tests**

```kotlin
class HomeWidgetBadgeTest {
    @Test fun zeroIsHidden() = assertNull(HomeWidgetBadge.text(0))
    @Test fun negativeIsHidden() = assertNull(HomeWidgetBadge.text(-1))
    @Test fun oneToNinetyNineAsIs() {
        assertEquals("1", HomeWidgetBadge.text(1))
        assertEquals("99", HomeWidgetBadge.text(99))
    }
    @Test fun aboveNinetyNineIsCapped() {
        assertEquals("99+", HomeWidgetBadge.text(100))
        assertEquals("99+", HomeWidgetBadge.text(12345))
    }
}

class HomeWidgetPaletteTest {
    @Test fun systemFollowsNightMode() {
        val palette = HomeWidgetPalette.of(ThemeMode.System)
        assertEquals(PaperMoneyColors.income, palette.day.income)
        assertEquals(MidnightMoneyColors.income, palette.night.income)
    }
    @Test fun explicitModeIsFixed() {
        val palette = HomeWidgetPalette.of(ThemeMode.Ember)
        assertEquals(palette.day, palette.night)
        assertEquals(EmberMoneyColors.expense, palette.day.expense)
        assertEquals(EmberMoneyColors.onAccent, palette.day.onButton)
        assertEquals(EmberMoneyColors.accent, palette.day.badge)
    }
    @Test fun lightIsPaperBoth() {
        val palette = HomeWidgetPalette.of(ThemeMode.Light)
        assertEquals(PaperMoneyColors.income, palette.night.income)
    }
}
```

- [ ] **Step 2: Run** `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.widget.*"` — expect compilation FAIL.

- [ ] **Step 3: Implement**

```kotlin
object HomeWidgetBadge {
    private const val MAX_SHOWN = 99

    /** @return the badge text, or null to hide the badge. */
    fun text(pendingCount: Long): String? = when {
        pendingCount <= 0 -> null
        pendingCount > MAX_SHOWN -> "$MAX_SHOWN+"
        else -> pendingCount.toString()
    }
}
```

```kotlin
data class HomeWidgetColors(
    val income: Color, val expense: Color, val onButton: Color, val badge: Color, val onBadge: Color,
)

/** Widget colours of the app theme; "System" follows the launcher night mode. */
data class HomeWidgetPalette(val day: HomeWidgetColors, val night: HomeWidgetColors) {
    companion object {
        fun of(themeMode: ThemeMode) = HomeWidgetPalette(
            day = moneyColorsOf(themeMode, isSystemDark = false).toWidgetColors(),
            night = moneyColorsOf(themeMode, isSystemDark = true).toWidgetColors(),
        )
        private fun MoneyColors.toWidgetColors() = HomeWidgetColors(
            income = income, expense = expense, onButton = onAccent, badge = accent, onBadge = onAccent,
        )
    }
}
```

- [ ] **Step 4: Run tests** — PASS.
- [ ] **Step 5: Commit** `Add the home widget badge text and palette`.

---

### Task 2: Quick entry activity

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/transfers/view/QuickTransferActivity.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/transfers/view/TransfersNavigator.kt` (optional `fallbackAccount`)
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/ua/com/radiokot/money/transfers/view/QuickTransferDirectionTest.kt`

**Interfaces:**
- Consumes: `TransferShortcutActivity` structure, `TransferCounterpartySelectionSheetRoute`, `transferSheet`, `MostUsedAccountSource` (session scope), `AccountRepository.getAccount(id)`.
- Produces (used by Task 3):
  ```kotlin
  enum class QuickTransferDirection(val action: String) {
      Income("ua.com.radiokot.money.actions.QUICK_INCOME"),
      Expense("ua.com.radiokot.money.actions.QUICK_EXPENSE");
      companion object { fun fromAction(action: String?): QuickTransferDirection }
  }
  class QuickTransferActivity { companion object { fun intent(context: Context, direction: QuickTransferDirection): Intent } }
  ```
  `intent()` returns an explicit intent with `action = direction.action` and flags `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`.

- [ ] **Step 1: Failing test**

```kotlin
class QuickTransferDirectionTest {
    @Test fun parsesIncome() = assertEquals(QuickTransferDirection.Income, QuickTransferDirection.fromAction(QuickTransferDirection.Income.action))
    @Test fun parsesExpense() = assertEquals(QuickTransferDirection.Expense, QuickTransferDirection.fromAction(QuickTransferDirection.Expense.action))
    @Test fun unknownActionIsExpense() {
        assertEquals(QuickTransferDirection.Expense, QuickTransferDirection.fromAction(null))
        assertEquals(QuickTransferDirection.Expense, QuickTransferDirection.fromAction("x"))
    }
}
```
Put `QuickTransferDirection` in its own file `transfers/view/QuickTransferDirection.kt` (no Android imports) so the test is pure JVM.

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: `TransfersNavigator` fallback.** Add a constructor parameter (last, default null):

```kotlin
    /**
     * Account for a category without a last used one; null keeps the account selection.
     */
    private val fallbackAccount: (suspend () -> Account?)? = null,
```
and in `proceedToTransfer(category, navOptions)`:
```kotlin
            val lastUsedAccount = lastUsedAccountsByCategoryFlow.first()[category.id]
                ?: fallbackAccount?.invoke()
```
Existing `TransfersModule` factory call is unchanged (default null).

- [ ] **Step 4: Activity.** Model on `TransferShortcutActivity.kt` (read it first):

```kotlin
class QuickTransferActivity : MoneyAppActivity(
    requiresSession = true,
    requiresUnlocking = true,
) {
    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val direction = QuickTransferDirection.fromAction(intent.action)
        setContent {
            MoneyAppTheme(paintWindow = false) {
                UserSessionScope {
                    QuickTransferScreen(direction = direction, finishActivity = ::finish)
                }
            }
        }
    }

    // The quick entry lives in its own task: open the sign-in in the app task.
    override fun goToAuth() {
        startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        fun intent(context: Context, direction: QuickTransferDirection): Intent =
            Intent(context, QuickTransferActivity::class.java)
                .setAction(direction.action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
```
`QuickTransferScreen`: same as `TransferShortcutScreen` but
- builds the navigator directly: `TransfersNavigator(getLastUsedAccountsByCategoryUseCase = koinInject(), isIncognito = false, navController = navController, fallbackAccount = { mostUsedAccountSource.getMostUsedAccountId()?.let { accountRepository.getAccount(it) } })` with `koinInject<MostUsedAccountSource>()` and `koinInject<AccountRepository>()`, inside `remember(navController)`.
- start destination `TransferCounterpartySelectionSheetRoute(isForSource = direction == Income, alreadySelectedCounterpartyId = null, isIncognito = false, showAccounts = false, showCategories = true)` (an income category is the source of an income).
- `transferSheet(onProceedToTransferCounterpartySelection = { … isIncognito = false … }, onTransferDone = finishActivity)`.
- back stack empty → `finishActivity()`; `MoneyAppModalBottomSheetHost(navController)`.
- Status bar icons: let `MoneyAppTheme` decide (do not force light/dark); if the shortcut activity's `isAppearanceLightStatusBars = false` is needed for the dim scrim, copy it.

- [ ] **Step 5: Manifest** (inside `<application>`, next to the shortcut activity):
```xml
        <!-- Quick entry from the home screen widget: its own task, back returns to the launcher. -->
        <activity
            android:name=".transfers.view.QuickTransferActivity"
            android:excludeFromRecents="true"
            android:exported="false"
            android:taskAffinity="${applicationId}.quickentry"
            android:theme="@style/TransparentActivity"
            android:windowSoftInputMode="adjustNothing" />
```
- [ ] **Step 6:** `testDebugUnitTest --tests "*QuickTransferDirectionTest"` PASS; `assembleDebug` PASS.
- [ ] **Step 7: Commit** `Add the quick entry activity for the home widget`.

---

### Task 3: Glance widget

**Files:**
- Modify: `app/build.gradle` (add `implementation "androidx.glance:glance-appwidget:1.2.0"`)
- Modify: `tools/icons/import_tabler.py` (`UI_ICONS` += `"arrow-up", "arrow-down"`), run it to generate `res/drawable/ic_tabler_arrow_up.xml`, `ic_tabler_arrow_down.xml` (`python tools/icons/import_tabler.py`; commit only the two new drawables plus any change the script makes to tracked files; if the script rewrites other generated files identically, fine; never commit `tools/icons/.cache`).
- Create: `widget/view/QuickEntryWidget.kt`, `widget/view/QuickEntryWidgetReceiver.kt`
- Create: `res/xml/quick_entry_widget_info.xml`, `res/layout/widget_quick_entry_preview.xml`, `res/drawable/widget_circle.xml`, `res/drawable/widget_badge.xml`, `res/drawable/widget_quick_entry_preview_image.xml`
- Modify: `AndroidManifest.xml`, `values/strings.xml`, `values-ru/strings.xml`

**Interfaces:**
- Consumes: `HomeWidgetBadge.text`, `HomeWidgetPalette.of` (Task 1); `QuickTransferActivity.intent`, `QuickTransferDirection` (Task 2); `HomeActivity.EXTRA_OPEN_INBOX`; `ThemePreferences.themeMode: StateFlow<ThemeMode>` (root Koin); `InboxRepository.getPendingCountFlow()` (session scope, `getKoin().getScopeOrNull(DI_SCOPE_SESSION)`).
- Produces (Task 4): `class QuickEntryWidget : GlanceAppWidget()` with a no-arg constructor.

- [ ] **Step 1:** Add the dependency; run `assembleDebug` to make sure it resolves.
- [ ] **Step 2:** Icons via the script (above).
- [ ] **Step 3: Strings** (EN / RU):
```xml
<string name="widget_quick_entry_label">Quick entry</string>
<string name="widget_quick_entry_description">Add an income or an expense from the home screen and see how many payments wait in the Inbox</string>
<string name="widget_open_inbox">Open Inbox</string>
<plurals name="widget_open_inbox_pending">
    <item quantity="one">Open Inbox, %d payment waits</item>
    <item quantity="other">Open Inbox, %d payments wait</item>
</plurals>
```
```xml
<string name="widget_quick_entry_label">Быстрый ввод</string>
<string name="widget_quick_entry_description">Добавляйте доход или расход с главного экрана и видите, сколько платежей ждёт во «Входящих»</string>
<string name="widget_open_inbox">Открыть «Входящие»</string>
<plurals name="widget_open_inbox_pending">
    <item quantity="one">Открыть «Входящие», ждёт %d платёж</item>
    <item quantity="few">Открыть «Входящие», ждут %d платежа</item>
    <item quantity="many">Открыть «Входящие», ждут %d платежей</item>
    <item quantity="other">Открыть «Входящие», ждут %d платежа</item>
</plurals>
```
Income/expense content descriptions reuse `add_income_shortcut_title` / `add_expense_shortcut_title`.

- [ ] **Step 4: Widget info** `res/xml/quick_entry_widget_info.xml`:
```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_quick_entry_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="180dp"
    android:minHeight="40dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:targetCellWidth="3"
    android:targetCellHeight="1"
    android:maxResizeHeight="110dp"
    android:resizeMode="horizontal"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:previewLayout="@layout/widget_quick_entry_preview"
    android:previewImage="@drawable/widget_quick_entry_preview_image" />
```
(`description` needs API 31; the label is the receiver's `android:label`.) Use `tools:targetApi` only if lint complains.

- [ ] **Step 5: Drawables / preview.** `widget_circle.xml` = `<shape android:shape="oval"><solid android:color="#FFFFFFFF"/></shape>` (tinted at runtime). `widget_quick_entry_preview.xml` = horizontal `LinearLayout` (match_parent, gravity center) with three 48 dp `FrameLayout`/`ImageView`s: green-ish solid circle with the arrow up, white circle with the pear foreground, red-ish solid circle with the arrow down; hard-coded colours = Paper `income` `#137A4C`, `expense` `#BF4310` (via `android:backgroundTint` on `@drawable/widget_circle` and `android:tint="#FFFFFFFF"` on the arrows). `widget_quick_entry_preview_image.xml` = `layer-list` 180×48 dp with three oval items (gravity/left insets) in the same colours (no arrows needed).

- [ ] **Step 6: Widget** `widget/view/QuickEntryWidget.kt`:

```kotlin
class QuickEntryWidget : GlanceAppWidget(), KoinComponent {
    private val log by lazyLogger("QuickEntryWidget")

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val themeMode = get<ThemePreferences>().themeMode.value
        val pendingCount = readPendingCount()
        provideContent {
            QuickEntryWidgetContent(
                palette = HomeWidgetPalette.of(themeMode),
                pendingCount = pendingCount,
            )
        }
    }

    /** One COUNT query; 0 without a session or on an error. */
    private suspend fun readPendingCount(): Long {
        val sessionScope = getKoin().getScopeOrNull(DI_SCOPE_SESSION)
            ?.takeIf { it.isNotClosed() } ?: return 0
        return runCatching { sessionScope.get<InboxRepository>().getPendingCountFlow().first() }
            .onFailure { log.error(it) { "readPendingCount(): failed" } }
            .getOrDefault(0)
    }
}
```
Content (Glance composables, `androidx.glance.*` imports only — not foundation):
```kotlin
@Composable
private fun QuickEntryWidgetContent(palette: HomeWidgetPalette, pendingCount: Long) {
    val context = LocalContext.current
    fun color(pick: (HomeWidgetColors) -> Color) = ColorProvider(day = pick(palette.day), night = pick(palette.night))
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundButton(background = color { it.income }, icon = R.drawable.ic_tabler_arrow_up, iconTint = color { it.onButton },
            contentDescription = context.getString(R.string.add_income_shortcut_title),
            onClick = actionStartActivity(QuickTransferActivity.intent(context, QuickTransferDirection.Income)))
        Spacer(GlanceModifier.defaultWeight())
        InboxButton(...)   // white circle (ColorProvider(Color.White)), pear Image, badge Box aligned TopEnd
        Spacer(GlanceModifier.defaultWeight())
        RoundButton(expense, ic_tabler_arrow_down, ...)
    }
}
```
- Put `Spacer(GlanceModifier.width(8.dp))` at both ends too, so circles do not touch the cell edge; with `defaultWeight` spacers the row spreads when resized wider.
- Circle: `Box(GlanceModifier.size(48.dp).background(ImageProvider(R.drawable.widget_circle), colorFilter = ColorFilter.tint(background)).clickable(onClick), contentAlignment = Alignment.Center) { Image(ImageProvider(icon), contentDescription, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(iconTint)) }`. If `background(ImageProvider, colorFilter)` is not available in 1.2.0, stack an `Image(ImageProvider(R.drawable.widget_circle), colorFilter = tint, modifier = fillMaxSize)` under the icon in the Box.
- Inbox button: outer `Box(size(56.dp))` with the 48 dp white circle centred containing `Image(ImageProvider(R.drawable.pear_by_francesco_cesqo_stefanini_from_noun_project_cc_by_3_0), size(48.dp))`, and when `HomeWidgetBadge.text(pendingCount)` is non-null a badge `Box(GlanceModifier.height(18.dp).padding(horizontal = 5.dp).background(ImageProvider(R.drawable.widget_badge), colorFilter = tint(badge)))` aligned top-end with `Text(text, style = TextStyle(color = color { it.onBadge }, fontSize = 11.sp, fontWeight = FontWeight.Bold))`. `widget_badge.xml` = a rectangle shape with 9 dp corners, white solid (tinted). Content description: `context.resources.getQuantityString(R.plurals.widget_open_inbox_pending, n, n)` when n > 0, else `widget_open_inbox`. Click: `actionStartActivity(Intent(context, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_OPEN_INBOX, true).addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP))` (same intent as `PaymentQuestionNotifier`).
- Content descriptions use `context.getString`, so the app language applies on API 33+ (as F3 notes for notifications).

`QuickEntryWidgetReceiver.kt`:
```kotlin
class QuickEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryWidget()
}
```
Manifest:
```xml
        <receiver
            android:name=".widget.view.QuickEntryWidgetReceiver"
            android:exported="true"
            android:label="@string/widget_quick_entry_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/quick_entry_widget_info" />
        </receiver>
```
- [ ] **Step 7:** `assembleDebug`, `lintDebug` PASS (fix any lint errors in new files; lint warnings in new files that are cheap to fix should be fixed).
- [ ] **Step 8: Commit** `Add the Quick entry home screen widget`.

---

### Task 4: Badge updates without polling

**Files:**
- Create: `widget/logic/DebouncedTrigger.kt`, `widget/logic/HomeWidgetUpdater.kt`, `widget/logic/HomeWidgetBadgeStarter.kt`, `widget/HomeWidgetModule.kt`
- Modify: `home/HomeModule.kt` (include `homeWidgetModule`), `inbox/listener/BankNotificationListenerService.kt` (after `Outcome.Pending`), `MoneyApp.kt` (`initTheme` collector)
- Test: `app/src/test/java/ua/com/radiokot/money/widget/logic/DebouncedTriggerTest.kt`

**Interfaces:**
- Consumes: `QuickEntryWidget` (Task 3), `UserSessionScopeListener`, `InboxRepository.getPendingCountFlow()`.
- Produces: `fun interface HomeWidgetUpdater { fun requestUpdate() }`; `class GlanceHomeWidgetUpdater(context: Context, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default), debounceMs: Long = 500) : HomeWidgetUpdater`; `class DebouncedTrigger(scope: CoroutineScope, delayMs: Long, action: suspend () -> Unit) { fun trigger() }`.

- [ ] **Step 1: Failing test** (real time, small delays, `runBlocking` like the other tests; no coroutines-test dependency):

```kotlin
class DebouncedTriggerTest {
    @Test fun burstCollapsesToOneRun() = runBlocking {
        val runs = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val trigger = DebouncedTrigger(scope, delayMs = 100) { runs.incrementAndGet() }
        repeat(20) { trigger.trigger() }
        delay(400)
        assertEquals(1, runs.get())
        scope.cancel()
    }

    @Test fun separateBurstsRunSeparately() = runBlocking {
        val runs = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val trigger = DebouncedTrigger(scope, delayMs = 50) { runs.incrementAndGet() }
        trigger.trigger(); delay(300)
        trigger.trigger(); delay(300)
        assertEquals(2, runs.get())
        scope.cancel()
    }

    @Test fun failingActionDoesNotStopLaterRuns() = runBlocking {
        val runs = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val trigger = DebouncedTrigger(scope, delayMs = 50) {
            if (runs.incrementAndGet() == 1) error("boom")
        }
        trigger.trigger(); delay(300)
        trigger.trigger(); delay(300)
        assertEquals(2, runs.get())
        scope.cancel()
    }
}
```
- [ ] **Step 2:** run, FAIL.
- [ ] **Step 3: Implement**

```kotlin
/**
 * Runs [action] once [delayMs] after the last [trigger] call; a newer call restarts the wait.
 * An action failure is logged and does not stop later runs.
 */
class DebouncedTrigger(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val action: suspend () -> Unit,
) {
    private val log by lazyLogger("DebouncedTrigger")
    private val lock = Any()
    private var job: Job? = null

    fun trigger() = synchronized(lock) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                log.error(e) { "trigger(): action failed" }
            }
        }
    }
}
```
(If `lazyLogger` needs an Android context in unit tests, check how other tested classes log — `lazyLogger` is used in tested classes, e.g. `NotificationSourceRegistry`.)

```kotlin
fun interface HomeWidgetUpdater { fun requestUpdate() }

/** Redraws every placed Quick entry widget; bursts collapse into one update. */
class GlanceHomeWidgetUpdater(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    debounceMs: Long = 500,
) : HomeWidgetUpdater {
    private val appContext = context.applicationContext
    private val trigger = DebouncedTrigger(scope, debounceMs) { QuickEntryWidget().updateAll(appContext) }
    override fun requestUpdate() = trigger.trigger()
}
```

```kotlin
/**
 * Keeps the widget badge in sync with the pending inbox count of every new session
 * without polling: the count is a PowerSync watch that only emits on table changes.
 * Closing the session scope (sign-out) redraws the widget without the badge.
 */
class HomeWidgetBadgeStarter(
    private val updater: HomeWidgetUpdater,
) : UserSessionScopeListener {
    override fun onSessionScopeCreated(sessionScope: Scope) {
        sessionScope.registerCallback(object : ScopeCallback {
            override fun onScopeClose(scope: Scope) = updater.requestUpdate()
        })
        val coroutineScope = sessionScope.get<CoroutineScope>(named(HOME_WIDGET_COROUTINE_SCOPE))
        coroutineScope.launch {
            sessionScope.get<InboxRepository>()
                .getPendingCountFlow()
                .distinctUntilChanged()
                .catch { log.error(it) { ... } }
                .collect { updater.requestUpdate() }
        }
    }
}
```
Getting the repository must happen off the main thread (the listener may be called on main): do the `get` inside the launched coroutine (`Dispatchers.Default` scope) and guard `if (!sessionScope.isNotClosed()) return@launch` like `NotificationSourceRegistryStarter`. `registerCallback` signature: check Koin 4.2 (`org.koin.core.scope.ScopeCallback`, `Scope.registerCallback`).

`HomeWidgetModule.kt`:
```kotlin
const val HOME_WIDGET_COROUTINE_SCOPE = "home-widget"

val homeWidgetModule = module {
    single { GlanceHomeWidgetUpdater(context = androidContext()) } bind HomeWidgetUpdater::class
    single { HomeWidgetBadgeStarter(updater = get()) } bind UserSessionScopeListener::class
    sessionScope {
        scoped(named(HOME_WIDGET_COROUTINE_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) } onClose { it?.cancel() }
    }
}
```
Note Koin `bind UserSessionScopeListener::class` on two singles: `getAll<UserSessionScopeListener>()` returns both (check `NotificationSourceRegistryStarter` is still found; Koin allows multiple secondary-type binds of distinct primary types). Include `homeWidgetModule` in `homeModule.includes(...)` (it needs `inboxModule`).

- [ ] **Step 4: Hooks.**
  - `BankNotificationListenerService.processSourceNotification`: inside `if (outcome is Outcome.Pending)` add `runCatching { get<HomeWidgetUpdater>().requestUpdate() }` (root scope; use the same Koin access style as the file).
  - `MoneyApp.initTheme`: inside the `drop(1).collect` add `get<HomeWidgetUpdater>().requestUpdate()` after `setDefaultNightMode`.
- [ ] **Step 5:** tests PASS, `assembleDebug`, `lintDebug` PASS.
- [ ] **Step 6: Commit** `Update the widget badge when the inbox changes`.

---

### Task 5: Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` (append "## F5. Home screen widget (designed 2026-10-05, approved by the owner)")
- Modify: `docs/redesign/PROGRESS.md` (Done row F5, remove the Backlog item text and replace with "Home screen widget: done in F5 (see above)", add "## F5 device checklist")
- Modify: `docs/HANDOFF.md` (row F5 in "What exists": branch `feature/home-widget`, not verified on device, no migration needed; key places `widget/`, `transfers/view/QuickTransferActivity.kt`)

- [ ] **Step 1: Spec section** — summarise: widget (3×1, resizable horizontally, three circles, Glance, `updatePeriodMillis = 0`), badge (pending count, hidden at 0, 99+, no session → none, tap → Inbox tab via `EXTRA_OPEN_INBOX`), updates (count flow in session scope, listener pending, theme change, sign-out; debounced 500 ms; no polling, no services), quick entry (`QuickTransferActivity`, own task, translucent, category grid → transfer sheet, account last used → most used → selection, ✓ saves and finishes, back/outside finishes, lock + auth like other entry points, privacy like the normal sheet), theme (exact palette, System follows launcher night mode), strings EN + RU, preview. "Decisions: plan's Rulings section" with the plan path.
- [ ] **Step 2: F5 device checklist:**
  - Add the widget from the launcher's widget picker: label "Quick entry" / "Быстрый ввод", description and preview shown; default 3×1, resize horizontally to 2 and 4+ cells: circles spread, nothing clipped.
  - Theme: System (toggle the phone's dark mode → widget follows), Paper, Midnight, Ember, Aurora: colours change within a second of switching in Settings → Appearance.
  - Badge: equals the Inbox tab badge; hidden at 0; posts a test notification from a source → badge increments within ~1 s without opening the app; sorting in the app decrements it; 100+ shows "99+" (only if such data exists, else skip).
  - Centre tap → app opens on the Inbox tab (cold start and app already open).
  - ↓ → dimmed launcher, Expense category grid; pick a category → transfer sheet with that category and its last used account (a category never used → the most used account); type an amount, ✓ → saved, back on the launcher; the transfer is in History.
  - ↑ → Income categories; same flow; the income account is the destination.
  - Back / tap outside on the grid and on the sheet → back on the launcher, nothing saved; the quick entry does not show in Recents; opening the app afterwards shows the app's own last screen, not the quick entry.
  - Passcode on, app locked (background > threshold): ↓ → unlock screen first, then the grid; cancelling unlock → back to the launcher.
  - Signed out: badge hidden; ↓ → sign-in screen; after signing in the badge reappears.
  - Privacy mode on: category amounts and balances in the grid/sheet masked as in the app; the keypad shows the typed amount.
  - Russian: widget label, description and TalkBack descriptions ("Добавить расход", "Открыть «Входящие», ждут 3 платежа").
  - Battery: no new periodic work (`adb shell dumpsys jobscheduler | grep radiokot` shows only the existing BackgroundSync/CurrencyPricesUpdate) and no persistent service.
- [ ] **Step 3: Commit** `Document F5 home screen widget`.
