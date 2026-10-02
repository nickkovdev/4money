# UI in the Style of 1Money Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the 4Money Android fork a 1Money-like look and flow: a light/dark theme with tokens, line icons, a Categories ring, an Overview tab with charts, period swipes and motion, swipe actions on transactions, and a redesigned transfer sheet.

**Architecture:** A `MoneyTheme` CompositionLocal supplies color tokens (light + dark); AppCompat night mode, driven by a stored `ThemeMode`, switches the resources (window, dialogs) and the Compose palette together. Charts are plain `Canvas` composables fed by pure, unit-tested functions (donut segments, ring layout, overview aggregation, swipe decisions). New screens follow the existing pattern: `*ScreenNavigation.kt` route + `*ScreenViewModel` sharing `HomeViewModel` as `HistoryStatsPeriodViewModel` + Koin `sessionScope` registration.

**Tech Stack:** Kotlin, Jetpack Compose foundation (no Material), Compose Unstyled (`com.composables:core:1.49.9`, `com.composeunstyled.Text/Icon/LocalContentColor`), Koin 4.2, PowerSync, navigation-compose 2.10, kotlinx-datetime, JUnit 4. Python 3 (stdlib only) for the icon import script.

**Spec:** `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md`, section **E. UI in the style of 1Money** (E1 theme and icons, E2 Categories ring, E3 Overview tab, E4 motion and gestures, E5 transfer sheet).

## Global Constraints

- No 1Money assets are copied (proprietary). Icons come from Tabler Icons (MIT), attributed in the repo.
- No Material dependency is added. Use `androidx.compose.foundation`, `com.composeunstyled.*`, `Canvas`. No chart library.
- `MoneyTheme` color tokens via CompositionLocal; dark by default **following the system**; light/dark/system preference.
- Avatars in dark mode: circle, dark tint of the category color as background, saturated icon/letter in the category color.
- ≈150 Tabler line icons as `*_itemicon` vector drawables named `<group>_<name>_itemicon` in `app/src/main/res-packs/drawable/` so `DrawableResItemIconRepository` and the existing picker show them. The group is the part before the first `_`.
- Bottom bar emoji replaced by line icons.
- Tabs: Accounts / Categories / Transactions / Overview; More moves to a profile icon in the top-left.
- Categories: 4 columns; one regular row, then two rows with two categories on each side of a 2×2 donut ring, then regular rows. Ring center shows the expense and income totals, tap toggles.
- Overview built on `HistoryStatsRepository.getCategoryDailyAmountsFlow`.
- Horizontal swipe on content switches the period on Categories, Transactions, Overview; tabs switch only by tapping the bottom bar; vertical scrolling must not be hijacked.
- Existing transfer behaviour must keep working: editing a transfer, income direction, account→account transfers, different-currency source/destination amounts.
- Every task leaves the app building (`assembleDebug`) and unit tests green (`testDebugUnitTest`).
- Commands run in Git Bash from `E:\projects\4money`:
  - `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest`
  - `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat assembleDebug`
  - A single test class: append `--tests 'ua.com.radiokot.money.<package>.<ClassName>'` to `testDebugUnitTest`.
- New Kotlin files start with the same GPL header the existing files use (copy it from any file, e.g. `home/view/HomeViewModel.kt`, keep "Copyright 2025 Oleg Koretsky" as the project header). It is omitted from the code blocks below for brevity.
- All Kotlin sources live under `app/src/main/java/ua/com/radiokot/money/` (abbreviated `…/money/` below); tests under `app/src/test/java/ua/com/radiokot/money/`.

## Review Focus

1. **A user who picked "Light" while the phone is dark (and vice versa).** Every activity, the window background, AlertDialogs, the date picker, bottom sheets and the navigation bar icons must follow the stored preference, not the system. Pinned by `ThemeModeTest` (Task 1) plus the manual matrix in Task 2 Step 5.
2. **Categories in different currencies.** The ring shares and Overview bars must be computed in the primary currency; summing raw minor units of UAH and EUR would be wrong. Pinned by `DailyAmountConversionTest` (Task 9) and the per-category primary amount used by Tasks 11 and 13.
3. **Overview on a period that is not "this month"**: a past month must average over all its days, a future month must show zeros without dividing by zero, "the entire time" must not try to draw 20 000 bars. Pinned by `OverviewStatsCalculatorTest` (Task 12).
4. **Swipe gestures on scrollable content.** A vertical fling on the Categories grid or the Transactions list must scroll, not change the month; a left swipe on a transaction row reveals actions instead of changing the month. Pinned by `PeriodSwipeTest` and `SwipeRevealTest` (Tasks 16, 19) plus the manual checks there.
5. **Deleting a transaction and leaving the screen before the undo timeout.** The deletion must still be committed (not lost) and undo must never revert a transfer twice. Pinned by `PendingTransferDeletionsTest` (Task 19).

---

## File Structure

New files (all paths under `…/money/` unless absolute):

| File | Responsibility |
|---|---|
| `theme/data/ThemeMode.kt` | `ThemeMode` enum (System/Light/Dark), stored name, AppCompat night-mode mapping |
| `theme/data/ThemePreferences.kt`, `theme/data/ThemePreferencesOnPrefs.kt` | Persisted theme mode (SharedPreferences, same pattern as `CurrencyPreferencesOnPrefs`) |
| `theme/ThemeModule.kt` | Koin `single` for `ThemePreferences` |
| `uikit/theme/MoneyColors.kt` | `MoneyColors` token class, `LightMoneyColors`, `DarkMoneyColors` |
| `uikit/theme/MoneyTheme.kt` | `LocalMoneyColors`, `MoneyTheme { }` composable, `MoneyTheme.colors` accessor |
| `currency/view/rememberViewAmountFormat.kt` | Theme-aware `ViewAmountFormat` factory |
| `colors/data/ItemColorSchemeAccents.kt` | Pure: accent (saturated) color of a scheme family; dark avatar colors |
| `tools/icons/import_tabler.py` (repo root) | Downloads pinned Tabler release, converts chosen SVGs to VectorDrawables |
| `tools/icons/LICENSE-tabler-icons.txt`, `tools/icons/README.md` | MIT license + attribution and how to re-run |
| `tools/icons/assign_category_icons.sql` | One-off: sets `money.categories.icon` by title |
| `currency/logic/DailyAmountConversion.kt` | Pure: convert a day amount to the primary currency with previous-day price fallback |
| `uikit/chart/DonutSegments.kt`, `uikit/chart/DonutRing.kt` | Pure donut geometry + animated Canvas ring |
| `categories/view/CategoryRingLayout.kt`, `categories/view/CategoryRingGrid.kt` | Pure 4-column ring layout + the grid composable |
| `overview/logic/OverviewStats.kt`, `overview/logic/OverviewStatsCalculator.kt` | Pure aggregation for Overview |
| `overview/logic/GetOverviewStatsUseCase.kt` | Flows → converted amounts → `OverviewData` |
| `overview/view/*` | `OverviewScreenNavigation.kt`, `OverviewScreen.kt`, `OverviewScreenViewModel.kt`, `ViewOverview.kt` |
| `overview/OverviewModule.kt` | Koin registrations |
| `uikit/chart/StackedBarChart.kt` | Animated Canvas stacked bars |
| `transfers/history/view/PeriodSwipe.kt` | Pure swipe decision + `Modifier.periodSwipe` + `PeriodSlideContainer` |
| `currency/view/AnimatedAmountText.kt` | Counter-animated amount text (reuses `animateAmountValueAsState`) |
| `transfers/view/SwipeRevealRow.kt` | Row with swipe-to-reveal actions + pure settle decision |
| `transfers/history/view/PendingTransferDeletions.kt` | Pure undo queue for deletions |
| `transfers/view/TransferSheetLabels.kt` | Pure labels for the sheet halves and amount kind |
| `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml` | Window colors per night mode |
| `app/src/main/res/anim/slide_*.xml` | Activity transition animations |

Modified files are listed per task.

---

## E1 — Theme and icons

### Task 1: Theme foundation (ThemeMode, preferences, MoneyTheme, night resources)

**Files:**
- Create: `…/money/theme/data/ThemeMode.kt`, `…/money/theme/data/ThemePreferences.kt`, `…/money/theme/data/ThemePreferencesOnPrefs.kt`, `…/money/theme/ThemeModule.kt`
- Create: `…/money/uikit/theme/MoneyColors.kt`, `…/money/uikit/theme/MoneyTheme.kt`
- Create: `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `…/money/MoneyApp.kt` (modules list, `initTheme()`)
- Modify: `…/money/auth/view/AuthActivity.kt` (`ComponentActivity` → `AppCompatActivity`)
- Modify (wrap `setContent` body in `MoneyTheme { }`): `home/view/HomeActivity.kt`, `auth/view/AuthActivity.kt`, `accounts/view/ArchivedAccountsActivity.kt`, `accounts/view/EditAccountActivity.kt`, `categories/view/EditCategoryActivity.kt`, `lock/view/SetUpPasscodeActivity.kt`, `lock/view/UnlockActivity.kt`, `transfers/view/TransferShortcutActivity.kt`
- Modify: `…/money/BottomSheetNavigation.kt` (navigation bar appearance)
- Test: `app/src/test/java/ua/com/radiokot/money/theme/data/ThemeModeTest.kt`

**Interfaces:**
- Produces: `enum class ThemeMode { System, Light, Dark }` with `storedName: String`, `appCompatNightMode: Int`, `ThemeMode.fromStoredName(String?): ThemeMode`.
- Produces: `interface ThemePreferences { val themeMode: MutableStateFlow<ThemeMode> }`, Koin `themeModule` (global `single`, not session-scoped).
- Produces: `class MoneyColors` (tokens listed in Step 5), `LightMoneyColors`, `DarkMoneyColors`, `LocalMoneyColors`, `@Composable fun MoneyTheme(isDark: Boolean = isSystemInDarkTheme(), content)`, `MoneyTheme.colors` (`@Composable` getter).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/theme/data/ThemeModeTest.kt`:

```kotlin
package ua.com.radiokot.money.theme.data

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert
import org.junit.Test

class ThemeModeTest {

    @Test
    fun fromStoredName_KnownNames() {
        Assert.assertEquals(ThemeMode.System, ThemeMode.fromStoredName("system"))
        Assert.assertEquals(ThemeMode.Light, ThemeMode.fromStoredName("light"))
        Assert.assertEquals(ThemeMode.Dark, ThemeMode.fromStoredName("dark"))
    }

    @Test
    fun fromStoredName_FallsBackToSystem() {
        Assert.assertEquals(ThemeMode.System, ThemeMode.fromStoredName(null))
        Assert.assertEquals(ThemeMode.System, ThemeMode.fromStoredName(""))
        Assert.assertEquals(ThemeMode.System, ThemeMode.fromStoredName("purple"))
    }

    @Test
    fun appCompatNightMode() {
        Assert.assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            ThemeMode.System.appCompatNightMode,
        )
        Assert.assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            ThemeMode.Light.appCompatNightMode,
        )
        Assert.assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            ThemeMode.Dark.appCompatNightMode,
        )
    }

    @Test
    fun storedNamesAreUnique() {
        Assert.assertEquals(
            ThemeMode.entries.size,
            ThemeMode.entries.map(ThemeMode::storedName).toSet().size,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.theme.data.ThemeModeTest'`
Expected: FAIL, compilation error `Unresolved reference: ThemeMode`.

- [ ] **Step 3: Implement ThemeMode and preferences**

`…/money/theme/data/ThemeMode.kt`:

```kotlin
package ua.com.radiokot.money.theme.data

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(
    val storedName: String,
) {
    /**
     * Follow the system dark mode setting. The default.
     */
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    val appCompatNightMode: Int
        get() = when (this) {
            System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Light -> AppCompatDelegate.MODE_NIGHT_NO
            Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }

    companion object {
        fun fromStoredName(storedName: String?): ThemeMode =
            entries.firstOrNull { it.storedName == storedName }
                ?: System
    }
}
```

`…/money/theme/data/ThemePreferences.kt`:

```kotlin
package ua.com.radiokot.money.theme.data

import kotlinx.coroutines.flow.MutableStateFlow

interface ThemePreferences {

    val themeMode: MutableStateFlow<ThemeMode>
}
```

`…/money/theme/data/ThemePreferencesOnPrefs.kt` (same shape as `currency/data/CurrencyPreferencesOnPrefs.kt`):

```kotlin
package ua.com.radiokot.money.theme.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ThemePreferencesOnPrefs(
    private val sharedPreferences: SharedPreferences,
) : ThemePreferences {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val themeMode: MutableStateFlow<ThemeMode> by lazy {
        val key = "theme_mode"
        MutableStateFlow(
            ThemeMode.fromStoredName(sharedPreferences.getString(key, null))
        ).apply {
            coroutineScope.launch {
                drop(1).collect { newValue ->
                    sharedPreferences.edit {
                        putString(key, newValue.storedName)
                    }
                }
            }
        }
    }
}
```

`…/money/theme/ThemeModule.kt`:

```kotlin
package ua.com.radiokot.money.theme

import android.content.Context
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.money.theme.data.ThemePreferences
import ua.com.radiokot.money.theme.data.ThemePreferencesOnPrefs

val themeModule = module {

    single {
        ThemePreferencesOnPrefs(
            sharedPreferences = androidApplication().getSharedPreferences(
                "theme",
                Context.MODE_PRIVATE,
            )
        )
    } bind ThemePreferences::class
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.theme.data.ThemeModeTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Add the color tokens and MoneyTheme**

Light values are exactly the current hardcoded ones, so light mode looks unchanged after Tasks 3–4.

`…/money/uikit/theme/MoneyColors.kt`:

```kotlin
package ua.com.radiokot.money.uikit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
class MoneyColors(
    val isDark: Boolean,
    /** Screen background (window). */
    val background: Color,
    /** Sheets, cards. */
    val surface: Color,
    /** Keyboard keys, passcode keys, subtle fills. */
    val surfaceVariant: Color,
    /** Primary text and icons. */
    val onBackground: Color,
    /** Hints, memos, secondary lines (was Color.Gray). */
    val onBackgroundSecondary: Color,
    /** Borders and enabled outlined controls (was Color.DarkGray). */
    val outline: Color,
    /** Disabled borders and controls (was Color.LightGray). */
    val outlineDisabled: Color,
    /** Thin separators (was Color.Gray). */
    val divider: Color,
    val income: Color,
    val expense: Color,
    val neutralAmount: Color,
    val bottomBar: Color,
    val bottomBarIndicator: Color,
    /** Red notice dot. */
    val notice: Color,
    val warning: Color,
    val onWarning: Color,
    val tooltip: Color,
    val onTooltip: Color,
    /** Action sheet body background (was 0xFFF9FBE7). */
    val actionSheet: Color,
    /** Selected option in selection sheets (was 0xfff8efb3). */
    val selection: Color,
    /** Unselected option in selection sheets (was 0xfff8fafd). */
    val selectionIdle: Color,
    /** "Rest" segments in charts, empty ring track. */
    val chartOther: Color,
)

val LightMoneyColors = MoneyColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3F0F6),
    onBackground = Color(0xFF000000),
    onBackgroundSecondary = Color(0xFF888888),
    outline = Color(0xFF444444),
    outlineDisabled = Color(0xFFCCCCCC),
    divider = Color(0xFF888888),
    income = Color(0xFF50AF99),
    expense = Color(0xFFD85E8C),
    neutralAmount = Color(0xFF757575),
    bottomBar = Color(0xFFF0EDF1),
    bottomBarIndicator = Color(0xFFD8CCE1),
    notice = Color(0xFFFF0000),
    warning = Color(0xFFFC9A47),
    onWarning = Color(0xFFFFFFFF),
    tooltip = Color(0xBE000000),
    onTooltip = Color(0xFFFFFFFF),
    actionSheet = Color(0xFFF9FBE7),
    selection = Color(0xFFF8EFB3),
    selectionIdle = Color(0xFFF8FAFD),
    chartOther = Color(0xFFBDBDBD),
)

val DarkMoneyColors = MoneyColors(
    isDark = true,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2D),
    onBackground = Color(0xFFEDEDED),
    onBackgroundSecondary = Color(0xFF9A9A9A),
    outline = Color(0xFF8A8A8A),
    outlineDisabled = Color(0xFF3C3C3C),
    divider = Color(0xFF3A3A3A),
    income = Color(0xFF5CC9AE),
    expense = Color(0xFFF0709E),
    neutralAmount = Color(0xFF9E9E9E),
    bottomBar = Color(0xFF1A1A1A),
    bottomBarIndicator = Color(0xFF3B3341),
    notice = Color(0xFFFF5252),
    warning = Color(0xFFE08A3C),
    onWarning = Color(0xFFFFFFFF),
    tooltip = Color(0xE6303030),
    onTooltip = Color(0xFFFFFFFF),
    actionSheet = Color(0xFF23261C),
    selection = Color(0xFF4A4320),
    selectionIdle = Color(0xFF26282C),
    chartOther = Color(0xFF4A4A4A),
)
```

`…/money/uikit/theme/MoneyTheme.kt`:

```kotlin
package ua.com.radiokot.money.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.composeunstyled.LocalContentColor

/**
 * Light by default so previews without [MoneyTheme] look like before.
 */
val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

/**
 * Provides the color tokens. [isDark] defaults to the configuration's night mode,
 * which AppCompat overrides with the stored [ua.com.radiokot.money.theme.data.ThemeMode],
 * see MoneyApp.initTheme().
 *
 * `com.composeunstyled.Text` and `Icon` read [LocalContentColor],
 * so they get the right text color without passing it explicitly.
 */
@Composable
fun MoneyTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors =
        if (isDark)
            DarkMoneyColors
        else
            LightMoneyColors

    CompositionLocalProvider(
        LocalMoneyColors provides colors,
        LocalContentColor provides colors.onBackground,
        content = content,
    )
}

object MoneyTheme {

    val colors: MoneyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMoneyColors.current
}
```

- [ ] **Step 6: Night resources**

`app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_background">#FFFFFFFF</color>
    <color name="window_foreground">#FF000000</color>
</resources>
```

`app/src/main/res/values-night/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_background">#FF121212</color>
    <color name="window_foreground">#FFEDEDED</color>
</resources>
```

Replace `app/src/main/res/values/styles.xml` so the activity and dialog themes are DayNight and use the color resources:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="DefaultActivity" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
        <item name="android:windowEnterAnimation">@android:anim/fade_in</item>
        <item name="android:windowExitAnimation">@android:anim/fade_out</item>
        <item name="android:colorPrimary">@color/window_foreground</item>
        <item name="android:colorAccent">@color/window_foreground</item>
        <item name="android:colorControlNormal">@color/window_foreground</item>
        <item name="android:colorControlActivated">@color/window_foreground</item>
        <item name="alertDialogTheme">@style/AlertDialog</item>
    </style>

    <style name="AlertDialog" parent="Theme.AppCompat.DayNight.Dialog.Alert">
        <item name="android:colorPrimary">@color/window_foreground</item>
        <item name="android:colorAccent">@color/window_foreground</item>
    </style>

    <style name="TransparentActivity" parent="Theme.AppCompat.NoActionBar">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowIsFloating">false</item>
        <item name="windowNoTitle">true</item>
    </style>

</resources>
```

No `values-night/styles.xml` is needed: the DayNight parents plus the night color resources do the work.

- [ ] **Step 7: Apply the night mode at app start and on change**

In `…/money/MoneyApp.kt`:

1. Add imports:

```kotlin
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ua.com.radiokot.money.theme.data.ThemePreferences
import ua.com.radiokot.money.theme.themeModule
```

2. In `startKoin { modules(...) }` add `themeModule` first:

```kotlin
            modules(
                themeModule,
                authModule,
                appLockModule,
                homeModule,
            )
```

3. Call `initTheme()` right after `startKoin { }` (before `initSessionHolder()`), and add to the class:

```kotlin
    private val themeScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private fun initTheme() {
        val themePreferences: ThemePreferences = get()

        AppCompatDelegate.setDefaultNightMode(
            themePreferences.themeMode.value.appCompatNightMode
        )

        // AppCompat recreates started activities when the mode changes.
        themeScope.launch {
            themePreferences.themeMode
                .drop(1)
                .collect { themeMode ->
                    log.debug {
                        "initTheme(): applying theme mode:" +
                                "\nthemeMode=$themeMode"
                    }

                    AppCompatDelegate.setDefaultNightMode(themeMode.appCompatNightMode)
                }
        }
    }
```

- [ ] **Step 8: Make AuthActivity an AppCompatActivity**

Night-mode overrides only apply to `AppCompatActivity`. In `…/money/auth/view/AuthActivity.kt` replace `import androidx.activity.ComponentActivity` with `import androidx.appcompat.app.AppCompatActivity` and `class AuthActivity : ComponentActivity()` with `class AuthActivity : AppCompatActivity()`.

- [ ] **Step 9: Wrap every `setContent` in MoneyTheme**

In each of the 8 activities listed under **Files**, add `import ua.com.radiokot.money.uikit.theme.MoneyTheme` and change

```kotlin
        setContent {
            <existing body>
        }
```

to

```kotlin
        setContent {
            MoneyTheme {
                <existing body>
            }
        }
```

In `home/view/HomeActivity.kt` also make the navigation bar style follow the theme (the bottom bar is drawn behind it). Replace

```kotlin
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                scrim = 0,
                darkScrim = 0,
            ),
        )
```

with

```kotlin
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
```

`AuthActivity` does not call `enableEdgeToEdge()`; leave it so.

- [ ] **Step 10: Bottom sheet navigation bar icons**

In `…/money/BottomSheetNavigation.kt`, inside `ModalBottomSheet { ImmediateLaunchedEffect { … } }` (the lambda is composable), replace

```kotlin
            WindowInsetsControllerCompat(modalWindow, modalWindow.decorView)
                .isAppearanceLightNavigationBars = true
```

with

```kotlin
            WindowInsetsControllerCompat(modalWindow, modalWindow.decorView)
                .isAppearanceLightNavigationBars = !MoneyTheme.colors.isDark
```

and add `import ua.com.radiokot.money.uikit.theme.MoneyTheme`.

- [ ] **Step 11: Build and run all tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. On a device with system dark mode the window background is now dark; most text is still black (fixed in Tasks 3–4).

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/theme app/src/main/java/ua/com/radiokot/money/uikit/theme \
  app/src/main/res/values/colors.xml app/src/main/res/values-night app/src/main/res/values/styles.xml \
  app/src/main/java/ua/com/radiokot/money/MoneyApp.kt app/src/main/java/ua/com/radiokot/money/BottomSheetNavigation.kt \
  app/src/main/java/ua/com/radiokot/money/*/view/*Activity.kt app/src/main/java/ua/com/radiokot/money/transfers/view/TransferShortcutActivity.kt \
  app/src/test/java/ua/com/radiokot/money/theme
git commit -m "Add MoneyTheme color tokens and system-following night mode"
```

---

### Task 2: Theme preference in PreferencesScreen

**Files:**
- Modify: `…/money/preferences/view/PreferencesScreenViewModel.kt`
- Modify: `…/money/preferences/view/PreferencesScreen.kt`
- Modify: `…/money/preferences/PreferencesModule.kt`

**Interfaces:**
- Consumes: `ThemePreferences`, `ThemeMode`, `themeModule` (Task 1).
- Produces: `PreferencesScreenViewModel.themeMode: StateFlow<ThemeMode>`, `fun onThemeModeClicked(mode: ThemeMode)`.

- [ ] **Step 1: ViewModel**

In `PreferencesScreenViewModel` add constructor parameter `private val themePreferences: ThemePreferences,` (after `disableAppLockUseCase`), imports `ua.com.radiokot.money.theme.data.ThemeMode`, `ua.com.radiokot.money.theme.data.ThemePreferences`, and:

```kotlin
    val themeMode: StateFlow<ThemeMode> =
        themePreferences.themeMode

    fun onThemeModeClicked(mode: ThemeMode) {
        if (themePreferences.themeMode.value == mode) {
            return
        }

        log.debug {
            "onThemeModeClicked(): switching theme mode:" +
                    "\nmode=$mode"
        }

        themePreferences.themeMode.value = mode
    }
```

- [ ] **Step 2: Koin**

In `PreferencesModule.kt` add `themeModule` to `includes(...)` (import `ua.com.radiokot.money.theme.themeModule`) and `themePreferences = get(),` to the `PreferencesScreenViewModel(...)` call.

- [ ] **Step 3: UI section**

In the private `PreferencesScreen(...)` add parameters `themeMode: State<ThemeMode>, onThemeModeClicked: (ThemeMode) -> Unit,`. Insert this block right before the `Text(text = "Currency", …)` header:

```kotlin
    Text(
        text = "Appearance",
        fontSize = 16.sp,
        fontWeight = FontWeight(500),
    )

    Spacer(modifier = Modifier.height(12.dp))

    ThemeMode.entries.forEach { mode ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { onThemeModeClicked(mode) },
                )
                .padding(
                    vertical = 10.dp,
                )
        ) {
            Text(
                text = when (mode) {
                    ThemeMode.System -> "Follow the system"
                    ThemeMode.Light -> "Light"
                    ThemeMode.Dark -> "Dark"
                },
                modifier = Modifier
                    .weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        width = 1.dp,
                        color = MoneyTheme.colors.outline,
                        shape = CircleShape,
                    )
                    .padding(4.dp)
                    .then(
                        if (themeMode.value == mode)
                            Modifier.background(
                                color = MoneyTheme.colors.onBackground,
                                shape = CircleShape,
                            )
                        else
                            Modifier
                    )
            )
        }
    }

    Spacer(modifier = Modifier.height(40.dp))
```

Add imports `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.shape.CircleShape`, `ua.com.radiokot.money.theme.data.ThemeMode`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`.

In the public `PreferencesScreen(viewModel)` pass `themeMode = viewModel.themeMode.collectAsState(), onThemeModeClicked = remember { viewModel::onThemeModeClicked },`; in the preview pass `themeMode = ThemeMode.System.let(::mutableStateOf), onThemeModeClicked = {},`.

- [ ] **Step 4: Build**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual check (theme matrix)**

Install `app/build/outputs/apk/debug/*.apk`. For system light and system dark, select each of the three options on More. Expected: the screen recreates; window background, the sign-out AlertDialog, and the date picker in the transfer sheet follow the selected mode; after killing and reopening the app the choice is kept; the auth screen (sign out → sign in) also follows it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/preferences
git commit -m "Add light/dark/system theme preference"
```

---

### Task 3: Tokens, part A — amounts, text and uikit

Make every text, amount and uikit control read the theme. `BasicText` ignores `LocalContentColor` and always draws black, so all 45 `BasicText` calls move to `com.composeunstyled.Text`, which has the same named parameters (`text`, `modifier`, `style`, `overflow`, `maxLines`) and reads `LocalContentColor`.

**Files:**
- Modify: `…/money/currency/view/ViewAmountFormat.kt`
- Create: `…/money/currency/view/rememberViewAmountFormat.kt`
- Modify (BasicText → Text, scripted): `accounts/view/AccountActionSheet.kt`, `accounts/view/AccountList.kt`, `accounts/view/AccountsScreen.kt`, `categories/view/CategoriesScreen.kt`, `categories/view/CategoryGrid.kt`, `categories/view/SelectableSubcategoryRow.kt`, `colors/view/ItemLogo.kt`, `colors/view/ItemLogoScreen.kt`, `transfers/history/view/ActivityScreen.kt`, `transfers/history/view/PeriodBar.kt`, `transfers/view/TransferCounterpartySelector.kt`, `transfers/view/TransferList.kt`, `transfers/view/TransferSheet.kt`, `uikit/TextButton.kt`
- Modify (amount colors): `categories/view/CategoriesScreen.kt:120-137`, `transfers/view/TransferList.kt:74-77,230-243,320`, plus every `remember(locale) { ViewAmountFormat(locale) }` in `accounts/view/AccountActionSheet.kt:136`, `accounts/view/AccountList.kt:369,441`, `accounts/view/AccountsScreen.kt:285`, `categories/view/CategoriesScreen.kt:124`, `categories/view/CategoryActionSheet.kt:211`, `categories/view/CategoryGrid.kt:234`, `transfers/history/view/ActivityScreen.kt:140`, `transfers/view/TransferList.kt:76`
- Modify: `uikit/TextButton.kt`, `uikit/RedToggleSwitch.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/currency/view/ViewAmountFormatTest.kt`

**Interfaces:**
- Consumes: `MoneyTheme.colors` (Task 1).
- Produces: `ViewAmountFormat(locale, positiveColor = Color(0xff50af99), negativeColor = Color(0xffd85e8c), zeroColor = Color(0xff757575))`; `@Composable fun rememberViewAmountFormat(): ViewAmountFormat`.

- [ ] **Step 1: Write the failing test**

Append to `ViewAmountFormatTest`:

```kotlin
    @Test
    fun invoke_UsesConfiguredSignColors() {
        val positive = androidx.compose.ui.graphics.Color(0xFF00FF00)
        val negative = androidx.compose.ui.graphics.Color(0xFFFF0000)
        val zero = androidx.compose.ui.graphics.Color(0xFF0000FF)
        val format = ViewAmountFormat(
            locale = Locale.ENGLISH,
            positiveColor = positive,
            negativeColor = negative,
            zeroColor = zero,
        )

        Assert.assertEquals(
            positive,
            format(value = BigInteger("150"), currency = usd).spanStyles.first().item.color,
        )
        Assert.assertEquals(
            negative,
            format(value = BigInteger("-150"), currency = usd).spanStyles.first().item.color,
        )
        Assert.assertEquals(
            zero,
            format(value = BigInteger.ZERO, currency = usd).spanStyles.first().item.color,
        )
    }

    @Test
    fun invoke_CustomColorWins() {
        val custom = androidx.compose.ui.graphics.Color(0xFF123456)
        val format = ViewAmountFormat(locale = Locale.ENGLISH)

        Assert.assertEquals(
            custom,
            format(value = BigInteger("150"), currency = usd, customColor = custom)
                .spanStyles.first().item.color,
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.currency.view.ViewAmountFormatTest'`
Expected: FAIL, `No parameter with name 'positiveColor' found`.

- [ ] **Step 3: Implement configurable colors**

In `ViewAmountFormat.kt` change the constructor to

```kotlin
class ViewAmountFormat(
    private val locale: Locale,
    private val positiveColor: Color = Color(0xff50af99),
    private val negativeColor: Color = Color(0xffd85e8c),
    private val zeroColor: Color = Color(0xff757575),
) {
```

and in `invoke(value, currency, customColor)` replace

```kotlin
                color = customColor ?: when (value.signum()) {
                    1 -> Color(0xff50af99)
                    -1 -> Color(0xffd85e8c)
                    else -> Color(0xff757575)
                }
```

with

```kotlin
                color = customColor ?: when (value.signum()) {
                    1 -> positiveColor
                    -1 -> negativeColor
                    else -> zeroColor
                }
```

Create `…/money/currency/view/rememberViewAmountFormat.kt`:

```kotlin
package ua.com.radiokot.money.currency.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A [ViewAmountFormat] for the current locale colored with the current theme.
 */
@Composable
fun rememberViewAmountFormat(): ViewAmountFormat {
    val locale = LocalConfiguration.current.locales.get(0)
    val colors = MoneyTheme.colors

    return remember(locale, colors) {
        ViewAmountFormat(
            locale = locale,
            positiveColor = colors.income,
            negativeColor = colors.expense,
            zeroColor = colors.neutralAmount,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.currency.view.ViewAmountFormatTest'`
Expected: PASS.

- [ ] **Step 5: Use the theme-aware format everywhere**

In each file listed under "every `remember(locale) { ViewAmountFormat(locale) }`", replace the block

```kotlin
    val locale = LocalConfiguration.current.locales.get(0)   // or .locales[0]
    val amountFormat = remember(locale) {
        ViewAmountFormat(locale)
    }
```

with

```kotlin
    val amountFormat = rememberViewAmountFormat()
```

Remove the now-unused `locale` variable only where nothing else in that scope uses it (in `TransferList.kt` the `locale` is still used by `dayFormat`/`monthYearFormat`; keep it there). Leave `currency/view/AmountInputState.kt:246` unchanged (it only parses/formats input, colors are irrelevant).

- [ ] **Step 6: BasicText → Text (scripted)**

Run in Git Bash from the repo root:

```bash
cd app/src/main/java/ua/com/radiokot/money
for f in $(grep -rl 'BasicText(' --include=*.kt .); do
  sed -i 's/\bBasicText(/Text(/g' "$f"
  sed -i '/^import androidx\.compose\.foundation\.text\.BasicText$/d' "$f"
  if ! grep -q '^import com\.composeunstyled\.Text$' "$f"; then
    sed -i '0,/^import /s//import com.composeunstyled.Text\nimport /' "$f"
  fi
done
grep -rn 'BasicText(' --include=*.kt . ; echo "remaining BasicText: $?"
cd -
```

Expected: the final grep prints nothing and `remaining BasicText: 1`. `BasicTextField(` is not touched (the pattern needs `(` right after `BasicText`).

- [ ] **Step 7: Hardcoded colors in this task's files**

Apply exactly these replacements. Where a literal sits inside `remember { }` (a non-composable lambda), read `val colors = MoneyTheme.colors` at the top of the composable and use `colors.x` inside, or drop the `remember` for a plain `val` as shown. Add `import ua.com.radiokot.money.uikit.theme.MoneyTheme` to each touched file and remove `import androidx.compose.ui.graphics.Color` if it becomes unused.

`categories/view/CategoriesScreen.kt` — inside `derivedStateOf`:

```kotlin
    val amountFormat = rememberViewAmountFormat()
    val incomeColor = MoneyTheme.colors.income
    val expenseColor = MoneyTheme.colors.expense
    val totalAmountText: AnnotatedString by remember(incomeColor, expenseColor) {
        derivedStateOf {
            if (totalAmount.value != null)
                amountFormat(
                    amount = totalAmount.value!!,
                    customColor =
                        if (isIncome.value)
                            incomeColor
                        else
                            expenseColor
                )
            else
                AnnotatedString("")
        }
    }
```

`transfers/view/TransferList.kt` — `TransferItem`:

```kotlin
    val colors = MoneyTheme.colors
    val amountColor = remember(item.type, colors) {
        when (item.type) {
            ViewTransferListItem.Transfer.Type.Income ->
                colors.income

            ViewTransferListItem.Transfer.Type.Expense ->
                colors.expense

            ViewTransferListItem.Transfer.Type.Other ->
                colors.neutralAmount
        }
    }
```

and in the memo `TextStyle` replace `color = Color.Gray,` with `color = MoneyTheme.colors.onBackgroundSecondary,`.

`uikit/TextButton.kt`:

```kotlin
    val color =
        if (isEnabled)
            MoneyTheme.colors.outline
        else
            MoneyTheme.colors.outlineDisabled
```

(replacing the `remember(isEnabled) { … Color.DarkGray … Color.LightGray }` block).

`uikit/RedToggleSwitch.kt`:

```kotlin
    val borderColor =
        if (isEnabled)
            MoneyTheme.colors.outline
        else
            MoneyTheme.colors.outlineDisabled
```

`categories/view/CategoryGrid.kt:291` (`AddItem` border): `color = Color.DarkGray,` → `color = MoneyTheme.colors.outline,`.

`colors/view/ItemLogo.kt` — leave its explicit `color = Color(colorScheme.onPrimary)` (rewritten in Task 5).

- [ ] **Step 8: Build and run tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. In dark mode Accounts, Categories and Activity text is light; amounts use the dark income/expense tones. Light mode looks as before.

- [ ] **Step 9: Commit**

```bash
git add -A app/src/main/java/ua/com/radiokot/money app/src/test/java/ua/com/radiokot/money/currency
git commit -m "Theme amounts, text and uikit controls"
```

---

### Task 4: Tokens, part B — surfaces, sheets, inputs and keypads

**Files:** (line numbers are from the current `main`)
- Modify: `home/view/HomeActivity.kt:387,518,555`
- Modify: `accounts/view/AccountsScreen.kt:301,362,409`, `accounts/view/AccountActionSheet.kt:115`, `accounts/view/AccountTypeSelectionSheet.kt:58,116,118,176`, `accounts/view/EditAccountScreen.kt:173,175,189,214,216,229,315`
- Modify: `auth/view/PhraseAuthScreen.kt:145`
- Modify: `categories/view/CategoryActionSheet.kt:104`, `categories/view/EditCategoryScreen.kt:201,203,217,258,288,386`, `categories/view/EditSubcategoryScreen.kt:155`
- Modify: `currency/view/AmountKeyboard.kt:71,359`
- Modify: `lock/view/PasscodeInput.kt:113,119,238,272`
- Modify: `preferences/view/PreferencesScreen.kt:79,83,121`
- Modify: `transfers/view/TransferCounterpartySelectionSheet.kt:48`, `transfers/view/TransferSheet.kt:143,242,412`
- Modify (BasicTextField text color + cursor): `accounts/view/EditAccountScreen.kt:302`, `auth/view/PhraseAuthScreen.kt:123`, `categories/view/EditCategoryScreen.kt:373`, `categories/view/EditSubcategoryScreen.kt:142`, `preferences/view/PreferencesScreen.kt:108`, `transfers/view/TransferSheet.kt:424`

**Interfaces:**
- Consumes: `MoneyTheme.colors` (Task 1).

- [ ] **Step 1: Replace literals with tokens**

Mapping (apply literally; same `remember`-hoisting rule as Task 3 Step 7; add the `MoneyTheme` import):

| Literal | Token |
|---|---|
| `Color(0xfff0edf1)` (HomeActivity bottom bar) | `MoneyTheme.colors.bottomBar` |
| `Color(0xFFD8CCE1)` (HomeActivity indicator) | `MoneyTheme.colors.bottomBarIndicator` |
| `Color.Red` (HomeActivity notice dot, inside `drawWithContent`) | read `val noticeColor = MoneyTheme.colors.notice` before `.run {`, use `noticeColor` |
| `Color(0xBE000000)` (AccountsScreen `tooltipColor` remember) | `val tooltipColor = MoneyTheme.colors.tooltip` (no remember) |
| `customColor = Color.White` (AccountsScreen tooltip amount) | `customColor = MoneyTheme.colors.onTooltip` |
| `.background(Color.Gray)` (AccountsScreen divider) | `.background(MoneyTheme.colors.divider)` |
| `Color(0xFFF9FBE7)` (AccountActionSheet, CategoryActionSheet) | `MoneyTheme.colors.actionSheet` |
| `.background(Color.White)` (AccountTypeSelectionSheet, TransferCounterpartySelectionSheet, TransferSheet ×2) | `.background(MoneyTheme.colors.surface)` |
| `Color(0xfff8efb3)` / `Color(0xfff8fafd)` (AccountTypeSelectionSheet) | `MoneyTheme.colors.selection` / `MoneyTheme.colors.selectionIdle` |
| `Color.Gray` used as text/hint color (AccountTypeSelectionSheet:176, EditAccountScreen:175,189,216,229, EditCategoryScreen:203,217,258,288, TransferSheet:412) | `MoneyTheme.colors.onBackgroundSecondary` |
| `Color.DarkGray` used as border (EditAccountScreen:173,214,315, PhraseAuthScreen:145, EditCategoryScreen:201,386, EditSubcategoryScreen:155, PreferencesScreen:121, AmountKeyboard:359, PasscodeInput:238) | `MoneyTheme.colors.outline` |
| `Color.DarkGray` passcode filled dot (PasscodeInput:113) | `MoneyTheme.colors.onBackground` |
| `Color.LightGray` passcode empty dot (PasscodeInput:119) | `MoneyTheme.colors.outlineDisabled` |
| `Color(0xfff3f0f6)` (AmountKeyboard:71 `actionBackground`, PasscodeInput:272) | `MoneyTheme.colors.surfaceVariant` |
| `color = Color.White` / `Color(0xFFfc9a47)` (PreferencesScreen sync notice) | `MoneyTheme.colors.onWarning` / `MoneyTheme.colors.warning` |

Leave untouched: `Color.Unspecified`, `Color.Transparent`, the `Color.Black` gradient mask in `AnimatedAmountInputText.kt:87` (it is an alpha mask with `BlendMode.SrcIn`), `Color.Black.copy(alpha = 0.1f)` in `uikit/ScaleIndication.kt`, and every `Color(colorScheme.primary/onPrimary)` (item colors).

- [ ] **Step 2: Text fields**

For each `BasicTextField(` listed under **Files**, make the text and cursor follow the theme. If the call has `textStyle = TextStyle(...)`, add `color = MoneyTheme.colors.onBackground,` inside it; if it has no `textStyle`, add `textStyle = TextStyle(color = MoneyTheme.colors.onBackground),`. Add `cursorBrush = SolidColor(MoneyTheme.colors.onBackground),` to every call. Imports: `androidx.compose.ui.graphics.SolidColor`, `androidx.compose.ui.text.TextStyle`.

Example (`preferences/view/PreferencesScreen.kt:108`):

```kotlin
    BasicTextField(
        value = primaryCurrencyCode.value,
        onValueChange = onPrimaryCurrencyCodeChanged,
        singleLine = true,
        textStyle = TextStyle(
            color = MoneyTheme.colors.onBackground,
        ),
        cursorBrush = SolidColor(MoneyTheme.colors.onBackground),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MoneyTheme.colors.outline,
            )
            .padding(12.dp)
    )
```

- [ ] **Step 3: Verify no stray literals remain**

Run:

```bash
grep -rn "Color(0x\|Color\.\(Red\|White\|Black\|DarkGray\|Gray\|LightGray\)" --include=*.kt app/src/main/java/ua/com/radiokot/money \
  | grep -v -e HardcodedItemColorSchemeRepository -e uikit/theme/MoneyColors.kt -e AnimatedAmountInputText.kt -e ScaleIndication.kt -e "ViewAmountFormat.kt"
```

Expected: no output.

- [ ] **Step 4: Build and run tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual: in dark mode open each screen (accounts, edit account, archived, categories, edit category/subcategory, activity, transfer sheet, counterparty selection, passcode setup/unlock, preferences): no white panels, readable text, visible field cursors.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/ua/com/radiokot/money
git commit -m "Theme surfaces, sheets, inputs and keypads"
```

---

### Task 5: Dark-mode avatars

**Files:**
- Create: `…/money/colors/data/ItemColorSchemeAccents.kt`
- Modify: `…/money/colors/view/ItemLogo.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/colors/data/ItemColorSchemeAccentsTest.kt`

**Interfaces:**
- Produces: `object ItemColorSchemeAccents { fun accent(scheme: ItemColorScheme, isDark: Boolean, schemesByName: Map<String, ItemColorScheme> = default): Long; fun darkLogoColors(scheme: ItemColorScheme, schemesByName = default): DarkItemLogoColors; fun blend(top: Long, bottom: Long, alpha: Float): Long; const val DARK_LOGO_SURFACE: Long }`, `data class DarkItemLogoColors(val background: Long, val foreground: Long)`.
- Produces: `@Composable fun itemAccentColor(scheme: ItemColorScheme): Color` (in `ItemLogo.kt`) — used by the ring, charts and the transfer sheet.

The saturated color of a scheme is its family's level-4 entry (`Red1…Red6` → `Red4` = `0xFFBF3D3F`). Black is special: `Black4` (`0xFF797979`) in light, `Black2` (`0xFFD6D5D5`) in dark, so it stays visible on a dark background.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/colors/data/ItemColorSchemeAccentsTest.kt`:

```kotlin
package ua.com.radiokot.money.colors.data

import org.junit.Assert
import org.junit.Test

class ItemColorSchemeAccentsTest {

    private val schemesByName = HardcodedItemColorSchemeRepository().getItemColorSchemesByName()

    @Test
    fun accent_IsFamilyLevel4() {
        listOf("Red1", "Red3", "Red6").forEach { name ->
            Assert.assertEquals(
                0xFFBF3D3F,
                ItemColorSchemeAccents.accent(schemesByName.getValue(name), isDark = false, schemesByName),
            )
        }
        Assert.assertEquals(
            0xFF4F63B3,
            ItemColorSchemeAccents.accent(schemesByName.getValue("Blue2"), isDark = true, schemesByName),
        )
    }

    @Test
    fun accent_BlackDependsOnTheme() {
        val black1 = schemesByName.getValue("Black1")
        Assert.assertEquals(0xFF797979, ItemColorSchemeAccents.accent(black1, isDark = false, schemesByName))
        Assert.assertEquals(0xFFD6D5D5, ItemColorSchemeAccents.accent(black1, isDark = true, schemesByName))
    }

    @Test
    fun accent_UnknownFamilyFallsBackToOwnPrimary() {
        val custom = ItemColorScheme(name = "Custom7", primary = 0xFF112233, onPrimary = 0xFFFFFFFF)
        Assert.assertEquals(0xFF112233, ItemColorSchemeAccents.accent(custom, isDark = true, schemesByName))
    }

    @Test
    fun blend() {
        Assert.assertEquals(0xFF808080, ItemColorSchemeAccents.blend(0xFFFFFFFF, 0xFF000000, 0.5f))
        Assert.assertEquals(0xFFFFFFFF, ItemColorSchemeAccents.blend(0xFFFFFFFF, 0xFF000000, 1f))
        Assert.assertEquals(0xFF000000, ItemColorSchemeAccents.blend(0xFFFFFFFF, 0xFF000000, 0f))
    }

    @Test
    fun darkLogoColors_ForegroundIsAccent_BackgroundIsDarkTint() {
        schemesByName.values.forEach { scheme ->
            val colors = ItemColorSchemeAccents.darkLogoColors(scheme, schemesByName)
            Assert.assertEquals(
                ItemColorSchemeAccents.accent(scheme, isDark = true, schemesByName),
                colors.foreground,
            )
            Assert.assertNotEquals(scheme.name, colors.foreground, colors.background)
            Assert.assertTrue(
                "${scheme.name} background must be dark",
                luminance(colors.background) < 0.3,
            )
            Assert.assertTrue(
                "${scheme.name} foreground must be lighter than background",
                luminance(colors.foreground) > luminance(colors.background),
            )
        }
    }

    private fun luminance(argb: Long): Double {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.colors.data.ItemColorSchemeAccentsTest'`
Expected: FAIL, `Unresolved reference: ItemColorSchemeAccents`.

- [ ] **Step 3: Implement**

`…/money/colors/data/ItemColorSchemeAccents.kt`:

```kotlin
package ua.com.radiokot.money.colors.data

import kotlin.math.roundToInt

data class DarkItemLogoColors(
    val background: Long,
    val foreground: Long,
)

/**
 * Saturated colors derived from the pastel [ItemColorScheme]s:
 * the accent of a scheme is its family's level-4 entry (e.g. Red2 → Red4).
 */
object ItemColorSchemeAccents {

    /**
     * The surface the dark avatar tint is blended over.
     * Matches DarkMoneyColors.surface.
     */
    const val DARK_LOGO_SURFACE: Long = 0xFF1E1E1E
    private const val DARK_LOGO_TINT_ALPHA = 0.24f

    private val defaultSchemesByName: Map<String, ItemColorScheme> by lazy {
        HardcodedItemColorSchemeRepository().getItemColorSchemesByName()
    }

    fun accent(
        scheme: ItemColorScheme,
        isDark: Boolean,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): Long {
        val family = scheme.name.trimEnd(Char::isDigit)
        val accentName =
            if (family == "Black")
                if (isDark) "Black2" else "Black4"
            else
                family + "4"

        return schemesByName[accentName]?.primary
            ?: scheme.primary
    }

    fun darkLogoColors(
        scheme: ItemColorScheme,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): DarkItemLogoColors {
        val accent = accent(scheme, isDark = true, schemesByName)

        return DarkItemLogoColors(
            background = blend(
                top = accent,
                bottom = DARK_LOGO_SURFACE,
                alpha = DARK_LOGO_TINT_ALPHA,
            ),
            foreground = accent,
        )
    }

    /**
     * @return opaque ARGB of [top] drawn with [alpha] over opaque [bottom].
     */
    fun blend(top: Long, bottom: Long, alpha: Float): Long {
        fun channel(shift: Int): Long {
            val t = (top shr shift) and 0xFF
            val b = (bottom shr shift) and 0xFF
            return (t * alpha + b * (1 - alpha)).roundToInt().toLong() and 0xFF
        }

        return 0xFF000000 or
                (channel(16) shl 16) or
                (channel(8) shl 8) or
                channel(0)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.colors.data.ItemColorSchemeAccentsTest'`
Expected: PASS (5 tests). If `darkLogoColors_…` fails for a family whose level-4 is too dark (luminance check), lighten only that family by changing `accent()` to pick level 3 for it and add the case to `accent_IsFamilyLevel4`; do not weaken the test.

- [ ] **Step 5: Use it in ItemLogo**

Replace the body of `ItemLogo` in `…/money/colors/view/ItemLogo.kt` (keep the signature and the preview):

```kotlin
@Composable
fun ItemLogo(
    modifier: Modifier = Modifier,
    title: String,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val isDark = MoneyTheme.colors.isDark
    val (backgroundColor, foregroundColor) = remember(colorScheme, isDark) {
        if (isDark) {
            val darkColors = ItemColorSchemeAccents.darkLogoColors(colorScheme)
            Color(darkColors.background) to Color(darkColors.foreground)
        } else {
            Color(colorScheme.primary) to Color(colorScheme.onPrimary)
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                color = backgroundColor,
                // Dark avatars are always circles.
                shape =
                    if (isDark)
                        CircleShape
                    else
                        shape,
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon.resId),
                contentDescription = "icon",
                tint = foregroundColor,
                modifier = Modifier
                    .size((maxWidth * 0.5f))
            )
            return@BoxWithConstraints
        }

        // Fallback to letter (grapheme).
        val fontSizeSp = (maxWidth * 0.5f).value.sp / LocalDensity.current.fontScale

        val firstGrapheme = remember(title) {

            val firstGraphemeEndIndex =
                BreakIterator.getCharacterInstance().run {
                    setText(title)
                    next()
                }

            if (firstGraphemeEndIndex > 0)
                title.substring(0, firstGraphemeEndIndex)
            else
                "…"
        }

        Text(
            text = firstGrapheme,
            style = TextStyle(
                color = foregroundColor,
                fontSize = fontSizeSp,
            )
        )
    }
}

/**
 * The saturated color of the item for charts, borders and accents.
 */
@Composable
fun itemAccentColor(colorScheme: ItemColorScheme): Color {
    val isDark = MoneyTheme.colors.isDark
    return remember(colorScheme, isDark) {
        Color(ItemColorSchemeAccents.accent(colorScheme, isDark))
    }
}
```

Imports to add: `androidx.compose.foundation.shape.CircleShape`, `ua.com.radiokot.money.colors.data.ItemColorSchemeAccents`, `ua.com.radiokot.money.uikit.theme.MoneyTheme` (`Text` was imported by Task 3).

- [ ] **Step 6: Build, test, check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual (dark): account and category avatars are circles with a dark tinted background and saturated icon/letter; the icon picker shows the same style.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/colors app/src/test/java/ua/com/radiokot/money/colors
git commit -m "Dark-mode avatars with saturated category colors"
```

---

### Task 6: Tabler icon import script, license and generated drawables

**Files:**
- Create: `tools/icons/import_tabler.py`, `tools/icons/README.md`, `tools/icons/.gitignore`
- Generated by the script (committed): `tools/icons/LICENSE-tabler-icons.txt`, `tools/icons/generated_item_icon_names.txt`, `app/src/main/res-packs/drawable/<group>_<name>_itemicon.xml` (≈190 files), `app/src/main/res/drawable/ic_tabler_<name>.xml` (13 files)
- Modify: `README.md` (attribution line)

**Interfaces:**
- Produces: item icon names `<group>_<name>` (as stored in `money.categories.icon`), e.g. `meal_shopping_cart`, `digital_brand_aws`; the full list is written to `tools/icons/generated_item_icon_names.txt`.
- Produces: UI drawables `R.drawable.ic_tabler_wallet`, `ic_tabler_chart_donut`, `ic_tabler_list_details`, `ic_tabler_chart_bar`, `ic_tabler_user_circle`, `ic_tabler_chevron_left`, `ic_tabler_chevron_right`, `ic_tabler_calendar`, `ic_tabler_backspace`, `ic_tabler_pencil`, `ic_tabler_trash`, `ic_tabler_check`, `ic_tabler_arrows_exchange`.

New groups deliberately do not reuse the existing pack prefixes (`finances`, `food`, `hobbies`, `other`, `services`, `socializing`, `transport`), so the line icons appear as their own sections in `ItemLogoIconPicker` instead of being mixed with the filled ones.

- [ ] **Step 1: Write the script with a self-test**

`tools/icons/import_tabler.py`:

```python
#!/usr/bin/env python3
"""
Imports Tabler Icons (MIT, https://github.com/tabler/tabler-icons) outline SVGs
as Android VectorDrawables. Stdlib only.

    python tools/icons/import_tabler.py --self-test   # converter unit test, no network
    python tools/icons/import_tabler.py --check       # verify every chosen name exists in the release
    python tools/icons/import_tabler.py               # download (cached), convert, write drawables

Item icons:  app/src/main/res-packs/drawable/<group>_<name>_itemicon.xml
             (picked up by DrawableResItemIconRepository, group = text before the first "_")
UI icons:    app/src/main/res/drawable/ic_tabler_<name>.xml
Re-running removes previously generated files (they carry GENERATED_MARKER) first,
the original icon packs are never touched.
"""

import argparse
import io
import re
import sys
import tarfile
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import quoteattr

TABLER_VERSION = "3.31.0"
TARBALL_URL = f"https://registry.npmjs.org/@tabler/icons/-/icons-{TABLER_VERSION}.tgz"
STROKE_WIDTH = "1.75"

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
CACHE_DIR = HERE / ".cache"
ITEM_ICONS_DIR = REPO_ROOT / "app" / "src" / "main" / "res-packs" / "drawable"
UI_ICONS_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "drawable"
LICENSE_OUT = HERE / "LICENSE-tabler-icons.txt"
NAMES_OUT = HERE / "generated_item_icon_names.txt"
GENERATED_MARKER = "Generated by tools/icons/import_tabler.py"

EXISTING_PACK_GROUPS = {"finances", "food", "hobbies", "other", "services", "socializing", "transport"}

# group -> Tabler outline icon names. Edit freely, then re-run the script.
ITEM_ICON_GROUPS = {
    "meal": [
        "shopping-cart", "basket", "apple", "carrot", "bread", "cheese", "egg", "fish", "meat",
        "milk", "pizza", "burger", "salad", "soup", "coffee", "cup", "beer", "bottle",
        "glass-full", "tools-kitchen-2", "chef-hat", "ice-cream", "cookie", "candy", "cake",
        "paper-bag",
    ],
    "vehicle": [
        "car", "bus", "train", "plane", "taxi", "motorbike", "scooter", "scooter-electric", "bike",
        "gas-station", "charging-pile", "parking", "steering-wheel", "car-garage", "road", "ship",
    ],
    "travel": ["luggage", "map-pin", "beach", "tent", "world", "mountain"],
    "home": [
        "home", "building", "sofa", "bed", "armchair", "lamp", "bulb", "plug", "bolt", "droplet",
        "flame", "wifi", "router", "key", "tool", "hammer", "paint", "wash-machine", "fridge",
        "plant", "trash", "truck-delivery", "door",
    ],
    "health": [
        "heart", "heartbeat", "first-aid-kit", "pill", "vaccine", "stethoscope", "dental", "eye",
        "barbell", "run", "yoga", "swimming",
    ],
    "care": ["scissors", "razor", "bath", "massage"],
    "shop": [
        "shopping-bag", "shirt", "shoe", "hanger", "eyeglass", "diamond", "gift", "device-mobile",
        "device-laptop", "device-desktop", "device-tv", "headphones", "camera", "device-watch",
        "tag", "discount", "building-store", "package",
    ],
    "digital": [
        "brand-youtube", "brand-spotify", "brand-netflix", "brand-aws", "brand-azure",
        "brand-google", "brand-apple", "brand-steam", "brand-amazon", "brand-github",
        "brand-openai", "brand-telegram", "cloud", "server", "world-www",
    ],
    "leisure": [
        "device-gamepad-2", "movie", "ticket", "music", "microphone", "book", "palette",
        "ball-football", "ball-basketball", "puzzle", "dice", "confetti", "balloon", "cards",
        "chess", "masks-theater", "smoking",
    ],
    "money": [
        "cash", "coin", "coins", "credit-card", "wallet", "pig-money", "building-bank", "receipt",
        "receipt-tax", "receipt-refund", "report-money", "chart-line", "currency-bitcoin",
        "currency-dollar", "currency-euro", "moneybag", "cash-banknote", "arrows-exchange",
        "percentage", "scale",
    ],
    "people": [
        "baby-carriage", "paw", "dog", "cat", "user", "users", "friends", "heart-handshake",
        "school", "backpack",
    ],
    "work": ["briefcase", "building-factory", "presentation", "plane-departure", "id-badge"],
    "misc": [
        "dots", "question-mark", "star", "flag", "bell", "calendar", "clock", "lock", "shield",
        "umbrella", "recycle", "phone", "mail", "box", "sun", "leaf",
    ],
}

UI_ICONS = [
    "wallet", "chart-donut", "list-details", "chart-bar", "user-circle", "chevron-left",
    "chevron-right", "calendar", "backspace", "pencil", "trash", "check", "arrows-exchange",
]

SVG_NS = "{http://www.w3.org/2000/svg}"
RES_NAME = re.compile(r"^[a-z][a-z0-9_]*$")


def fmt(x: float) -> str:
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("", "-0") else s


def num(attrs: dict, key: str) -> float:
    return float(attrs.get(key, "0") or "0")


def element_to_path_data(tag: str, a: dict) -> str:
    if tag == "path":
        return a["d"]
    if tag == "circle":
        cx, cy, r = num(a, "cx"), num(a, "cy"), num(a, "r")
        return (f"M{fmt(cx - r)},{fmt(cy)}"
                f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0"
                f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0")
    if tag == "ellipse":
        cx, cy, rx, ry = num(a, "cx"), num(a, "cy"), num(a, "rx"), num(a, "ry")
        return (f"M{fmt(cx - rx)},{fmt(cy)}"
                f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0"
                f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0")
    if tag == "line":
        return f"M{fmt(num(a, 'x1'))},{fmt(num(a, 'y1'))}L{fmt(num(a, 'x2'))},{fmt(num(a, 'y2'))}"
    if tag in ("polyline", "polygon"):
        values = [float(v) for v in re.split(r"[\s,]+", a["points"].strip()) if v]
        pairs = [f"{fmt(values[i])},{fmt(values[i + 1])}" for i in range(0, len(values) - 1, 2)]
        return "M" + "L".join(pairs) + ("Z" if tag == "polygon" else "")
    if tag == "rect":
        x, y, w, h = num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height")
        rx = num(a, "rx") if "rx" in a else num(a, "ry")
        ry = num(a, "ry") if "ry" in a else rx
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx == 0 and ry == 0:
            return f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}Z"
        return (f"M{fmt(x + rx)},{fmt(y)}h{fmt(w - 2 * rx)}"
                f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(ry)}v{fmt(h - 2 * ry)}"
                f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(ry)}h{fmt(-(w - 2 * rx))}"
                f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(-ry)}v{fmt(-(h - 2 * ry))}"
                f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(-ry)}Z")
    raise ValueError(f"Unsupported SVG element <{tag}>")


def svg_to_vector_drawable(svg_text: str, comment: str) -> str:
    root = ET.fromstring(svg_text)
    root_stroke = root.attrib.get("stroke", "currentColor")
    path_xml = []
    for el in root.iter():
        tag = el.tag.replace(SVG_NS, "")
        if tag in ("svg", "title", "desc", "defs"):
            continue
        if tag == "g":
            if "transform" in el.attrib:
                raise ValueError("<g transform> is not supported")
            continue
        stroke = el.attrib.get("stroke", root_stroke)
        fill = el.attrib.get("fill", "none")
        is_stroked = stroke != "none"
        is_filled = fill != "none"
        if not is_stroked and not is_filled:
            # Tabler's invisible 24x24 bounding box.
            continue
        attrs = [f"android:pathData={quoteattr(element_to_path_data(tag, el.attrib))}",
                 f'android:fillColor="{"#FF000000" if is_filled else "#00000000"}"']
        if is_stroked:
            attrs += ['android:strokeColor="#FF000000"',
                      f'android:strokeWidth="{STROKE_WIDTH}"',
                      'android:strokeLineCap="round"',
                      'android:strokeLineJoin="round"']
        path_xml.append("    <path\n        " + "\n        ".join(attrs) + " />")
    if not path_xml:
        raise ValueError("No drawable elements")
    return ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            f"<!-- {GENERATED_MARKER}. {comment} -->\n"
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            "    android:width=\"24dp\"\n"
            "    android:height=\"24dp\"\n"
            "    android:viewportWidth=\"24\"\n"
            "    android:viewportHeight=\"24\">\n"
            + "\n".join(path_xml) + "\n</vector>\n")


def self_test() -> None:
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" '
           'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'
           '<path stroke="none" d="M0 0h24v24H0z" fill="none"/>'
           '<path d="M3 12h18" /><circle cx="12" cy="12" r="2" /><line x1="1" y1="2" x2="3" y2="4" />'
           '<rect x="2" y="3" width="10" height="6" rx="1" /><polyline points="1 1 2 2 3 1" />'
           '<path d="M5 5h.01" fill="currentColor" stroke="none" /></svg>')
    out = svg_to_vector_drawable(svg, "test")
    assert out.count("<path") == 6, out
    assert 'android:pathData="M3 12h18"' in out, out
    assert 'android:pathData="M10,12a2,2 0 1,0 4,0a2,2 0 1,0 -4,0"' in out, out
    assert 'android:pathData="M1,2L3,4"' in out, out
    assert 'android:pathData="M3,3h8a1,1 0 0,1 1,1v4a1,1 0 0,1 -1,1h-8a1,1 0 0,1 -1,-1v-4a1,1 0 0,1 1,-1Z"' in out, out
    assert 'android:pathData="M1,1L2,2L3,1"' in out, out
    assert out.count('android:fillColor="#FF000000"') == 1, out
    assert out.count('android:strokeColor="#FF000000"') == 5, out
    assert 'M0 0h24v24H0z' not in out, out
    ET.fromstring(out.split("\n", 2)[2])  # well-formed without the prolog/comment
    print("self-test OK")


def res_name(group: str, icon: str) -> str:
    name = f"{group}_{icon.replace('-', '_')}"
    if not RES_NAME.match(name):
        raise ValueError(f"Invalid resource name {name}")
    return name


def load_tarball() -> tarfile.TarFile:
    CACHE_DIR.mkdir(exist_ok=True)
    cached = CACHE_DIR / f"tabler-icons-{TABLER_VERSION}.tgz"
    if not cached.exists():
        print(f"Downloading {TARBALL_URL}")
        with urllib.request.urlopen(TARBALL_URL) as response:
            cached.write_bytes(response.read())
    return tarfile.open(fileobj=io.BytesIO(cached.read_bytes()), mode="r:gz")


def outline_svgs(tar: tarfile.TarFile) -> dict:
    result = {}
    for member in tar.getmembers():
        match = re.search(r"/icons/outline/([a-z0-9-]+)\.svg$", member.name)
        if match:
            result[match.group(1)] = member
    if not result:
        raise SystemExit("No icons/outline/*.svg in the tarball; the package layout changed")
    return result


def remove_generated(directory: Path, pattern: str) -> int:
    removed = 0
    for path in directory.glob(pattern):
        if GENERATED_MARKER in path.read_text(encoding="utf-8", errors="ignore"):
            path.unlink()
            removed += 1
    return removed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return

    clashing = EXISTING_PACK_GROUPS & ITEM_ICON_GROUPS.keys()
    if clashing:
        raise SystemExit(f"Groups clash with existing packs: {clashing}")

    tar = load_tarball()
    svgs = outline_svgs(tar)
    wanted = {icon for icons in ITEM_ICON_GROUPS.values() for icon in icons} | set(UI_ICONS)
    missing = sorted(wanted - svgs.keys())
    if missing:
        raise SystemExit("Not in Tabler " + TABLER_VERSION + ", pick similar names at "
                         "https://tabler.io/icons: " + ", ".join(missing))
    if args.check:
        print(f"All {len(wanted)} icons exist in Tabler {TABLER_VERSION}")
        return

    removed = remove_generated(ITEM_ICONS_DIR, "*_itemicon.xml") + remove_generated(UI_ICONS_DIR, "ic_tabler_*.xml")

    def read_svg(icon: str) -> str:
        return tar.extractfile(svgs[icon]).read().decode("utf-8")

    item_names = []
    for group, icons in ITEM_ICON_GROUPS.items():
        for icon in icons:
            name = res_name(group, icon)
            comment = f'Tabler Icons {TABLER_VERSION} "{icon}", MIT, see tools/icons/LICENSE-tabler-icons.txt'
            (ITEM_ICONS_DIR / f"{name}_itemicon.xml").write_text(
                svg_to_vector_drawable(read_svg(icon), comment), encoding="utf-8")
            item_names.append(name)
    for icon in UI_ICONS:
        name = "ic_tabler_" + icon.replace("-", "_")
        comment = f'Tabler Icons {TABLER_VERSION} "{icon}", MIT, see tools/icons/LICENSE-tabler-icons.txt'
        (UI_ICONS_DIR / f"{name}.xml").write_text(
            svg_to_vector_drawable(read_svg(icon), comment), encoding="utf-8")

    license_member = next(m for m in tar.getmembers() if m.name.endswith("/LICENSE"))
    LICENSE_OUT.write_bytes(tar.extractfile(license_member).read())
    NAMES_OUT.write_text("\n".join(item_names) + "\n", encoding="utf-8")
    print(f"Removed {removed} old files, wrote {len(item_names)} item icons and {len(UI_ICONS)} UI icons")


if __name__ == "__main__":
    sys.exit(main())
```

`tools/icons/.gitignore`:

```
.cache/
```

- [ ] **Step 2: Run the self-test**

Run: `python tools/icons/import_tabler.py --self-test`
Expected: `self-test OK`. (If it fails, fix the converter before touching the network.)

- [ ] **Step 3: Check names against the pinned release**

Run: `python tools/icons/import_tabler.py --check`
Expected: `All 197 icons exist in Tabler 3.31.0` (item + UI names, deduplicated: `wallet`, `calendar`, `trash`, `arrows-exchange` are in both lists). If it reports missing names, replace each with the closest existing name from https://tabler.io/icons (keep the group), and if a name used by Task 8's SQL changes, update the SQL mapping too. If the tarball layout changed (`No icons/outline/*.svg`), bump `TABLER_VERSION` to the latest 3.x on https://www.npmjs.com/package/@tabler/icons and re-run.

- [ ] **Step 4: Generate**

Run: `python tools/icons/import_tabler.py`
Expected: `Removed 0 old files, wrote 188 item icons and 13 UI icons`; `tools/icons/LICENSE-tabler-icons.txt` contains "MIT License" and "Paweł Kuna".

- [ ] **Step 5: Attribution**

`tools/icons/README.md`:

```markdown
# Icons

Line icons in `app/src/main/res-packs/drawable/*_itemicon.xml` (groups meal, vehicle, travel, home,
health, care, shop, digital, leisure, money, people, work, misc) and `app/src/main/res/drawable/ic_tabler_*.xml`
are [Tabler Icons](https://github.com/tabler/tabler-icons) by Paweł Kuna, MIT License
(see `LICENSE-tabler-icons.txt`), converted to Android VectorDrawables by `import_tabler.py`.
Brand icons depict trademarks of their owners and are used only to label the user's own categories.

Regenerate after editing the lists in the script:

    python tools/icons/import_tabler.py --self-test
    python tools/icons/import_tabler.py --check
    python tools/icons/import_tabler.py
```

Append to the repository `README.md` (end of file):

```markdown

## Third-party assets

Line icons are [Tabler Icons](https://github.com/tabler/tabler-icons) (MIT), see `tools/icons/README.md`.
```

- [ ] **Step 6: Build**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (AAPT validates every generated vector). Manual: Edit category → icon picker shows the new line-icon sections; icons render tinted in both themes.

- [ ] **Step 7: Commit**

```bash
git add tools/icons README.md app/src/main/res-packs/drawable app/src/main/res/drawable
git commit -m "Import Tabler line icons as item and UI vector drawables"
```

---

### Task 7: Bottom bar line icons

**Files:**
- Modify: `…/money/home/view/HomeActivity.kt` (`BottomNavigation`, `BottomNavigationEntry`)

**Interfaces:**
- Consumes: `R.drawable.ic_tabler_wallet`, `ic_tabler_chart_donut`, `ic_tabler_list_details`, `ic_tabler_user_circle` (Task 6), `MoneyTheme.colors` (Task 1).
- Produces: `BottomNavigationEntry(modifier, text: String, @DrawableRes icon: Int, isCurrent: Boolean, hasNotice: Boolean = false)` (Task 15 reuses it).

- [ ] **Step 1: Change the entry to draw a drawable**

In `BottomNavigationEntry` change the parameter `icon: String` to `@DrawableRes icon: Int` and replace the `Text(text = icon, …)` call (keep its `.run { … notice dot … }` modifier chain) with:

```kotlin
        Icon(
            painter = painterResource(icon),
            contentDescription = text,
            tint = MoneyTheme.colors.onBackground,
            modifier = Modifier
                .padding(
                    vertical = 4.dp,
                )
                .size(22.dp)
                .run {
                    if (!hasNotice) {
                        return@run this
                    }

                    val noticeCircleRadiusPx: Float
                    val noticeCircleOffset: Offset
                    with(LocalDensity.current) {
                        noticeCircleRadiusPx = 4.dp.toPx()
                        noticeCircleOffset = Offset(
                            x = 12.dp.toPx(),
                            y = (-10).dp.toPx(),
                        )
                    }
                    val noticeColor = MoneyTheme.colors.notice

                    then(Modifier.drawWithContent {
                        drawContent()
                        drawCircle(
                            color = noticeColor,
                            radius = noticeCircleRadiusPx,
                            center = center + noticeCircleOffset
                        )
                    })
                }
        )
```

Imports: `androidx.annotation.DrawableRes`, `androidx.compose.foundation.layout.size`, `androidx.compose.ui.res.painterResource`, `com.composeunstyled.Icon`, `ua.com.radiokot.money.R`.

- [ ] **Step 2: Pass drawables**

In `BottomNavigation` replace `icon = "👛"` → `icon = R.drawable.ic_tabler_wallet`, `"📊"` → `R.drawable.ic_tabler_chart_donut`, `"📜"` → `R.drawable.ic_tabler_list_details`, `"⚙️"` → `R.drawable.ic_tabler_user_circle`.

- [ ] **Step 3: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL; the bar shows four line icons, the current tab keeps the pill indicator, the red dot shows on More when there are sync errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/home/view/HomeActivity.kt
git commit -m "Use line icons in the bottom bar"
```

---

### Task 8: One-off SQL to assign icons to imported categories

**Files:**
- Create: `tools/icons/assign_category_icons.sql`

**Interfaces:**
- Consumes: item icon names from Task 6 (`tools/icons/generated_item_icon_names.txt`); table `money.categories(id, user_id, title, parent_category_id, icon)` from `supabase/migrations/20261001000000_money_schema.sql`.

The client resolves `categories.icon` via `iconsByName[name]` (`DbSchema.toCategory`), unknown names become "no icon", so a wrong name is harmless. PowerSync streams the server update to the device. Note: editing a category in the app rewrites its subcategories' `icon` to the parent's (`PowerSyncCategoryRepository.updateSubcategories`), and the app only displays the parent's icon, so subcategory rows matter only for the database.

- [ ] **Step 1: Write the script**

`tools/icons/assign_category_icons.sql`:

```text
(Script content removed from the public repo: it lists personal category titles.
 The applied version is kept locally in tools/icons/local/assign_category_icons.sql, gitignored.)
```

- [ ] **Step 2: Verify every icon name exists**

Run (Git Bash):

```bash
grep -o "'[a-z]*_[a-z0-9_]*')" tools/icons/assign_category_icons.sql | tr -d "')" | sort -u > /tmp/sql_icons.txt
comm -23 /tmp/sql_icons.txt <(sort -u tools/icons/generated_item_icon_names.txt)
```

Expected: no output (every name used by the SQL was generated).

- [ ] **Step 3: Dry run, then apply**

Preview: copy the script to the scratch dir with the last line `commit;` replaced by `rollback;` and run it: `psql "$DATABASE_URL" -v user_id="'<uuid>'" -f <copy>.sql`. Expected: `UPDATE <n>` with n > 0 and a report listing only categories that have no mapping. Then run the real script (same command with `tools/icons/assign_category_icons.sql`). On the phone, after sync, categories show the icons. Extend the `values` list and re-run for any reported title you want covered.

- [ ] **Step 4: Commit**

```bash
git add tools/icons/assign_category_icons.sql
git commit -m "Add one-off SQL assigning icons to imported categories"
```

---

## E2 — Categories with a ring

### Task 9: Per-category amounts in the primary currency

The ring shares and Overview bars must be in one currency. Extract the existing day-price conversion (with the previous-day fallback) from `GetCategoriesWithAmountsAndTotalUseCase` into a pure function and also expose a per-category primary amount.

**Files:**
- Create: `…/money/currency/logic/DailyAmountConversion.kt`
- Modify: `…/money/categories/data/CategoryWithAmount.kt`
- Modify: `…/money/categories/logic/GetCategoriesWithAmountsAndTotalUseCase.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/currency/logic/DailyAmountConversionTest.kt`

**Interfaces:**
- Produces: `fun convertDailyAmount(dayString: String, amount: BigInteger, base: Currency, quote: Currency, dailyPrices: Map<String, CurrencyPairMap>): BigInteger?` (null when no price for the day nor the previous day; same currency code returns `amount` without prices).
- Produces: `data class CategoryWithAmount(val category: Category, val amount: BigInteger, val amountInPrimaryCurrency: BigInteger? = null)` — destructuring `(category, amount)` keeps working.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/currency/logic/DailyAmountConversionTest.kt`:

```kotlin
package ua.com.radiokot.money.currency.logic

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.currency.data.CurrencyPairMap
import java.math.BigDecimal
import java.math.BigInteger

class DailyAmountConversionTest {

    private val usd = Currency(code = "USD", symbol = "$", precision = 2)
    private val eur = Currency(code = "EUR", symbol = "€", precision = 2)
    private val uah = Currency(code = "UAH", symbol = "₴", precision = 2)

    private val prices = mapOf(
        "2026-09-01" to CurrencyPairMap(
            quoteCode = "USD",
            decimalPriceByBaseCode = mapOf(
                "EUR" to BigDecimal("1.10"),
                "UAH" to BigDecimal("0.025"),
            ),
        ),
    )

    @Test
    fun sameCurrency_NoPricesNeeded() {
        Assert.assertEquals(
            BigInteger("1234"),
            convertDailyAmount("2026-09-05", BigInteger("1234"), eur, eur, emptyMap()),
        )
    }

    @Test
    fun convertsWithTheDayPrice() {
        // 10.00 EUR → 11.00 USD
        Assert.assertEquals(
            BigInteger("1100"),
            convertDailyAmount("2026-09-01", BigInteger("1000"), eur, usd, prices),
        )
        // 400.00 UAH → 10.00 USD
        Assert.assertEquals(
            BigInteger("1000"),
            convertDailyAmount("2026-09-01", BigInteger("40000"), uah, usd, prices),
        )
    }

    @Test
    fun fallsBackToThePreviousDay() {
        Assert.assertEquals(
            BigInteger("1100"),
            convertDailyAmount("2026-09-02", BigInteger("1000"), eur, usd, prices),
        )
    }

    @Test
    fun nullWhenNoPrice() {
        Assert.assertNull(
            convertDailyAmount("2026-09-10", BigInteger("1000"), eur, usd, prices),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.currency.logic.DailyAmountConversionTest'`
Expected: FAIL, `Unresolved reference: convertDailyAmount`.

- [ ] **Step 3: Implement**

`…/money/currency/logic/DailyAmountConversion.kt`:

```kotlin
package ua.com.radiokot.money.currency.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.currency.data.CurrencyPairMap
import java.math.BigInteger

/**
 * Converts the [amount] of [base] transferred on [dayString] (YYYY-MM-DD)
 * to [quote] using [dailyPrices] of that day. If there's no price for this day,
 * which could happen due to time zone differences, the previous day is used.
 *
 * @return the converted amount or null if there's no price.
 */
fun convertDailyAmount(
    dayString: String,
    amount: BigInteger,
    base: Currency,
    quote: Currency,
    dailyPrices: Map<String, CurrencyPairMap>,
): BigInteger? {
    if (base.code == quote.code) {
        return amount
    }

    val pricesForTheDay: CurrencyPairMap =
        dailyPrices[dayString]
            ?: dailyPrices[
                LocalDate
                    .parse(dayString, LocalDate.Formats.ISO)
                    .minus(1, DateTimeUnit.DAY)
                    .toString()
            ]
            ?: return null

    return pricesForTheDay
        .get(
            base = base,
            quote = quote,
        )
        ?.baseToQuote(amount)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.currency.logic.DailyAmountConversionTest'`
Expected: PASS (4 tests). If `convertsWithTheDayPrice` is off by rounding, check `CurrencyPair.baseToQuote` rounding (do not change it); adjust the expected value only if `baseToQuote` truncates by design, and note it in the test.

- [ ] **Step 5: Use it in GetCategoriesWithAmountsAndTotalUseCase**

In `CategoryWithAmount.kt`:

```kotlin
data class CategoryWithAmount(
    val category: Category,
    val amount: BigInteger,
    /**
     * Null if the primary currency doesn't exist.
     */
    val amountInPrimaryCurrency: BigInteger? = null,
)
```

In `GetCategoriesWithAmountsAndTotalUseCase.invoke`, replace the whole `categories.forEach { category -> … }` block with:

```kotlin
            categories.forEach { category ->

                val categoryDailyAmounts: Collection<Pair<String, BigInteger>> =
                    dailyAmountsByCategoryId[category.id]
                        ?.entries
                        ?.map { it.key to it.value }
                        ?: emptySet()

                val amountInPrimaryCurrency: BigInteger? =
                    if (primaryCurrency != null)
                        categoryDailyAmounts.fold(BigInteger.ZERO) { sum, (dayString, amount) ->
                            sum + (convertDailyAmount(
                                dayString = dayString,
                                amount = amount,
                                base = category.currency,
                                quote = primaryCurrency,
                                dailyPrices = dailyPrices,
                            ) ?: BigInteger.ZERO)
                        }
                    else
                        null

                categoriesWithTotal += CategoryWithAmount(
                    category = category,
                    amount = categoryDailyAmounts.sumOf { it.second },
                    amountInPrimaryCurrency = amountInPrimaryCurrency,
                )

                if (amountInPrimaryCurrency != null) {
                    totalInPrimaryCurrency += amountInPrimaryCurrency
                }
            }
```

Add `import ua.com.radiokot.money.currency.logic.convertDailyAmount`; remove the now-unused `DateTimeUnit`, `LocalDate`, `minus` imports.

- [ ] **Step 6: Build and run all tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; Categories total unchanged for single-currency data.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/currency/logic app/src/main/java/ua/com/radiokot/money/categories app/src/test/java/ua/com/radiokot/money/currency/logic
git commit -m "Extract daily amount conversion and expose per-category primary amounts"
```

---

### Task 10: Donut ring component

**Files:**
- Create: `…/money/uikit/chart/DonutSegments.kt`, `…/money/uikit/chart/DonutRing.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/uikit/chart/DonutSegmentsTest.kt`

**Interfaces:**
- Produces: `data class DonutSegment<K>(val key: K, val startAngle: Float, val sweepAngle: Float)`; `fun <K> computeDonutSegments(values: List<Pair<K, BigInteger>>, gapAngle: Float = 2f, startAngle: Float = -90f): List<DonutSegment<K>>` — non-positive values are dropped, order kept, a single segment is a full 360° circle without a gap, angles in degrees clockwise from 3 o'clock (Compose `drawArc` convention).
- Produces: `@Composable fun DonutRing(modifier, segments: List<DonutSegment<Color>>, trackColor: Color, strokeWidth: Dp = 14.dp, animationKey: Any? = segments, content: @Composable BoxScope.() -> Unit = {})` — segments grow from 0 to their sweep when `animationKey` changes (E4 "ring segments animate").

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/uikit/chart/DonutSegmentsTest.kt`:

```kotlin
package ua.com.radiokot.money.uikit.chart

import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class DonutSegmentsTest {

    private fun v(key: String, value: Long) = key to BigInteger.valueOf(value)

    @Test
    fun empty_And_NonPositive() {
        Assert.assertEquals(emptyList<DonutSegment<String>>(), computeDonutSegments<String>(emptyList()))
        Assert.assertEquals(
            emptyList<DonutSegment<String>>(),
            computeDonutSegments(listOf(v("a", 0), v("b", -5))),
        )
    }

    @Test
    fun single_IsFullCircle() {
        Assert.assertEquals(
            listOf(DonutSegment("x", -90f, 360f)),
            computeDonutSegments(listOf(v("a", 0), v("x", 42))),
        )
    }

    @Test
    fun shares_WithoutGap() {
        val segments = computeDonutSegments(
            listOf(v("a", 1), v("b", 1), v("c", 2)),
            gapAngle = 0f,
        )
        Assert.assertEquals(
            listOf(
                DonutSegment("a", -90f, 90f),
                DonutSegment("b", 0f, 90f),
                DonutSegment("c", 90f, 180f),
            ),
            segments,
        )
    }

    @Test
    fun shares_WithGap() {
        val segments = computeDonutSegments(listOf(v("a", 5), v("b", 5)), gapAngle = 2f)
        Assert.assertEquals(
            listOf(
                DonutSegment("a", -89f, 178f),
                DonutSegment("b", 91f, 178f),
            ),
            segments,
        )
    }

    @Test
    fun sweepsPlusGapsCoverTheCircle() {
        val segments = computeDonutSegments(
            listOf(v("a", 333), v("b", 120), v("c", 547)),
            gapAngle = 2f,
        )
        Assert.assertEquals(360f, segments.sumOf { (it.sweepAngle + 2f).toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun tinyShare_NeverNegative() {
        val segments = computeDonutSegments(listOf(v("a", 1), v("b", 100_000)), gapAngle = 2f)
        Assert.assertEquals(0f, segments[0].sweepAngle, 0f)
        Assert.assertTrue(segments.all { it.sweepAngle >= 0f })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.uikit.chart.DonutSegmentsTest'`
Expected: FAIL, `Unresolved reference: computeDonutSegments`.

- [ ] **Step 3: Implement the geometry**

`…/money/uikit/chart/DonutSegments.kt`:

```kotlin
package ua.com.radiokot.money.uikit.chart

import java.math.BigInteger
import java.math.MathContext

data class DonutSegment<K>(
    val key: K,
    val startAngle: Float,
    val sweepAngle: Float,
)

/**
 * Splits a circle into segments proportional to positive [values], in their order.
 * Each segment is shortened by [gapAngle] (half on each side) to separate it from neighbours;
 * a single segment is a full circle.
 */
fun <K> computeDonutSegments(
    values: List<Pair<K, BigInteger>>,
    gapAngle: Float = 2f,
    startAngle: Float = -90f,
): List<DonutSegment<K>> {
    val positive = values.filter { it.second.signum() > 0 }
    if (positive.isEmpty()) {
        return emptyList()
    }
    if (positive.size == 1) {
        return listOf(DonutSegment(positive.first().first, startAngle, 360f))
    }

    val total = positive
        .fold(BigInteger.ZERO) { sum, (_, value) -> sum + value }
        .toBigDecimal()
    var cursor = startAngle

    return positive.map { (key, value) ->
        val share = value
            .toBigDecimal()
            .divide(total, MathContext.DECIMAL64)
            .toFloat()
        val fullSweep = 360f * share
        val segment = DonutSegment(
            key = key,
            startAngle = cursor + gapAngle / 2,
            sweepAngle = (fullSweep - gapAngle).coerceAtLeast(0f),
        )
        cursor += fullSweep
        segment
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.uikit.chart.DonutSegmentsTest'`
Expected: PASS (6 tests). (`sweepsPlusGapsCoverTheCircle` holds when no share is below the gap; `tinyShare_NeverNegative` documents the clamp.)

- [ ] **Step 5: Implement the ring**

`…/money/uikit/chart/DonutRing.kt`:

```kotlin
package ua.com.radiokot.money.uikit.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigInteger

@Composable
fun DonutRing(
    modifier: Modifier = Modifier,
    segments: List<DonutSegment<Color>>,
    trackColor: Color,
    strokeWidth: Dp = 14.dp,
    animationKey: Any? = segments,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawBehind {
                val strokePx = strokeWidth.toPx()
                val diameter = size.minDimension - strokePx
                val topLeft = Offset(
                    x = (size.width - diameter) / 2,
                    y = (size.height - diameter) / 2,
                )
                val arcSize = Size(diameter, diameter)
                val stroke = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Butt,
                )

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )

                val currentProgress = progress.value
                segments.forEach { segment ->
                    drawArc(
                        color = segment.key,
                        startAngle = segment.startAngle,
                        sweepAngle = segment.sweepAngle * currentProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
            },
        content = content,
    )
}

@Preview
@Composable
private fun DonutRingPreview() = DonutRing(
    segments = computeDonutSegments(
        listOf(
            Color(0xFFBF3D3F) to BigInteger.valueOf(50),
            Color(0xFF4F63B3) to BigInteger.valueOf(30),
            Color(0xFF3FA56F) to BigInteger.valueOf(20),
        )
    ),
    trackColor = Color(0xFFBDBDBD),
    animationKey = Unit,
    modifier = Modifier.size(160.dp),
)
```

- [ ] **Step 6: Build**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; the preview shows three colored arcs.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/uikit/chart app/src/test/java/ua/com/radiokot/money/uikit/chart
git commit -m "Add animated donut ring chart"
```

---

### Task 11: Categories ring layout and screen

**Files:**
- Create: `…/money/categories/view/CategoryRingLayout.kt`, `…/money/categories/view/CategoryRingGrid.kt`
- Modify: `…/money/categories/view/CategoryGrid.kt` (make `CategoryListItem`, `AddItem`, `ArchiveHeader` `internal`)
- Modify: `…/money/categories/view/CategoriesScreenViewModel.kt`
- Modify: `…/money/categories/view/CategoriesScreen.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/categories/view/CategoryRingLayoutTest.kt`

**Interfaces:**
- Consumes: `CategoryWithAmount.amountInPrimaryCurrency` (Task 9), `computeDonutSegments`, `DonutRing` (Task 10), `ItemColorSchemeAccents.accent` (Task 5), `rememberViewAmountFormat` (Task 3).
- Produces: `data class CategoryRingLayout<T>(val topRow: List<T>, val ringLeft: List<T>, val ringRight: List<T>, val rows: List<List<T>>)`, `fun <T> layoutAroundRing(items: List<T>): CategoryRingLayout<T>` (4 columns).
- Produces: `CategoriesScreenViewModel.expenseTotalAmount: StateFlow<ViewAmount?>`, `incomeTotalAmount: StateFlow<ViewAmount?>`, `ringSegments: StateFlow<List<DonutSegment<ItemColorScheme>>>`; `CategoriesScreenViewModel.totalAmount` is removed.

Layout (4 columns): row 1 = items 1–4; rows 2–3 = item 5 | ring | item 6, item 7 | ring | item 8; then rows of 4. The "add" cell is the last item, so it flows into the layout like a category. Archived categories stay under the "Archive" header below.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/categories/view/CategoryRingLayoutTest.kt`:

```kotlin
package ua.com.radiokot.money.categories.view

import org.junit.Assert
import org.junit.Test

class CategoryRingLayoutTest {

    private fun items(count: Int) = (1..count).toList()

    @Test
    fun empty() {
        Assert.assertEquals(
            CategoryRingLayout<Int>(emptyList(), emptyList(), emptyList(), emptyList()),
            layoutAroundRing(items(0)),
        )
    }

    @Test
    fun onlyTopRow() {
        Assert.assertEquals(
            CategoryRingLayout(listOf(1, 2, 3), emptyList(), emptyList(), emptyList()),
            layoutAroundRing(items(3)),
        )
    }

    @Test
    fun partialRing() {
        Assert.assertEquals(
            CategoryRingLayout(listOf(1, 2, 3, 4), listOf(5, 7), listOf(6), emptyList()),
            layoutAroundRing(items(7)),
        )
    }

    @Test
    fun fullRing_ThenRows() {
        Assert.assertEquals(
            CategoryRingLayout(
                topRow = listOf(1, 2, 3, 4),
                ringLeft = listOf(5, 7),
                ringRight = listOf(6, 8),
                rows = listOf(listOf(9, 10, 11, 12), listOf(13)),
            ),
            layoutAroundRing(items(13)),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.categories.view.CategoryRingLayoutTest'`
Expected: FAIL, `Unresolved reference: layoutAroundRing`.

- [ ] **Step 3: Implement the layout**

`…/money/categories/view/CategoryRingLayout.kt`:

```kotlin
package ua.com.radiokot.money.categories.view

const val CATEGORY_RING_GRID_COLUMNS = 4

data class CategoryRingLayout<T>(
    val topRow: List<T>,
    /** Two cells (one per ring row) left of the 2×2 ring. */
    val ringLeft: List<T>,
    /** Two cells (one per ring row) right of the 2×2 ring. */
    val ringRight: List<T>,
    val rows: List<List<T>>,
)

/**
 * Places [items] in 4 columns: one regular row, then two rows with one item
 * on each side of a 2×2 ring (left, right, left, right), then regular rows.
 */
fun <T> layoutAroundRing(items: List<T>): CategoryRingLayout<T> {
    val columns = CATEGORY_RING_GRID_COLUMNS
    val ringItems = items.drop(columns).take(4)

    return CategoryRingLayout(
        topRow = items.take(columns),
        ringLeft = ringItems.filterIndexed { index, _ -> index % 2 == 0 },
        ringRight = ringItems.filterIndexed { index, _ -> index % 2 == 1 },
        rows = items.drop(columns + 4).chunked(columns),
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.categories.view.CategoryRingLayoutTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Grid composable**

In `CategoryGrid.kt` change `private fun ArchiveHeader`, `private fun CategoryListItem`, `private fun AddItem` to `internal fun …` (no other change; `CategoryGrid` stays for the counterparty selection sheet).

`…/money/categories/view/CategoryRingGrid.kt`:

```kotlin
package ua.com.radiokot.money.categories.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemColorSchemeAccents
import ua.com.radiokot.money.uikit.chart.DonutRing
import ua.com.radiokot.money.uikit.chart.DonutSegment
import ua.com.radiokot.money.uikit.theme.MoneyTheme

private sealed interface RingGridCell {
    val key: Any

    class Category(val item: ViewCategoryListItem) : RingGridCell {
        override val key: Any get() = item.key
    }

    data object Add : RingGridCell {
        override val key: Any = "add"
    }
}

@Composable
fun CategoryRingGrid(
    modifier: Modifier = Modifier,
    itemList: State<List<ViewCategoryListItem>>,
    ringSegments: State<List<DonutSegment<ItemColorScheme>>>,
    onItemClicked: (ViewCategoryListItem) -> Unit,
    onItemLongClicked: (ViewCategoryListItem) -> Unit,
    onAddClicked: () -> Unit,
    onRingClicked: () -> Unit,
    ringCenter: @Composable BoxScope.() -> Unit,
) {
    val spaceBy = 6.dp
    val layout = remember {
        derivedStateOf {
            layoutAroundRing(
                itemList.value
                    .fastFilter(ViewCategoryListItem::isNotArchived)
                    .map<ViewCategoryListItem, RingGridCell>(RingGridCell::Category)
                        + RingGridCell.Add
            )
        }
    }
    val archivedRows = remember {
        derivedStateOf {
            itemList.value
                .fastFilter(ViewCategoryListItem::isArchived)
                .map(RingGridCell::Category)
                .chunked(CATEGORY_RING_GRID_COLUMNS)
        }
    }
    val isArchiveExpanded = remember { mutableStateOf(false) }
    val isDark = MoneyTheme.colors.isDark
    val coloredSegments = remember(ringSegments.value, isDark) {
        ringSegments.value.map { segment ->
            DonutSegment(
                key = Color(ItemColorSchemeAccents.accent(segment.key, isDark)),
                startAngle = segment.startAngle,
                sweepAngle = segment.sweepAngle,
            )
        }
    }

    @Composable
    fun Cell(cell: RingGridCell, cellModifier: Modifier) = when (cell) {
        is RingGridCell.Category ->
            CategoryListItem(
                item = cell.item,
                modifier = cellModifier
                    .combinedClickable(
                        onClick = { onItemClicked(cell.item) },
                        onLongClick = { onItemLongClicked(cell.item) },
                    )
            )

        RingGridCell.Add ->
            AddItem(
                modifier = cellModifier
                    .clickable(onClick = onAddClicked)
            )
    }

    @Composable
    fun CellRow(cells: List<RingGridCell>) = Row(
        horizontalArrangement = Arrangement.spacedBy(spaceBy),
        modifier = Modifier.fillMaxWidth(),
    ) {
        cells.forEach { cell ->
            Box(modifier = Modifier.weight(1f)) {
                Cell(cell, Modifier.fillMaxWidth())
            }
        }
        repeat(CATEGORY_RING_GRID_COLUMNS - cells.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(spaceBy),
        verticalArrangement = Arrangement.spacedBy(spaceBy),
        modifier = modifier,
    ) {
        item(key = "top") {
            CellRow(layout.value.topRow)
        }

        item(key = "ring") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spaceBy),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spaceBy),
                    modifier = Modifier.weight(1f),
                ) {
                    layout.value.ringLeft.forEach { Cell(it, Modifier.fillMaxWidth()) }
                }

                DonutRing(
                    segments = coloredSegments,
                    trackColor = MoneyTheme.colors.chartOther,
                    content = ringCenter,
                    modifier = Modifier
                        .weight(2f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onRingClicked)
                        .padding(4.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(spaceBy),
                    modifier = Modifier.weight(1f),
                ) {
                    layout.value.ringRight.forEach { Cell(it, Modifier.fillMaxWidth()) }
                }
            }
        }

        items(
            count = layout.value.rows.size,
            key = { index -> "row-$index" },
        ) { index ->
            CellRow(layout.value.rows[index])
        }

        if (archivedRows.value.isNotEmpty()) {
            item(key = "archive") {
                ArchiveHeader(isArchiveExpanded = isArchiveExpanded)
            }

            if (isArchiveExpanded.value) {
                items(
                    count = archivedRows.value.size,
                    key = { index -> "archive-row-$index" },
                ) { index ->
                    CellRow(archivedRows.value[index])
                }
            }
        }
    }
}
```

- [ ] **Step 6: ViewModel — both modes, totals, segments**

In `CategoriesScreenViewModel` replace the `categoriesWithAmountAndTotalSharedFlow` property and the `totalAmount` property with:

```kotlin
    /**
     * Expense (first) and income (second) data for the current period,
     * so the ring center can show both totals and switching the mode is instant.
     */
    private val bothModesSharedFlow: SharedFlow<Pair<CategoriesWithAmountAndTotal, CategoriesWithAmountAndTotal>> =
        historyStatsPeriod
            .flatMapLatest { period ->
                combine(
                    getCategoriesWithAmountAndTotalUseCase(
                        isIncome = false,
                        period = period,
                    ),
                    getCategoriesWithAmountAndTotalUseCase(
                        isIncome = true,
                        period = period,
                    ),
                    transform = ::Pair,
                )
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val categoriesWithAmountAndTotalSharedFlow: SharedFlow<CategoriesWithAmountAndTotal> =
        combine(
            bothModesSharedFlow,
            isIncome,
        ) { (expense, income), isIncome ->
            if (isIncome)
                income
            else
                expense
        }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val expenseTotalAmount: StateFlow<ViewAmount?> =
        bothModesSharedFlow
            .map { it.first.totalInPrimaryCurrency?.let(::ViewAmount) }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val incomeTotalAmount: StateFlow<ViewAmount?> =
        bothModesSharedFlow
            .map { it.second.totalInPrimaryCurrency?.let(::ViewAmount) }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val ringSegments: StateFlow<List<DonutSegment<ItemColorScheme>>> =
        categoriesWithAmountAndTotalSharedFlow
            .map { data ->
                computeDonutSegments(
                    data.categories
                        .filterNot { it.category.isArchived }
                        .sortedBy(CategoryWithAmount::category)
                        .map { it.category.colorScheme to (it.amountInPrimaryCurrency ?: BigInteger.ZERO) }
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

Keep `categoryItemList` as is (it reads `categoriesWithAmountAndTotalSharedFlow`). Imports: `ua.com.radiokot.money.categories.data.CategoryWithAmount`, `ua.com.radiokot.money.colors.data.ItemColorScheme`, `ua.com.radiokot.money.uikit.chart.DonutSegment`, `ua.com.radiokot.money.uikit.chart.computeDonutSegments`, `java.math.BigInteger`.

- [ ] **Step 7: Screen**

Replace `CategoriesScreenRoot` and `CategoriesScreen` in `CategoriesScreen.kt` with:

```kotlin
@Composable
fun CategoriesScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: CategoriesScreenViewModel,
) = CategoriesScreen(
    isIncome = viewModel.isIncome.collectAsState(),
    period = viewModel.viewHistoryStatsPeriod.collectAsState(),
    expenseTotal = viewModel.expenseTotalAmount.collectAsState(),
    incomeTotal = viewModel.incomeTotalAmount.collectAsState(),
    ringSegments = viewModel.ringSegments.collectAsState(),
    categoryItemList = viewModel.categoryItemList.collectAsState(),
    onTitleClicked = remember { viewModel::onTitleClicked },
    onCategoryItemClicked = remember { viewModel::onCategoryItemClicked },
    onCategoryItemLongClicked = remember { viewModel::onCategoryItemLongClicked },
    onPeriodClicked = {},
    isPreviousPeriodButtonEnabled = viewModel.isPreviousHistoryStatsPeriodButtonEnabled.collectAsState(),
    onPreviousPeriodClicked = remember { viewModel::onPreviousHistoryStatsPeriodClicked },
    isNextPeriodButtonEnabled = viewModel.isNextHistoryStatsPeriodButtonEnabled.collectAsState(),
    onNextPeriodClicked = remember { viewModel::onNextHistoryStatsPeriodClicked },
    onAddClicked = remember { viewModel::onAddClicked },
    modifier = modifier,
)

@Composable
private fun CategoriesScreen(
    modifier: Modifier = Modifier,
    isIncome: State<Boolean>,
    period: State<ViewHistoryPeriod>,
    expenseTotal: State<ViewAmount?>,
    incomeTotal: State<ViewAmount?>,
    ringSegments: State<List<DonutSegment<ItemColorScheme>>>,
    categoryItemList: State<List<ViewCategoryListItem>>,
    onTitleClicked: () -> Unit,
    onCategoryItemClicked: (ViewCategoryListItem) -> Unit,
    onCategoryItemLongClicked: (ViewCategoryListItem) -> Unit,
    onPeriodClicked: () -> Unit,
    isNextPeriodButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    isPreviousPeriodButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
    onAddClicked: () -> Unit,
) = Column(
    modifier = modifier
        .padding(
            vertical = 16.dp,
        )
) {
    PeriodBar(
        period = period,
        onPeriodClicked = onPeriodClicked,
        isNextButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 22.dp,
            )
    )

    Spacer(modifier = Modifier.height(8.dp))

    CategoryRingGrid(
        itemList = categoryItemList,
        ringSegments = ringSegments,
        onItemClicked = onCategoryItemClicked,
        onItemLongClicked = onCategoryItemLongClicked,
        onAddClicked = onAddClicked,
        onRingClicked = onTitleClicked,
        ringCenter = {
            RingCenter(
                isIncome = isIncome,
                expenseTotal = expenseTotal,
                incomeTotal = incomeTotal,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )
}

@Composable
private fun RingCenter(
    isIncome: State<Boolean>,
    expenseTotal: State<ViewAmount?>,
    incomeTotal: State<ViewAmount?>,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    val colors = MoneyTheme.colors
    val amountFormat = rememberViewAmountFormat()

    Text(
        text =
            if (isIncome.value)
                "Income"
            else
                "Expenses",
        style = TextStyle(
            fontSize = 13.sp,
            color = colors.onBackgroundSecondary,
        ),
    )

    listOf(
        Triple(expenseTotal.value, colors.expense, !isIncome.value),
        Triple(incomeTotal.value, colors.income, isIncome.value),
    ).forEach { (amount, color, isCurrent) ->
        if (amount != null) {
            Text(
                text = amountFormat(
                    amount = amount,
                    customColor = color,
                ),
                maxLines = 1,
                style = TextStyle(
                    textAlign = TextAlign.Center,
                    fontSize = if (isCurrent) 18.sp else 13.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}
```

Imports to add: `androidx.compose.ui.Alignment`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.unit.sp`, `ua.com.radiokot.money.colors.data.ItemColorScheme`, `ua.com.radiokot.money.uikit.chart.DonutSegment`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`, `ua.com.radiokot.money.currency.view.rememberViewAmountFormat`; remove now-unused ones (`clickable`, `derivedStateOf`, `getValue`, `AnnotatedString`, `LocalConfiguration`, `Color`, `ViewAmountFormat`).

- [ ] **Step 8: Build, test, check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual: Categories shows one row of 4, then 2 categories left/right of the ring, then rows; ring segments match category colors and shares; center shows expense (pink) and income (green) totals, the current mode bigger; tap the ring → switches to income categories and segments re-animate; tap/long-tap categories and "add" work as before; archive expands.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/categories app/src/test/java/ua/com/radiokot/money/categories
git commit -m "Lay out categories around a donut ring with both totals"
```

---

## E3 — Overview tab

### Task 12: Overview aggregation (pure)

**Files:**
- Create: `…/money/overview/logic/OverviewStats.kt`, `…/money/overview/logic/OverviewStatsCalculator.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/overview/logic/OverviewStatsCalculatorTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class OverviewStats(
    val total: BigInteger,
    val days: List<OverviewDay>,          // one per day of [firstDay, lastDay], empty if > 31 days
    val stackCategoryIds: List<String>,   // top-N by total, descending
    val dayAverage: BigInteger,
    val weekAverage: BigInteger,
    val topCategories: List<OverviewCategoryShare>,
    val categoryCount: Int,               // categories with a positive total
)
data class OverviewDay(val date: LocalDate, val total: BigInteger, val segments: List<OverviewDaySegment>)
data class OverviewDaySegment(val categoryId: String?, val amount: BigInteger) // null = the rest
data class OverviewCategoryShare(val categoryId: String, val amount: BigInteger, val percent: Int)

object OverviewStatsCalculator {
    const val MAX_CHART_DAYS = 31
    fun calculate(amountsByCategoryId: Map<String, Map<LocalDate, BigInteger>>, firstDay: LocalDate, lastDay: LocalDate,
                  today: LocalDate, stackCategoryCount: Int = 4, topCategoryCount: Int = 3): OverviewStats
    fun bounds(period: HistoryPeriod, dataDays: Collection<LocalDate>, today: LocalDate): Pair<LocalDate, LocalDate>
    fun percentOf(part: BigInteger, total: BigInteger): Int
}
```

Rules: amounts are already in one currency. Day average = total ÷ elapsed days, where elapsed = days from `firstDay` to `today` inclusive while the period is running, all days for a past period, 0 for a future one (average 0). Week average = day average × 7. Percent is rounded half-up. Ties are ordered by category ID so the result is stable. Days outside the bounds are ignored. "The entire time" spans from the first day with data to today.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/overview/logic/OverviewStatsCalculatorTest.kt`:

```kotlin
package ua.com.radiokot.money.overview.logic

import kotlinx.datetime.LocalDate
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import java.math.BigInteger

class OverviewStatsCalculatorTest {

    private fun d(day: Int, month: Int = 9) = LocalDate(2026, month, day)
    private fun bi(value: Long) = BigInteger.valueOf(value)

    private val september = mapOf(
        "food" to mapOf(d(1) to bi(1000), d(2) to bi(500)),
        "car" to mapOf(d(1) to bi(3000)),
        "fun" to mapOf(d(3) to bi(200)),
        "gift" to mapOf(d(3) to bi(100)),
        "misc" to mapOf(d(2) to bi(50), d(31, month = 8) to bi(999_999)),
    )

    private fun calculateSeptember(today: LocalDate) =
        OverviewStatsCalculator.calculate(
            amountsByCategoryId = september,
            firstDay = d(1),
            lastDay = d(30),
            today = today,
            stackCategoryCount = 2,
            topCategoryCount = 3,
        )

    @Test
    fun totals_IgnoreDaysOutsideBounds() {
        val stats = calculateSeptember(today = d(10))
        Assert.assertEquals(bi(4850), stats.total)
        Assert.assertEquals(5, stats.categoryCount)
    }

    @Test
    fun days_StackedByTopCategories_RestGrouped() {
        val stats = calculateSeptember(today = d(10))

        Assert.assertEquals(listOf("car", "food"), stats.stackCategoryIds)
        Assert.assertEquals(30, stats.days.size)
        Assert.assertEquals(
            OverviewDay(d(1), bi(4000), listOf(OverviewDaySegment("car", bi(3000)), OverviewDaySegment("food", bi(1000)))),
            stats.days[0],
        )
        Assert.assertEquals(
            OverviewDay(d(2), bi(550), listOf(OverviewDaySegment("food", bi(500)), OverviewDaySegment(null, bi(50)))),
            stats.days[1],
        )
        Assert.assertEquals(
            OverviewDay(d(3), bi(300), listOf(OverviewDaySegment(null, bi(300)))),
            stats.days[2],
        )
        Assert.assertEquals(OverviewDay(d(4), bi(0), emptyList()), stats.days[3])
        Assert.assertEquals(d(30), stats.days.last().date)
    }

    @Test
    fun averages_CurrentPeriod_UseElapsedDays() {
        val stats = calculateSeptember(today = d(10))
        Assert.assertEquals(bi(485), stats.dayAverage)
        Assert.assertEquals(bi(3395), stats.weekAverage)
    }

    @Test
    fun averages_PastPeriod_UseAllDays() {
        val stats = calculateSeptember(today = d(5, month = 10))
        Assert.assertEquals(bi(161), stats.dayAverage)
        Assert.assertEquals(bi(1127), stats.weekAverage)
    }

    @Test
    fun averages_FuturePeriod_AreZero() {
        val stats = calculateSeptember(today = d(20, month = 8))
        Assert.assertEquals(bi(0), stats.dayAverage)
        Assert.assertEquals(bi(0), stats.weekAverage)
    }

    @Test
    fun topCategories_WithRoundedPercent() {
        Assert.assertEquals(
            listOf(
                OverviewCategoryShare("car", bi(3000), 62),
                OverviewCategoryShare("food", bi(1500), 31),
                OverviewCategoryShare("fun", bi(200), 4),
            ),
            calculateSeptember(today = d(10)).topCategories,
        )
    }

    @Test
    fun ties_AreOrderedById() {
        val stats = OverviewStatsCalculator.calculate(
            amountsByCategoryId = mapOf("b" to mapOf(d(1) to bi(10)), "a" to mapOf(d(1) to bi(10))),
            firstDay = d(1),
            lastDay = d(30),
            today = d(10),
        )
        Assert.assertEquals(listOf("a", "b"), stats.stackCategoryIds)
    }

    @Test
    fun empty() {
        val stats = OverviewStatsCalculator.calculate(emptyMap(), d(1), d(30), d(10))
        Assert.assertEquals(bi(0), stats.total)
        Assert.assertEquals(emptyList<OverviewCategoryShare>(), stats.topCategories)
        Assert.assertEquals(30, stats.days.size)
        Assert.assertTrue(stats.days.all { it.segments.isEmpty() })
        Assert.assertEquals(bi(0), stats.dayAverage)
    }

    @Test
    fun longRange_HasNoBars() {
        val stats = OverviewStatsCalculator.calculate(september, d(1), d(2, month = 10), d(2, month = 10))
        Assert.assertEquals(emptyList<OverviewDay>(), stats.days)
        Assert.assertEquals(bi(4850 / 32), stats.dayAverage)
    }

    @Test
    fun bounds_Month() {
        Assert.assertEquals(
            d(1) to d(30),
            OverviewStatsCalculator.bounds(HistoryPeriod.Month(d(15)), emptyList(), d(10)),
        )
    }

    @Test
    fun bounds_EntireTime_FromFirstDataDayToToday() {
        Assert.assertEquals(
            d(3) to d(2, month = 10),
            OverviewStatsCalculator.bounds(HistoryPeriod.Since70th, listOf(d(5), d(3)), d(2, month = 10)),
        )
        Assert.assertEquals(
            d(2, month = 10) to d(2, month = 10),
            OverviewStatsCalculator.bounds(HistoryPeriod.Since70th, emptyList(), d(2, month = 10)),
        )
    }

    @Test
    fun percentOf() {
        Assert.assertEquals(0, OverviewStatsCalculator.percentOf(bi(5), bi(0)))
        Assert.assertEquals(50, OverviewStatsCalculator.percentOf(bi(1), bi(2)))
        Assert.assertEquals(33, OverviewStatsCalculator.percentOf(bi(1), bi(3)))
        Assert.assertEquals(67, OverviewStatsCalculator.percentOf(bi(2), bi(3)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.overview.logic.OverviewStatsCalculatorTest'`
Expected: FAIL, `Unresolved reference: OverviewStatsCalculator`.

- [ ] **Step 3: Implement**

`…/money/overview/logic/OverviewStats.kt`:

```kotlin
package ua.com.radiokot.money.overview.logic

import kotlinx.datetime.LocalDate
import java.math.BigInteger

data class OverviewStats(
    val total: BigInteger,
    /**
     * One entry per day within the bounds, or empty if there are too many days to chart.
     */
    val days: List<OverviewDay>,
    /**
     * Categories having their own segment in [days], by total descending.
     */
    val stackCategoryIds: List<String>,
    val dayAverage: BigInteger,
    val weekAverage: BigInteger,
    val topCategories: List<OverviewCategoryShare>,
    /**
     * Number of categories with a positive total.
     */
    val categoryCount: Int,
)

data class OverviewDay(
    val date: LocalDate,
    val total: BigInteger,
    /**
     * Positive segments only, stack categories first, then the rest.
     */
    val segments: List<OverviewDaySegment>,
)

data class OverviewDaySegment(
    /**
     * Null for the rest of the categories.
     */
    val categoryId: String?,
    val amount: BigInteger,
)

data class OverviewCategoryShare(
    val categoryId: String,
    val amount: BigInteger,
    val percent: Int,
)
```

`…/money/overview/logic/OverviewStatsCalculator.kt`:

```kotlin
package ua.com.radiokot.money.overview.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import java.math.BigInteger

object OverviewStatsCalculator {

    const val MAX_CHART_DAYS = 31
    private val SEVEN = BigInteger.valueOf(7)
    private val HUNDRED = BigInteger.valueOf(100)

    // BigInteger.TWO needs API 33.
    private val TWO = BigInteger.valueOf(2)

    /**
     * @param amountsByCategoryId daily amounts in one currency by category ID
     * @param firstDay first day of the period, inclusive
     * @param lastDay last day of the period, inclusive
     */
    fun calculate(
        amountsByCategoryId: Map<String, Map<LocalDate, BigInteger>>,
        firstDay: LocalDate,
        lastDay: LocalDate,
        today: LocalDate,
        stackCategoryCount: Int = 4,
        topCategoryCount: Int = 3,
    ): OverviewStats {
        val bounds = firstDay..lastDay

        val totalsByCategoryId: Map<String, BigInteger> =
            amountsByCategoryId
                .mapValues { (_, amountsByDay) ->
                    amountsByDay.entries
                        .filter { it.key in bounds }
                        .fold(BigInteger.ZERO) { sum, entry -> sum + entry.value }
                }
                .filterValues { it.signum() > 0 }

        val total = totalsByCategoryId.values.fold(BigInteger.ZERO, BigInteger::add)

        val rankedIds: List<String> =
            totalsByCategoryId.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, BigInteger>> { it.value }
                        .thenBy { it.key }
                )
                .map { it.key }

        val stackIds = rankedIds.take(stackCategoryCount)
        val periodLength = firstDay.daysUntil(lastDay) + 1

        val days: List<OverviewDay> =
            if (periodLength in 1..MAX_CHART_DAYS)
                (0 until periodLength).map { offset ->
                    val date = firstDay.plus(offset, DateTimeUnit.DAY)
                    val dayTotal = totalsByCategoryId.keys.fold(BigInteger.ZERO) { sum, categoryId ->
                        sum + (amountsByCategoryId.getValue(categoryId)[date] ?: BigInteger.ZERO)
                    }
                    val stackSegments = stackIds.map { categoryId ->
                        OverviewDaySegment(
                            categoryId = categoryId,
                            amount = amountsByCategoryId.getValue(categoryId)[date] ?: BigInteger.ZERO,
                        )
                    }
                    val rest = stackSegments.fold(dayTotal) { left, segment -> left - segment.amount }

                    OverviewDay(
                        date = date,
                        total = dayTotal,
                        segments = (stackSegments + OverviewDaySegment(null, rest))
                            .filter { it.amount.signum() > 0 },
                    )
                }
            else
                emptyList()

        val elapsedDays = when {
            today < firstDay -> 0
            today > lastDay -> periodLength
            else -> firstDay.daysUntil(today) + 1
        }
        val dayAverage =
            if (elapsedDays > 0)
                total / BigInteger.valueOf(elapsedDays.toLong())
            else
                BigInteger.ZERO

        return OverviewStats(
            total = total,
            days = days,
            stackCategoryIds = stackIds,
            dayAverage = dayAverage,
            weekAverage = dayAverage * SEVEN,
            topCategories = rankedIds
                .take(topCategoryCount)
                .map { categoryId ->
                    val amount = totalsByCategoryId.getValue(categoryId)
                    OverviewCategoryShare(
                        categoryId = categoryId,
                        amount = amount,
                        percent = percentOf(amount, total),
                    )
                },
            categoryCount = totalsByCategoryId.size,
        )
    }

    /**
     * @return inclusive first and last days to aggregate for the [period].
     * "The entire time" spans from the first day with data to today.
     */
    fun bounds(
        period: HistoryPeriod,
        dataDays: Collection<LocalDate>,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate> = when (period) {
        HistoryPeriod.Since70th ->
            (dataDays.minOrNull() ?: today) to maxOf(today, dataDays.maxOrNull() ?: today)

        else ->
            period.startInclusive.date to period.endExclusive.date.minus(1, DateTimeUnit.DAY)
    }

    /**
     * @return [part] of [total] in percent, rounded half-up, 0 for zero total.
     */
    fun percentOf(part: BigInteger, total: BigInteger): Int =
        if (total.signum() == 0)
            0
        else
            ((part * HUNDRED + total / TWO) / total).toInt()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.overview.logic.OverviewStatsCalculatorTest'`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/overview app/src/test/java/ua/com/radiokot/money/overview
git commit -m "Add overview stats aggregation"
```

---

### Task 13: GetOverviewStatsUseCase and DI

**Files:**
- Create: `…/money/overview/logic/GetOverviewStatsUseCase.kt`, `…/money/overview/OverviewModule.kt`
- Modify: `…/money/home/HomeModule.kt` (include `overviewModule`)

**Interfaces:**
- Consumes: `convertDailyAmount` (Task 9), `OverviewStatsCalculator` (Task 12), `HistoryStatsRepository.getCategoryDailyAmountsFlow`, `CategoryRepository.getCategoriesFlow`, `CurrencyPreferences`, `CurrencyRepository`, `CurrencyPriceRepository`.
- Produces: `class OverviewData(val primaryCurrency: Currency, val period: HistoryPeriod, val expense: OverviewStats, val income: OverviewStats, val categoriesById: Map<String, Category>)`; `class GetOverviewStatsUseCase { operator fun invoke(period: HistoryPeriod): Flow<OverviewData?> }` — null when the primary currency doesn't exist; Koin `overviewModule` (Task 14 adds the ViewModel to it).

- [ ] **Step 1: Implement the use case**

`…/money/overview/logic/GetOverviewStatsUseCase.kt`:

```kotlin
package ua.com.radiokot.money.overview.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.currency.data.CurrencyPairMap
import ua.com.radiokot.money.currency.data.CurrencyPreferences
import ua.com.radiokot.money.currency.data.CurrencyPriceRepository
import ua.com.radiokot.money.currency.data.CurrencyRepository
import ua.com.radiokot.money.currency.logic.convertDailyAmount
import ua.com.radiokot.money.transfers.history.data.DailyAmountsByCategoryId
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.HistoryStatsRepository
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OverviewData(
    val primaryCurrency: Currency,
    val period: HistoryPeriod,
    val expense: OverviewStats,
    val income: OverviewStats,
    val categoriesById: Map<String, Category>,
)

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class GetOverviewStatsUseCase(
    private val currencyPreferences: CurrencyPreferences,
    private val currencyRepository: CurrencyRepository,
    private val currencyPriceRepository: CurrencyPriceRepository,
    private val categoryRepository: CategoryRepository,
    private val historyStatsRepository: HistoryStatsRepository,
) {

    /**
     * @return expense and income stats for the [period] in the primary currency,
     * or null if the primary currency doesn't exist.
     */
    operator fun invoke(
        period: HistoryPeriod,
    ): Flow<OverviewData?> =
        combine(
            currencyPreferences
                .primaryCurrencyCode
                .mapLatest(currencyRepository::getCurrencyByCode),
            categoryRepository.getCategoriesFlow(isIncome = false),
            categoryRepository.getCategoriesFlow(isIncome = true),
            historyStatsRepository.getCategoryDailyAmountsFlow(isIncome = false, period = period),
            historyStatsRepository.getCategoryDailyAmountsFlow(isIncome = true, period = period),
        ) { primaryCurrency, expenseCategories, incomeCategories, expenseDailyAmounts, incomeDailyAmounts ->
            Input(
                primaryCurrency = primaryCurrency,
                categoriesById = (expenseCategories + incomeCategories).associateBy(Category::id),
                expenseDailyAmounts = expenseDailyAmounts,
                incomeDailyAmounts = incomeDailyAmounts,
            )
        }
            .mapLatest { input ->
                val primaryCurrency = input.primaryCurrency
                    ?: return@mapLatest null

                val today = Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date

                val dailyPrices: Map<String, CurrencyPairMap> =
                    currencyPriceRepository.getDailyPrices(
                        period = period,
                        currencyCodes = input.categoriesById.values
                            .mapTo(mutableSetOf(primaryCurrency.code)) { it.currency.code },
                    )

                fun toPrimaryCurrency(
                    dailyAmounts: DailyAmountsByCategoryId,
                ): Map<String, Map<LocalDate, BigInteger>> =
                    dailyAmounts.entries
                        .mapNotNull { (categoryId, amountsByDay) ->
                            val category = input.categoriesById[categoryId]
                                ?: return@mapNotNull null

                            categoryId to amountsByDay.entries.associate { (dayString, amount) ->
                                LocalDate.parse(dayString, LocalDate.Formats.ISO) to
                                        (convertDailyAmount(
                                            dayString = dayString,
                                            amount = amount,
                                            base = category.currency,
                                            quote = primaryCurrency,
                                            dailyPrices = dailyPrices,
                                        ) ?: BigInteger.ZERO)
                            }
                        }
                        .toMap()

                val expense = toPrimaryCurrency(input.expenseDailyAmounts)
                val income = toPrimaryCurrency(input.incomeDailyAmounts)
                val (firstDay, lastDay) = OverviewStatsCalculator.bounds(
                    period = period,
                    dataDays = (expense.values + income.values).flatMap { it.keys },
                    today = today,
                )

                OverviewData(
                    primaryCurrency = primaryCurrency,
                    period = period,
                    expense = OverviewStatsCalculator.calculate(expense, firstDay, lastDay, today),
                    income = OverviewStatsCalculator.calculate(income, firstDay, lastDay, today),
                    categoriesById = input.categoriesById,
                )
            }
            .flowOn(Dispatchers.Default)

    private class Input(
        val primaryCurrency: Currency?,
        val categoriesById: Map<String, Category>,
        val expenseDailyAmounts: DailyAmountsByCategoryId,
        val incomeDailyAmounts: DailyAmountsByCategoryId,
    )
}
```

- [ ] **Step 2: Koin module**

`…/money/overview/OverviewModule.kt`:

```kotlin
package ua.com.radiokot.money.overview

import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.money.auth.logic.sessionScope
import ua.com.radiokot.money.categories.categoriesModule
import ua.com.radiokot.money.overview.logic.GetOverviewStatsUseCase

val overviewModule = module {
    includes(
        categoriesModule,
    )

    sessionScope {
        scoped {
            GetOverviewStatsUseCase(
                currencyPreferences = get(),
                currencyRepository = get(),
                currencyPriceRepository = get(),
                categoryRepository = get(),
                historyStatsRepository = get(),
            )
        } bind GetOverviewStatsUseCase::class
    }
}
```

In `HomeModule.kt` add `overviewModule` to `includes(...)` and `import ua.com.radiokot.money.overview.overviewModule`.

- [ ] **Step 3: Build and run tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/overview app/src/main/java/ua/com/radiokot/money/home/HomeModule.kt
git commit -m "Add overview stats use case"
```

---

### Task 14: Overview screen, ViewModel and stacked bar chart

**Files:**
- Create: `…/money/uikit/chart/StackedBarChart.kt`
- Create: `…/money/overview/view/ViewOverview.kt`, `…/money/overview/view/OverviewScreenViewModel.kt`, `…/money/overview/view/OverviewScreen.kt`, `…/money/overview/view/OverviewScreenNavigation.kt`
- Modify: `…/money/overview/OverviewModule.kt` (ViewModel)
- Modify: `…/money/home/view/HomeActivity.kt` (register `overviewScreen` in the `NavHost`; the tab is added in Task 15)

**Interfaces:**
- Consumes: `GetOverviewStatsUseCase`, `OverviewData` (Task 13), `HistoryStatsPeriodViewModel` (`HomeViewModel`), `ItemLogo`, `ItemColorSchemeAccents` (Task 5), `rememberViewAmountFormat` (Task 3), `PeriodBar`.
- Produces: `const val OverviewScreenRoute = "overview"`, `fun NavGraphBuilder.overviewScreen(homeViewModel: HomeViewModel, onProceedToCategories: () -> Unit)`, `OverviewScreenViewModel.state: StateFlow<OverviewScreenState>`, `@Composable fun StackedBarChart(modifier, bars: List<List<Pair<Color, Float>>>, labels: List<String?>, labelColor: Color, animationKey: Any? = bars)` — bars grow from 0 when `animationKey` changes (E4 "bars animate").

- [ ] **Step 1: Bar chart**

`…/money/uikit/chart/StackedBarChart.kt`:

```kotlin
package ua.com.radiokot.money.uikit.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @param bars per bar, segments from the bottom up as color to value
 * @param labels per bar, a label under it or null
 */
@Composable
fun StackedBarChart(
    modifier: Modifier = Modifier,
    bars: List<List<Pair<Color, Float>>>,
    labels: List<String?>,
    labelColor: Color,
    animationKey: Any? = bars,
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = labelColor,
    )

    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Canvas(modifier = modifier) {
        if (bars.isEmpty()) {
            return@Canvas
        }

        val labelAreaHeight = 16.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        val slotWidth = size.width / bars.size
        val barWidth = slotWidth * 0.6f
        val maxTotal = bars
            .maxOf { segments -> segments.sumOf { it.second.toDouble() } }
            .toFloat()
            .coerceAtLeast(Float.MIN_VALUE)
        val currentProgress = progress.value

        bars.forEachIndexed { index, segments ->
            val x = index * slotWidth + (slotWidth - barWidth) / 2
            var bottom = chartHeight

            segments.forEach { (color, value) ->
                val height = value / maxTotal * chartHeight * currentProgress
                drawRect(
                    color = color,
                    topLeft = Offset(x, bottom - height),
                    size = Size(barWidth, height),
                )
                bottom -= height
            }

            val label = labels.getOrNull(index)
            if (label != null) {
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = index * slotWidth + (slotWidth - layout.size.width) / 2,
                        y = chartHeight + (labelAreaHeight - layout.size.height) / 2,
                    ),
                )
            }
        }
    }
}

@Preview(widthDp = 320)
@Composable
private fun StackedBarChartPreview() = StackedBarChart(
    bars = (1..30).map { day ->
        listOf(
            Color(0xFFBF3D3F) to (day % 5).toFloat(),
            Color(0xFFBDBDBD) to (day % 3).toFloat(),
        )
    },
    labels = (1..30).map { day -> if (day == 1 || day % 5 == 0) day.toString() else null },
    labelColor = Color.Gray,
    animationKey = Unit,
    modifier = Modifier
        .fillMaxWidth()
        .height(160.dp),
)
```

- [ ] **Step 2: View model data**

`…/money/overview/view/ViewOverview.kt`:

```kotlin
package ua.com.radiokot.money.overview.view

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.overview.logic.OverviewData
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import java.math.BigInteger

@Immutable
sealed interface OverviewScreenState {
    data object Loading : OverviewScreenState
    data object NoPrimaryCurrency : OverviewScreenState
    class Loaded(val overview: ViewOverview) : OverviewScreenState
}

@Immutable
class ViewOverview(
    val isIncome: Boolean,
    val isMonth: Boolean,
    val balance: ViewAmount,
    val expenseTotal: ViewAmount,
    val incomeTotal: ViewAmount,
    val bars: List<ViewOverviewBar>,
    val dayAverage: ViewAmount,
    val weekAverage: ViewAmount,
    val periodTotal: ViewAmount,
    val topCategories: List<ViewOverviewTopCategory>,
    val hasMoreCategories: Boolean,
    /**
     * Changes when the chart must re-animate: period or mode.
     */
    val animationKey: Any,
) {
    companion object {
        fun fromData(
            data: OverviewData,
            isIncome: Boolean,
        ): ViewOverview {
            val currency = ViewCurrency(data.primaryCurrency)
            fun amount(value: BigInteger) = ViewAmount(value = value, currency = currency)
            val stats =
                if (isIncome)
                    data.income
                else
                    data.expense

            return ViewOverview(
                isIncome = isIncome,
                isMonth = data.period is HistoryPeriod.Month,
                balance = amount(data.income.total - data.expense.total),
                expenseTotal = amount(data.expense.total),
                incomeTotal = amount(data.income.total),
                bars = stats.days.map { day ->
                    ViewOverviewBar(
                        dayOfMonth = day.date.day,
                        segments = day.segments.map { segment ->
                            ViewOverviewBarSegment(
                                colorScheme = segment.categoryId
                                    ?.let(data.categoriesById::get)
                                    ?.colorScheme,
                                value = segment.amount.toFloat(),
                            )
                        },
                    )
                },
                dayAverage = amount(stats.dayAverage),
                weekAverage = amount(stats.weekAverage),
                periodTotal = amount(stats.total),
                topCategories = stats.topCategories.mapNotNull { share ->
                    val category = data.categoriesById[share.categoryId]
                        ?: return@mapNotNull null

                    ViewOverviewTopCategory(
                        key = category.id,
                        title = category.title,
                        colorScheme = category.colorScheme,
                        icon = category.icon,
                        amount = amount(share.amount),
                        percent = share.percent,
                    )
                },
                hasMoreCategories = stats.categoryCount > stats.topCategories.size,
                animationKey = data.period.startInclusive to isIncome,
            )
        }
    }
}

@Immutable
class ViewOverviewBar(
    val dayOfMonth: Int,
    val segments: List<ViewOverviewBarSegment>,
)

@Immutable
class ViewOverviewBarSegment(
    /**
     * Null for the rest of the categories.
     */
    val colorScheme: ItemColorScheme?,
    val value: Float,
)

@Immutable
class ViewOverviewTopCategory(
    val key: String,
    val title: String,
    val colorScheme: ItemColorScheme,
    val icon: ItemIcon?,
    val amount: ViewAmount,
    val percent: Int,
)
```

- [ ] **Step 3: ViewModel**

`…/money/overview/view/OverviewScreenViewModel.kt`:

```kotlin
package ua.com.radiokot.money.overview.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.overview.logic.GetOverviewStatsUseCase
import ua.com.radiokot.money.transfers.history.view.HistoryStatsPeriodViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewScreenViewModel(
    historyStatsPeriodViewModel: HistoryStatsPeriodViewModel,
    getOverviewStatsUseCase: GetOverviewStatsUseCase,
) : ViewModel(),
    HistoryStatsPeriodViewModel by historyStatsPeriodViewModel {

    private val log by lazyLogger("OverviewScreenVM")
    private val _isIncome: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isIncome = _isIncome.asStateFlow()
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    private val overviewDataFlow =
        historyStatsPeriod
            .flatMapLatest { period ->
                getOverviewStatsUseCase(
                    period = period,
                )
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val state: StateFlow<OverviewScreenState> =
        combine(
            overviewDataFlow,
            isIncome,
        ) { data, isIncome ->
            if (data == null)
                OverviewScreenState.NoPrimaryCurrency
            else
                OverviewScreenState.Loaded(
                    ViewOverview.fromData(
                        data = data,
                        isIncome = isIncome,
                    )
                )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, OverviewScreenState.Loading)

    fun onExpensesCardClicked() {
        log.debug { "onExpensesCardClicked(): switching to expenses" }
        _isIncome.value = false
    }

    fun onIncomeCardClicked() {
        log.debug { "onIncomeCardClicked(): switching to income" }
        _isIncome.value = true
    }

    fun onMoreCategoriesClicked() {
        _events.tryEmit(Event.ProceedToCategories)
    }

    sealed interface Event {
        object ProceedToCategories : Event
    }
}
```

In `OverviewModule.kt` add inside `sessionScope { }` (imports `org.koin.core.module.dsl.viewModel`, `ua.com.radiokot.money.overview.view.OverviewScreenViewModel`):

```kotlin
        viewModel { parameters ->
            OverviewScreenViewModel(
                historyStatsPeriodViewModel = checkNotNull(parameters.getOrNull()) {
                    "HistoryStatsPeriodViewModel must be provided through the parameters " +
                            "to share the same instance"
                },
                getOverviewStatsUseCase = get(),
            )
        } bind OverviewScreenViewModel::class
```

- [ ] **Step 4: Screen**

`…/money/overview/view/OverviewScreen.kt`:

```kotlin
package ua.com.radiokot.money.overview.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.colors.data.ItemColorSchemeAccents
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.transfers.history.view.PeriodBar
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import ua.com.radiokot.money.uikit.chart.StackedBarChart
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun OverviewScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: OverviewScreenViewModel,
) = OverviewScreen(
    state = viewModel.state.collectAsState(),
    period = viewModel.viewHistoryStatsPeriod.collectAsState(),
    isPreviousPeriodButtonEnabled = viewModel.isPreviousHistoryStatsPeriodButtonEnabled.collectAsState(),
    onPreviousPeriodClicked = remember { viewModel::onPreviousHistoryStatsPeriodClicked },
    isNextPeriodButtonEnabled = viewModel.isNextHistoryStatsPeriodButtonEnabled.collectAsState(),
    onNextPeriodClicked = remember { viewModel::onNextHistoryStatsPeriodClicked },
    onExpensesCardClicked = remember { viewModel::onExpensesCardClicked },
    onIncomeCardClicked = remember { viewModel::onIncomeCardClicked },
    onMoreCategoriesClicked = remember { viewModel::onMoreCategoriesClicked },
    modifier = modifier,
)

@Composable
private fun OverviewScreen(
    modifier: Modifier = Modifier,
    state: State<OverviewScreenState>,
    period: State<ViewHistoryPeriod>,
    isPreviousPeriodButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
    isNextPeriodButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    onExpensesCardClicked: () -> Unit,
    onIncomeCardClicked: () -> Unit,
    onMoreCategoriesClicked: () -> Unit,
) = Column(
    modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(
            horizontal = 16.dp,
            vertical = 16.dp,
        )
) {
    PeriodBar(
        period = period,
        onPeriodClicked = {},
        isNextButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    when (val currentState = state.value) {
        OverviewScreenState.Loading ->
            Unit

        OverviewScreenState.NoPrimaryCurrency ->
            Text(
                text = "Set an existing primary currency in More to see the overview",
                color = MoneyTheme.colors.onBackgroundSecondary,
            )

        is OverviewScreenState.Loaded ->
            OverviewContent(
                overview = currentState.overview,
                onExpensesCardClicked = onExpensesCardClicked,
                onIncomeCardClicked = onIncomeCardClicked,
                onMoreCategoriesClicked = onMoreCategoriesClicked,
            )
    }
}

@Composable
private fun OverviewContent(
    overview: ViewOverview,
    onExpensesCardClicked: () -> Unit,
    onIncomeCardClicked: () -> Unit,
    onMoreCategoriesClicked: () -> Unit,
) = Column {
    val colors = MoneyTheme.colors
    val amountFormat = rememberViewAmountFormat()

    Text(
        text = "Balance",
        color = colors.onBackgroundSecondary,
        fontSize = 13.sp,
    )
    Text(
        text = amountFormat(overview.balance),
        style = TextStyle(
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalCard(
            title = "Expenses",
            amount = overview.expenseTotal,
            color = colors.expense,
            isSelected = !overview.isIncome,
            onClick = onExpensesCardClicked,
            modifier = Modifier.weight(1f),
        )
        TotalCard(
            title = "Income",
            amount = overview.incomeTotal,
            color = colors.income,
            isSelected = overview.isIncome,
            onClick = onIncomeCardClicked,
            modifier = Modifier.weight(1f),
        )
    }

    if (overview.bars.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))

        val isDark = colors.isDark
        val chartOther = colors.chartOther
        val bars = remember(overview, isDark) {
            overview.bars.map { bar ->
                bar.segments.map { segment ->
                    (segment.colorScheme
                        ?.let { Color(ItemColorSchemeAccents.accent(it, isDark)) }
                        ?: chartOther) to segment.value
                }
            }
        }
        val labels = remember(overview) {
            overview.bars.map { bar ->
                bar.dayOfMonth
                    .takeIf { it == 1 || it % 5 == 0 || overview.bars.size <= 7 }
                    ?.toString()
            }
        }

        StackedBarChart(
            bars = bars,
            labels = labels,
            labelColor = colors.onBackgroundSecondary,
            animationKey = overview.animationKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatColumn("Day avg", overview.dayAverage, Modifier.weight(1f))
        StatColumn("Week avg", overview.weekAverage, Modifier.weight(1f))
        StatColumn(
            if (overview.isMonth) "Month total" else "Total",
            overview.periodTotal,
            Modifier.weight(1f),
        )
    }

    if (overview.topCategories.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))

        overview.topCategories.forEach { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                ItemLogo(
                    title = category.title,
                    colorScheme = category.colorScheme,
                    icon = category.icon,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${category.percent}%",
                    color = colors.onBackgroundSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    text = amountFormat(
                        amount = category.amount,
                        customColor = Color.Unspecified,
                    ),
                )
            }
        }

        if (overview.hasMoreCategories) {
            Text(
                text = "More…",
                color = colors.onBackgroundSecondary,
                modifier = Modifier
                    .clickable(onClick = onMoreCategoriesClicked)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TotalCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: ViewAmount,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val amountFormat = rememberViewAmountFormat()

    Column(
        modifier = modifier
            .background(MoneyTheme.colors.surfaceVariant, shape)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) color else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = MoneyTheme.colors.onBackgroundSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = amountFormat(amount = amount, customColor = color),
            maxLines = 1,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun StatColumn(
    title: String,
    amount: ViewAmount,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val amountFormat = rememberViewAmountFormat()

    Text(
        text = title,
        color = MoneyTheme.colors.onBackgroundSecondary,
        fontSize = 12.sp,
    )
    Text(
        text = amountFormat(amount = amount, customColor = Color.Unspecified),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
```

- [ ] **Step 5: Navigation**

`…/money/overview/view/OverviewScreenNavigation.kt`:

```kotlin
package ua.com.radiokot.money.overview.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.money.home.view.HomeViewModel

const val OverviewScreenRoute = "overview"

fun NavGraphBuilder.overviewScreen(
    homeViewModel: HomeViewModel,
    onProceedToCategories: () -> Unit,
) = composable(OverviewScreenRoute) {

    val viewModel = koinViewModel<OverviewScreenViewModel> {
        parametersOf(
            homeViewModel,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                OverviewScreenViewModel.Event.ProceedToCategories ->
                    onProceedToCategories()
            }
        }
    }

    OverviewScreenRoot(
        viewModel = viewModel,
        modifier = Modifier
            .fillMaxSize()
    )
}
```

In `HomeActivity.kt` `NavHost { … }`, after `activityScreen(...)`, add (imports `ua.com.radiokot.money.overview.view.overviewScreen`):

```kotlin
            overviewScreen(
                homeViewModel = viewModel,
                onProceedToCategories = {
                    navController.popBackStack()
                    navController.navigate(CategoriesScreenRoute)
                },
            )
```

- [ ] **Step 6: Build and run tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. The Overview screen is reachable after Task 15; the bar chart preview renders in Android Studio.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/overview app/src/main/java/ua/com/radiokot/money/uikit/chart/StackedBarChart.kt app/src/main/java/ua/com/radiokot/money/home/view/HomeActivity.kt
git commit -m "Add Overview screen with stacked daily bars and top categories"
```

---

### Task 15: Tabs rework — Accounts / Categories / Transactions / Overview, profile icon

**Files:**
- Modify: `…/money/home/view/HomeActivity.kt`

**Interfaces:**
- Consumes: `OverviewScreenRoute`, `overviewScreen` (Task 14), `BottomNavigationEntry` with drawable icons (Task 7), `R.drawable.ic_tabler_chart_bar`, `ic_tabler_user_circle` (Task 6).
- Produces: `private fun NavController.navigateToTab(route: String)` (used by tab clicks, Overview "More…").

- [ ] **Step 1: Tab navigation helper**

Add to `HomeActivity.kt`:

```kotlin
/**
 * Bottom tabs replace each other; the preferences screen, opened from the profile icon,
 * sits above a tab and is closed when switching tabs.
 */
private fun NavController.navigateToTab(route: String) {
    if (currentDestination?.route == PreferencesScreenRoute) {
        popBackStack()
    }
    popBackStack()
    navigate(route)
}
```

Use it in the Overview "More…" callback added in Task 14: `onProceedToCategories = { navController.navigateToTab(CategoriesScreenRoute) },`.

- [ ] **Step 2: Bottom bar entries**

Replace the body of `BottomNavigation` after the `lastVisitedBottomRoute` declaration with four entries, and drop the `hasMoreNotice` parameter:

```kotlin
    listOf(
        Triple("Accounts", R.drawable.ic_tabler_wallet, AccountsScreenRoute),
        Triple("Categories", R.drawable.ic_tabler_chart_donut, CategoriesScreenRoute),
        Triple("Transactions", R.drawable.ic_tabler_list_details, ActivityScreenRoute),
        Triple("Overview", R.drawable.ic_tabler_chart_bar, OverviewScreenRoute),
    ).forEach { (text, icon, route) ->
        BottomNavigationEntry(
            text = text,
            icon = icon,
            isCurrent = lastVisitedBottomRoute == route
                    || (LocalInspectionMode.current && route == AccountsScreenRoute),
            modifier = Modifier
                .weight(1f)
                .clickable(
                    onClick = { navController.navigateToTab(route) },
                )
        )
    }
```

Update the set:

```kotlin
private val bottomNavigationRoutes: Set<String> = setOf(
    AccountsScreenRoute,
    CategoriesScreenRoute,
    ActivityScreenRoute,
    OverviewScreenRoute,
)
```

Update the preview to `BottomNavigation(navController = rememberNavController())`.

- [ ] **Step 3: Profile icon top bar**

Add:

```kotlin
@Composable
private fun TopBar(
    hasNotice: State<Boolean>,
    onProfileClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .padding(
            horizontal = 10.dp,
            vertical = 2.dp,
        )
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onProfileClicked)
            .padding(6.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tabler_user_circle),
            contentDescription = "Profile and settings",
            tint = MoneyTheme.colors.onBackground,
            modifier = Modifier
                .size(28.dp)
        )

        if (hasNotice.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .background(
                        color = MoneyTheme.colors.notice,
                        shape = CircleShape,
                    )
            )
        }
    }
}
```

In `HomeScreen`, inside the outer `Column`, before `NavHost(…)`:

```kotlin
        TopBar(
            hasNotice = viewModel.hasMoreNotice.collectAsState(),
            onProfileClicked = {
                if (navController.currentDestination?.route == PreferencesScreenRoute) {
                    navController.navigateUp()
                } else {
                    navController.navigate(PreferencesScreenRoute) {
                        launchSingleTop = true
                    }
                }
            },
        )
```

and change the bottom bar call to `BottomNavigation(navController = navController)`.

Imports: `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.draw.clip`, `ua.com.radiokot.money.overview.view.OverviewScreenRoute`.

- [ ] **Step 4: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual:
- Bottom bar: Accounts, Categories, Transactions, Overview; the profile icon top-left opens More (settings); back or the icon again returns; switching a tab from More closes it; the red dot moves to the profile icon on sync errors.
- Overview: balance = income − expense; tapping Income/Expenses cards switches the chart and stats; bars stacked by the top 4 categories in their colors, the rest grey; day/week averages and month total look right against Categories totals for the same month; top 3 with % and "More…" → Categories tab; past month and "entire time" (if reachable) don't crash.
- Category action sheet "Activity" and account "Activity" still land on Transactions.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/home/view/HomeActivity.kt
git commit -m "Rework tabs: Transactions and Overview, settings behind the profile icon"
```

---

## E4 — Motion and gestures

Already covered by earlier tasks: the ring segments (Task 10 `DonutRing`) and the Overview bars (Task 14 `StackedBarChart`) animate from zero whenever the period or the mode changes (`animationKey`).

### Task 16: Swipe to change the period, with a slide

**Files:**
- Create: `…/money/transfers/history/view/PeriodSwipe.kt`
- Modify: `…/money/categories/view/CategoriesScreen.kt`, `…/money/transfers/history/view/ActivityScreen.kt`, `…/money/overview/view/OverviewScreen.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/transfers/history/view/PeriodSwipeTest.kt`

**Interfaces:**
- Consumes: `HistoryStatsPeriodViewModel.historyStatsPeriod`, `isNextHistoryStatsPeriodButtonEnabled`, `isPreviousHistoryStatsPeriodButtonEnabled`, `onNextHistoryStatsPeriodClicked()`, `onPreviousHistoryStatsPeriodClicked()` (already on all three ViewModels via delegation).
- Produces: `enum class PeriodSwipe { Previous, Next }`, `fun resolvePeriodSwipe(totalDragX: Float, thresholdPx: Float): PeriodSwipe?`, `fun periodChangeDirection(old: HistoryPeriod, new: HistoryPeriod): Int`, `fun Modifier.periodSwipe(isPreviousEnabled: State<Boolean>, isNextEnabled: State<Boolean>, onPrevious: () -> Unit, onNext: () -> Unit): Modifier`, `@Composable fun PeriodSlideContainer(period: HistoryPeriod, modifier: Modifier = Modifier, content: @Composable () -> Unit)`.

Direction convention (like turning pages): finger moves right → previous month, content slides in from the left; finger moves left → next month, content slides in from the right. `detectHorizontalDragGestures` only claims the pointer after horizontal touch slop, so vertical scrolling of the grid/list/column is untouched. Tabs never react to swipes (the bottom bar has no gesture).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/ua/com/radiokot/money/transfers/history/view/PeriodSwipeTest.kt`:

```kotlin
package ua.com.radiokot.money.transfers.history.view

import kotlinx.datetime.LocalDate
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod

class PeriodSwipeTest {

    @Test
    fun resolvePeriodSwipe_Thresholds() {
        Assert.assertNull(resolvePeriodSwipe(totalDragX = 0f, thresholdPx = 100f))
        Assert.assertNull(resolvePeriodSwipe(totalDragX = 99f, thresholdPx = 100f))
        Assert.assertNull(resolvePeriodSwipe(totalDragX = -99f, thresholdPx = 100f))
        Assert.assertEquals(PeriodSwipe.Previous, resolvePeriodSwipe(totalDragX = 100f, thresholdPx = 100f))
        Assert.assertEquals(PeriodSwipe.Previous, resolvePeriodSwipe(totalDragX = 400f, thresholdPx = 100f))
        Assert.assertEquals(PeriodSwipe.Next, resolvePeriodSwipe(totalDragX = -100f, thresholdPx = 100f))
    }

    @Test
    fun periodChangeDirection() {
        val september = HistoryPeriod.Month(LocalDate(2026, 9, 15))

        Assert.assertEquals(1, periodChangeDirection(september, september.getNext()))
        Assert.assertEquals(-1, periodChangeDirection(september, september.getPrevious()))
        Assert.assertEquals(0, periodChangeDirection(september, HistoryPeriod.Month(LocalDate(2026, 9, 1))))
        Assert.assertEquals(-1, periodChangeDirection(september, HistoryPeriod.Since70th))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.transfers.history.view.PeriodSwipeTest'`
Expected: FAIL, `Unresolved reference: resolvePeriodSwipe`.

- [ ] **Step 3: Implement**

`…/money/transfers/history/view/PeriodSwipe.kt`:

```kotlin
package ua.com.radiokot.money.transfers.history.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import kotlin.math.sign

enum class PeriodSwipe {
    Previous,
    Next,
    ;
}

/**
 * @param totalDragX horizontal drag distance, positive when the finger moved right
 */
fun resolvePeriodSwipe(
    totalDragX: Float,
    thresholdPx: Float,
): PeriodSwipe? = when {
    totalDragX >= thresholdPx -> PeriodSwipe.Previous
    totalDragX <= -thresholdPx -> PeriodSwipe.Next
    else -> null
}

/**
 * @return 1 if [new] starts later than [old], -1 if earlier, 0 if at the same time.
 */
fun periodChangeDirection(
    old: HistoryPeriod,
    new: HistoryPeriod,
): Int =
    new.startInclusive.compareTo(old.startInclusive).sign

private val PeriodSwipeThreshold = 72.dp

/**
 * Switches the period on a horizontal swipe. Doesn't interfere with vertical scrolling.
 */
fun Modifier.periodSwipe(
    isPreviousEnabled: State<Boolean>,
    isNextEnabled: State<Boolean>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = composed {
    val thresholdPx = with(LocalDensity.current) { PeriodSwipeThreshold.toPx() }
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)

    pointerInput(thresholdPx) {
        var totalDragX = 0f

        detectHorizontalDragGestures(
            onDragStart = { totalDragX = 0f },
            onDragCancel = { totalDragX = 0f },
            onDragEnd = {
                when (resolvePeriodSwipe(totalDragX, thresholdPx)) {
                    PeriodSwipe.Previous ->
                        if (isPreviousEnabled.value) currentOnPrevious()

                    PeriodSwipe.Next ->
                        if (isNextEnabled.value) currentOnNext()

                    null ->
                        Unit
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                change.consume()
            },
        )
    }
}

/**
 * Slides and fades the [content] in from the side of the new [period]
 * when it changes: later period from the right, earlier from the left.
 */
@Composable
fun PeriodSlideContainer(
    period: HistoryPeriod,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offsetFraction = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var shownPeriod by remember { mutableStateOf(period) }

    LaunchedEffect(period) {
        val direction = periodChangeDirection(shownPeriod, period)
        shownPeriod = period
        if (direction == 0) {
            return@LaunchedEffect
        }

        offsetFraction.snapTo(0.3f * direction)
        alpha.snapTo(0.3f)
        launch {
            offsetFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
            )
        }
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(250),
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetFraction.value * size.width
                this.alpha = alpha.value
            }
    ) {
        content()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.transfers.history.view.PeriodSwipeTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Apply to the three screens**

Each `…Root` passes a new parameter `historyPeriod = viewModel.historyStatsPeriod.collectAsState()`, and the private screen takes `historyPeriod: State<HistoryPeriod>` (import `ua.com.radiokot.money.transfers.history.data.HistoryPeriod`).

`CategoriesScreen.kt` — on the outer `Column` modifier, before `.padding(vertical = 16.dp)`:

```kotlin
        .periodSwipe(
            isPreviousEnabled = isPreviousPeriodButtonEnabled,
            isNextEnabled = isNextPeriodButtonEnabled,
            onPrevious = onPreviousPeriodClicked,
            onNext = onNextPeriodClicked,
        )
```

and wrap the grid:

```kotlin
    PeriodSlideContainer(
        period = historyPeriod.value,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        CategoryRingGrid(
            itemList = categoryItemList,
            ringSegments = ringSegments,
            onItemClicked = onCategoryItemClicked,
            onItemLongClicked = onCategoryItemLongClicked,
            onAddClicked = onAddClicked,
            onRingClicked = onTitleClicked,
            ringCenter = {
                RingCenter(
                    isIncome = isIncome,
                    expenseTotal = expenseTotal,
                    incomeTotal = incomeTotal,
                )
            },
            modifier = Modifier
                .fillMaxSize()
        )
    }
```

`ActivityScreen.kt` — same `.periodSwipe(...)` on the outer `Column` modifier, and wrap `TransferList(...)` in `PeriodSlideContainer(period = historyPeriod.value, modifier = Modifier.weight(1f)) { TransferList(…, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) }`. (This also fixes the current `modifier = modifier.padding(...)` reuse of the outer modifier on the list.)

`OverviewScreen.kt` — same `.periodSwipe(...)` on the outer `Column` modifier (before `.verticalScroll`), and wrap the `when (state.value) { … }` in `PeriodSlideContainer(period = historyPeriod.value) { … }`.

- [ ] **Step 6: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual on Categories, Transactions, Overview: swipe right → previous month, content slides in from the left; swipe left → next month (only if enabled, not into the future); short or diagonal-vertical swipes do nothing; vertical flings scroll the grid/list/overview normally; tapping categories/transfers still works; swiping on the bottom bar does nothing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/transfers/history/view app/src/main/java/ua/com/radiokot/money/categories/view/CategoriesScreen.kt app/src/main/java/ua/com/radiokot/money/overview/view/OverviewScreen.kt app/src/test/java/ua/com/radiokot/money/transfers/history/view
git commit -m "Swipe to switch the period with a slide animation"
```

---

### Task 17: Animated numbers

**Files:**
- Create: `…/money/currency/view/AnimatedAmountText.kt`
- Modify: `…/money/categories/view/CategoriesScreen.kt` (`RingCenter`), `…/money/overview/view/OverviewScreen.kt` (balance, `TotalCard`, `StatColumn`, top category amounts), `…/money/accounts/view/AccountsScreen.kt` (total, ≈ line 415)

**Interfaces:**
- Consumes: `animateAmountValueAsState` (existing, `currency/view/animateAmountValueAsState.kt`), `rememberViewAmountFormat` (Task 3).
- Produces: `@Composable fun AnimatedAmountText(amount: ViewAmount, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default, customColor: Color? = null, maxLines: Int = 1)`.

- [ ] **Step 1: Implement**

`…/money/currency/view/AnimatedAmountText.kt`:

```kotlin
package ua.com.radiokot.money.currency.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.composeunstyled.Text

/**
 * An amount that counts to the new value when it changes.
 */
@Composable
fun AnimatedAmountText(
    amount: ViewAmount,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    customColor: Color? = null,
    maxLines: Int = 1,
) {
    val amountFormat = rememberViewAmountFormat()

    // animateAmountValueAsState remembers the precision of the first currency.
    key(amount.currency) {
        val animatedValue = animateAmountValueAsState(
            targetAmount = amount,
        )

        Text(
            text = amountFormat(
                value = animatedValue.value,
                currency = amount.currency,
                customColor = customColor,
            ),
            style = style,
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 2: Use it**

Replace these `Text(text = amountFormat(...))` calls with `AnimatedAmountText` (same style/color):
- `CategoriesScreen.kt` `RingCenter`: `AnimatedAmountText(amount = amount, customColor = color, style = TextStyle(textAlign = TextAlign.Center, fontSize = if (isCurrent) 18.sp else 13.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal))`.
- `OverviewScreen.kt`: balance → `AnimatedAmountText(amount = overview.balance, style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold))`; `TotalCard` → `AnimatedAmountText(amount = amount, customColor = color, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))`; `StatColumn` → `AnimatedAmountText(amount = amount, customColor = Color.Unspecified)`; top category amount → `AnimatedAmountText(amount = category.amount, customColor = Color.Unspecified)`.
- `AccountsScreen.kt` total: `AnimatedAmountText(amount = totalAmount.value!!, style = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center), modifier = <the existing modifier>)`.

Remove `rememberViewAmountFormat()` locals that become unused.

- [ ] **Step 3: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual: switching months on Categories/Overview and adding a transfer make the totals count to the new value (like category amounts already do).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/currency/view/AnimatedAmountText.kt app/src/main/java/ua/com/radiokot/money/categories/view/CategoriesScreen.kt app/src/main/java/ua/com/radiokot/money/overview/view/OverviewScreen.kt app/src/main/java/ua/com/radiokot/money/accounts/view/AccountsScreen.kt
git commit -m "Animate totals and balances"
```

---

### Task 18: Screen and sheet transitions

**Files:**
- Create: `app/src/main/res/anim/slide_in_end.xml`, `slide_out_start.xml`, `slide_in_start.xml`, `slide_out_end.xml`
- Modify: `app/src/main/res/values/styles.xml` (activity animation style)
- Modify: `…/money/home/view/HomeActivity.kt` (`NavHost` transitions)
- Modify: `…/money/preferences/view/PreferencesScreenNavigation.kt` (slide in/out)
- Modify: `…/money/BottomSheetNavigation.kt` (sheet spring, content transition)

**Interfaces:** none new.

- [ ] **Step 1: Activity animations**

`app/src/main/res/anim/slide_in_end.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="240"
    android:interpolator="@android:interpolator/fast_out_slow_in">
    <translate android:fromXDelta="30%" android:toXDelta="0" />
    <alpha android:fromAlpha="0" android:toAlpha="1" />
</set>
```

`slide_out_start.xml`: same with `<translate android:fromXDelta="0" android:toXDelta="-10%" />` and `<alpha android:fromAlpha="1" android:toAlpha="0" />`.
`slide_in_start.xml`: `<translate android:fromXDelta="-10%" android:toXDelta="0" />`, `<alpha android:fromAlpha="0" android:toAlpha="1" />`.
`slide_out_end.xml`: `<translate android:fromXDelta="0" android:toXDelta="30%" />`, `<alpha android:fromAlpha="1" android:toAlpha="0" />`.

In `styles.xml` add to `DefaultActivity` `<item name="android:windowAnimationStyle">@style/MoneyActivityAnimation</item>` and the style:

```xml
    <style name="MoneyActivityAnimation" parent="@android:style/Animation.Activity">
        <item name="android:activityOpenEnterAnimation">@anim/slide_in_end</item>
        <item name="android:activityOpenExitAnimation">@anim/slide_out_start</item>
        <item name="android:activityCloseEnterAnimation">@anim/slide_in_start</item>
        <item name="android:activityCloseExitAnimation">@anim/slide_out_end</item>
    </style>
```

(`TransparentActivity` keeps the platform default.)

- [ ] **Step 2: In-app navigation**

In `HomeActivity.kt` `NavHost(...)` replace the transitions:

```kotlin
            enterTransition = {
                fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))
            },
            exitTransition = { fadeOut(tween(150)) },
```

(import `androidx.compose.animation.scaleIn`). In `PreferencesScreenNavigation.kt` change `composable(PreferencesScreenRoute) {` to:

```kotlin
) = composable(
    route = PreferencesScreenRoute,
    enterTransition = {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250))
    },
    popExitTransition = {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(200))
    },
) {
```

Imports: `androidx.compose.animation.AnimatedContentTransitionScope`, `androidx.compose.animation.core.tween`, `androidx.compose.animation.fadeIn`, `androidx.compose.animation.fadeOut`.

- [ ] **Step 3: Sheets**

In `BottomSheetNavigation.kt` `MoneyAppModalBottomSheetHost`:

```kotlin
    val sheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        positionalThreshold = { sheetDragThresholdDp },
        // Prevent closing by accident.
        velocityThreshold = { Dp.Infinity },
    )
```

and the content transition between stacked sheets:

```kotlin
                ContentTransform(
                    targetContentEnter = fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
                    initialContentExit = fadeOut(tween(150)),
                    sizeTransform = null,
                )
```

(import `androidx.compose.animation.scaleIn`).

- [ ] **Step 4: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL. Manual: edit account/category activities slide in from the right and back out; More slides in and back; tabs cross-fade; the transfer sheet rises smoothly without bounce; transfer → counterparty selection sheet cross-fades with a slight zoom; the sheet still cannot be flung closed by accident.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/anim app/src/main/res/values/styles.xml app/src/main/java/ua/com/radiokot/money/home/view/HomeActivity.kt app/src/main/java/ua/com/radiokot/money/preferences/view/PreferencesScreenNavigation.kt app/src/main/java/ua/com/radiokot/money/BottomSheetNavigation.kt
git commit -m "Smoother screen and sheet transitions"
```

---

### Task 19: Swipe a transaction to edit/delete, delete with undo

**Files:**
- Create: `…/money/transfers/view/SwipeRevealRow.kt`, `…/money/transfers/history/view/PendingTransferDeletions.kt`
- Modify: `…/money/transfers/view/TransferList.kt`, `…/money/transfers/history/view/ActivityViewModel.kt`, `…/money/transfers/history/view/ActivityScreen.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/transfers/history/view/PendingTransferDeletionsTest.kt`, `app/src/test/java/ua/com/radiokot/money/transfers/view/SwipeRevealTest.kt`

**Interfaces:**
- Consumes: `RevertTransferUseCase` (existing), `R.drawable.ic_tabler_pencil`, `ic_tabler_trash` (Task 6).
- Produces: `fun shouldSettleRevealed(offsetPx: Float, revealWidthPx: Float, velocityX: Float, flingVelocity: Float = 1200f): Boolean`; `@Composable fun SwipeRevealRow(modifier, isSwipeEnabled: Boolean, actions: @Composable RowScope.(close: () -> Unit) -> Unit, content: @Composable () -> Unit)`; `class PendingTransferDeletions` (API in Step 3); `ActivityViewModel.onTransferItemDeleteClicked(item)`, `onUndoDeletionClicked()`, `isUndoDeletionVisible: StateFlow<Boolean>`; `TransferList(…, onTransferItemEditClicked: ((ViewTransferListItem.Transfer) -> Unit)? = null, onTransferItemDeleteClicked: ((ViewTransferListItem.Transfer) -> Unit)? = null)`.

Behaviour: swipe a row to the left → Edit (pencil) and Delete (trash) appear. Delete hides the row immediately and shows "Transaction deleted · Undo" for 4 s; after that (or when the screen is left) the transfer is reverted with the existing `RevertTransferUseCase` (balances restored, same as the long-press revert). Undo cancels before anything is written. A right swipe on a closed row is not claimed by the row, so it still switches to the previous month (Task 16); a left swipe on a row reveals actions instead of the next month — next month stays available by swiping on headers/empty space or the period arrows.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/ua/com/radiokot/money/transfers/history/view/PendingTransferDeletionsTest.kt`:

```kotlin
package ua.com.radiokot.money.transfers.history.view

import org.junit.Assert
import org.junit.Test

class PendingTransferDeletionsTest {

    @Test
    fun schedule_HidesOnce() {
        val deletions = PendingTransferDeletions()
        Assert.assertTrue(deletions.schedule("a"))
        Assert.assertFalse(deletions.schedule("a"))
        Assert.assertEquals(setOf("a"), deletions.hiddenIds)
    }

    @Test
    fun undo_RestoresAndPreventsCommit() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        Assert.assertTrue(deletions.undo("a"))
        Assert.assertEquals(emptySet<String>(), deletions.hiddenIds)
        Assert.assertFalse(deletions.take("a"))
    }

    @Test
    fun take_CommitsOnce_AndCannotBeUndone() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        Assert.assertTrue(deletions.take("a"))
        Assert.assertFalse(deletions.take("a"))
        Assert.assertFalse(deletions.undo("a"))
        // Stays hidden while and after reverting.
        Assert.assertEquals(setOf("a"), deletions.hiddenIds)
        Assert.assertFalse(deletions.schedule("a"))
    }

    @Test
    fun restore_AfterFailedCommit_ShowsAgain() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        deletions.take("a")
        deletions.restore("a")
        Assert.assertEquals(emptySet<String>(), deletions.hiddenIds)
        Assert.assertTrue(deletions.schedule("a"))
    }

    @Test
    fun takeAll_ReturnsOnlyPending() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        deletions.schedule("b")
        deletions.schedule("c")
        deletions.take("a")
        deletions.undo("b")
        Assert.assertEquals(setOf("c"), deletions.takeAll())
        Assert.assertEquals(emptySet<String>(), deletions.takeAll())
        Assert.assertEquals(setOf("a", "c"), deletions.hiddenIds)
    }
}
```

`app/src/test/java/ua/com/radiokot/money/transfers/view/SwipeRevealTest.kt`:

```kotlin
package ua.com.radiokot.money.transfers.view

import org.junit.Assert
import org.junit.Test

class SwipeRevealTest {

    @Test
    fun settlesByPosition() {
        Assert.assertFalse(shouldSettleRevealed(offsetPx = -40f, revealWidthPx = 100f, velocityX = 0f))
        Assert.assertTrue(shouldSettleRevealed(offsetPx = -50f, revealWidthPx = 100f, velocityX = 0f))
        Assert.assertTrue(shouldSettleRevealed(offsetPx = -100f, revealWidthPx = 100f, velocityX = 0f))
    }

    @Test
    fun flingWins() {
        Assert.assertTrue(shouldSettleRevealed(offsetPx = -10f, revealWidthPx = 100f, velocityX = -2000f))
        Assert.assertFalse(shouldSettleRevealed(offsetPx = -90f, revealWidthPx = 100f, velocityX = 2000f))
    }

    @Test
    fun noActionsNeverReveal() {
        Assert.assertFalse(shouldSettleRevealed(offsetPx = 0f, revealWidthPx = 0f, velocityX = -5000f))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.transfers.history.view.PendingTransferDeletionsTest' --tests 'ua.com.radiokot.money.transfers.view.SwipeRevealTest'`
Expected: FAIL, unresolved `PendingTransferDeletions`, `shouldSettleRevealed`.

- [ ] **Step 3: Implement the undo queue**

`…/money/transfers/history/view/PendingTransferDeletions.kt`:

```kotlin
package ua.com.radiokot.money.transfers.history.view

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Deletions that can be undone until they are taken for committing.
 * Taken (committing or committed) ones stay hidden, unless restored after a failure.
 */
class PendingTransferDeletions {

    private data class State(
        val pending: Set<String> = emptySet(),
        val committing: Set<String> = emptySet(),
    ) {
        val hidden: Set<String>
            get() = pending + committing
    }

    private val state = MutableStateFlow(State())

    val hiddenIds: Set<String>
        get() = state.value.hidden

    val hiddenIdsFlow: Flow<Set<String>> =
        state
            .map { it.hidden }
            .distinctUntilChanged()

    /**
     * @return true if scheduled, false if it is already pending or taken.
     */
    fun schedule(transferId: String): Boolean {
        var isScheduled = false
        state.update { current ->
            isScheduled = transferId !in current.hidden
            if (isScheduled)
                current.copy(pending = current.pending + transferId)
            else
                current
        }
        return isScheduled
    }

    /**
     * @return true if the deletion was pending and is now cancelled.
     */
    fun undo(transferId: String): Boolean {
        var isUndone = false
        state.update { current ->
            isUndone = transferId in current.pending
            current.copy(pending = current.pending - transferId)
        }
        return isUndone
    }

    /**
     * Takes the deletion for committing.
     *
     * @return true if it was pending, so the caller must commit it exactly once.
     */
    fun take(transferId: String): Boolean {
        var isTaken = false
        state.update { current ->
            isTaken = transferId in current.pending
            if (isTaken)
                current.copy(
                    pending = current.pending - transferId,
                    committing = current.committing + transferId,
                )
            else
                current
        }
        return isTaken
    }

    /**
     * Takes all the pending deletions for committing.
     */
    fun takeAll(): Set<String> {
        var taken: Set<String> = emptySet()
        state.update { current ->
            taken = current.pending
            current.copy(
                pending = emptySet(),
                committing = current.committing + current.pending,
            )
        }
        return taken
    }

    /**
     * Shows a taken transfer again, e.g. when committing failed.
     */
    fun restore(transferId: String) {
        state.update { current ->
            current.copy(committing = current.committing - transferId)
        }
    }
}
```

- [ ] **Step 4: Implement the swipe row**

`…/money/transfers/view/SwipeRevealRow.kt`:

```kotlin
package ua.com.radiokot.money.transfers.view

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import kotlin.math.roundToInt

/**
 * @return whether a released row must settle revealed.
 */
fun shouldSettleRevealed(
    offsetPx: Float,
    revealWidthPx: Float,
    velocityX: Float,
    flingVelocity: Float = 1200f,
): Boolean = when {
    revealWidthPx <= 0f -> false
    velocityX <= -flingVelocity -> true
    velocityX >= flingVelocity -> false
    else -> -offsetPx >= revealWidthPx / 2f
}

/**
 * A row that reveals [actions] at its end when swiped to the left.
 * Rightward drags on a closed row are not consumed, so a parent can handle them.
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    isSwipeEnabled: Boolean,
    actions: @Composable RowScope.(close: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var actionsWidthPx by remember { mutableIntStateOf(0) }
    val close: () -> Unit = remember {
        {
            coroutineScope.launch { offsetX.animateTo(0f) }
            Unit
        }
    }

    Box(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.matchParentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .onSizeChanged { actionsWidthPx = it.width },
            ) {
                actions(close)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MoneyTheme.colors.background)
                .pointerInput(isSwipeEnabled) {
                    if (!isSwipeEnabled) {
                        return@pointerInput
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val wasRevealed = offsetX.value < 0f
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            // Claim leftward drags, or any drag of a revealed row.
                            if (overSlop < 0f || wasRevealed) {
                                change.consume()
                            }
                        } ?: return@awaitEachGesture

                        val minOffset = -actionsWidthPx.toFloat()
                        var currentOffset = offsetX.value

                        horizontalDrag(dragStart.id) { change ->
                            currentOffset = (currentOffset + change.positionChange().x)
                                .coerceIn(minOffset, 0f)
                            val snapTarget = currentOffset
                            coroutineScope.launch { offsetX.snapTo(snapTarget) }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                        }

                        val isRevealed = shouldSettleRevealed(
                            offsetPx = currentOffset,
                            revealWidthPx = actionsWidthPx.toFloat(),
                            velocityX = velocityTracker.calculateVelocity().x,
                        )
                        coroutineScope.launch {
                            offsetX.animateTo(if (isRevealed) minOffset else 0f)
                        }
                    }
                }
        ) {
            content()
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.transfers.history.view.PendingTransferDeletionsTest' --tests 'ua.com.radiokot.money.transfers.view.SwipeRevealTest'`
Expected: PASS (5 + 3 tests).

- [ ] **Step 6: ViewModel**

In `ActivityViewModel`:

1. Move `cachedIn` to the end of the existing chain so the separated paging data can be re-filtered, and rename it:

```kotlin
    private val separatedTransferItemPagingFlow: Flow<PagingData<ViewTransferListItem>> =
        transferHistoryPagerFlow
            .flatMapLatest { it.flow }
            .map { pagingData ->
                /* the existing body: today/yesterday, map, insertSeparators, map { it.first } */
            }
            .flowOn(Dispatchers.Default)
            .cachedIn(viewModelScope)

    private val pendingDeletions = PendingTransferDeletions()
    private val deletionUndoJobs = mutableMapOf<String, Job>()
    private val undoableDeletionTransferId: MutableStateFlow<String?> = MutableStateFlow(null)

    val isUndoDeletionVisible: StateFlow<Boolean> =
        undoableDeletionTransferId
            .map(viewModelScope) { it != null }

    val transferItemPagingFlow: Flow<PagingData<ViewTransferListItem>> =
        combine(
            separatedTransferItemPagingFlow,
            pendingDeletions.hiddenIdsFlow,
        ) { pagingData, hiddenIds ->
            if (hiddenIds.isEmpty())
                pagingData
            else
                pagingData.filter { item ->
                    item !is ViewTransferListItem.Transfer
                            || item.source?.id !in hiddenIds
                }
        }
```

2. Add:

```kotlin
    fun onTransferItemDeleteClicked(item: ViewTransferListItem.Transfer) {
        val transferId = item.source?.id
        if (transferId == null) {
            log.debug { "onTransferItemDeleteClicked(): missing transfer source" }
            return
        }

        if (!pendingDeletions.schedule(transferId)) {
            return
        }

        log.debug {
            "onTransferItemDeleteClicked(): scheduled deletion:" +
                    "\ntransferId=$transferId"
        }

        // Only the latest deletion can be undone from the bar,
        // an older pending one gets committed right away.
        undoableDeletionTransferId.value
            ?.also(::commitDeletion)
        undoableDeletionTransferId.value = transferId

        deletionUndoJobs[transferId] = viewModelScope.launch {
            delay(DELETION_UNDO_TIMEOUT_MS)
            if (undoableDeletionTransferId.value == transferId) {
                undoableDeletionTransferId.value = null
            }
            commitDeletion(transferId)
        }
    }

    fun onUndoDeletionClicked() {
        val transferId = undoableDeletionTransferId.value
            ?: return
        undoableDeletionTransferId.value = null

        if (pendingDeletions.undo(transferId)) {
            deletionUndoJobs.remove(transferId)?.cancel()

            log.debug {
                "onUndoDeletionClicked(): undone:" +
                        "\ntransferId=$transferId"
            }
        }
    }

    private fun commitDeletion(transferId: String) {
        if (!pendingDeletions.take(transferId)) {
            return
        }
        deletionUndoJobs.remove(transferId)

        viewModelScope.launch {
            revertTransferUseCase(
                transferId = transferId,
            )
                .onSuccess {
                    log.info { "Deleted (reverted) transfer $transferId" }
                }
                .onFailure { error ->
                    log.error(error) { "commitDeletion(): failed to revert transfer" }
                    pendingDeletions.restore(transferId)
                }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        // Leaving the screen commits what is still pending.
        pendingDeletions.takeAll().forEach { transferId ->
            GlobalScope.launch {
                revertTransferUseCase(
                    transferId = transferId,
                )
            }
        }
        super.onCleared()
    }
```

and a top-level `private const val DELETION_UNDO_TIMEOUT_MS = 4000L`. Imports: `androidx.paging.filter`, `kotlinx.coroutines.DelicateCoroutinesApi`, `kotlinx.coroutines.GlobalScope`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.flow.MutableStateFlow`.

Note on `commitDeletion` inside its own undo job: it removes the job from the map but launches the revert in a separate coroutine, so nothing cancels the revert.

- [ ] **Step 7: List and screen**

`TransferList.kt` — add parameters `onTransferItemEditClicked: ((ViewTransferListItem.Transfer) -> Unit)? = null, onTransferItemDeleteClicked: ((ViewTransferListItem.Transfer) -> Unit)? = null,` and replace the `TransferItem(...)` call in the `is ViewTransferListItem.Transfer ->` branch with:

```kotlin
                    SwipeRevealRow(
                        isSwipeEnabled = onTransferItemDeleteClicked != null,
                        actions = { close ->
                            RevealAction(
                                icon = R.drawable.ic_tabler_pencil,
                                contentDescription = "Edit",
                                tint = MoneyTheme.colors.onBackground,
                                background = MoneyTheme.colors.surfaceVariant,
                                onClick = {
                                    close()
                                    onTransferItemEditClicked?.invoke(item)
                                },
                            )
                            RevealAction(
                                icon = R.drawable.ic_tabler_trash,
                                contentDescription = "Delete",
                                tint = MoneyTheme.colors.onWarning,
                                background = MoneyTheme.colors.expense,
                                onClick = {
                                    onTransferItemDeleteClicked?.invoke(item)
                                },
                            )
                        },
                        modifier = Modifier
                            .padding(
                                bottom = 16.dp,
                            )
                    ) {
                        TransferItem(
                            item = item,
                            amountFormat = amountFormat,
                            modifier = clickableModifier
                        )
                    }
```

and add at the end of the file:

```kotlin
@Composable
private fun RevealAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .fillMaxHeight()
        .width(64.dp)
        .background(background)
        .clickable(onClick = onClick)
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}
```

Imports: `androidx.annotation.DrawableRes`, `androidx.compose.foundation.background`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.fillMaxHeight`, `androidx.compose.ui.res.painterResource`, `com.composeunstyled.Icon`, `ua.com.radiokot.money.R`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`.

`ActivityScreen.kt` — `ActivityScreenRoot` passes `onTransferItemDeleteClicked = remember { viewModel::onTransferItemDeleteClicked }`, `isUndoDeletionVisible = viewModel.isUndoDeletionVisible.collectAsState()`, `onUndoDeletionClicked = remember { viewModel::onUndoDeletionClicked }`; `TransferList(...)` gets `onTransferItemEditClicked = onTransferItemClicked, onTransferItemDeleteClicked = onTransferItemDeleteClicked`. Wrap the screen's `Column` in a `Box(modifier = modifier)` (the `Column` then uses `Modifier.fillMaxSize()` plus the existing `periodSwipe`) and add after the `Column`, inside the `Box`:

```kotlin
    AnimatedVisibility(
        visible = isUndoDeletionVisible.value,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MoneyTheme.colors.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                )
        ) {
            Text(
                text = "Transaction deleted",
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Undo",
                fontWeight = FontWeight.SemiBold,
                color = MoneyTheme.colors.income,
                modifier = Modifier
                    .clickable(onClick = onUndoDeletionClicked)
                    .padding(4.dp),
            )
        }
    }
```

(`AnimatedVisibility` here is the `BoxScope` overload; imports: `androidx.compose.animation.AnimatedVisibility`, `fadeIn`, `fadeOut`, `slideInVertically`, `slideOutVertically`, `androidx.compose.foundation.background`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.Box`, `Row`, `fillMaxSize`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.text.font.FontWeight`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`.)

The long-press revert confirmation stays as it is.

- [ ] **Step 8: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual:
- Swipe a row left → Edit/Delete; release halfway → snaps; swipe back right closes; vertical list scrolling still works while rows are closed and open.
- Edit opens the transfer sheet for that transfer and closes the row.
- Delete → row disappears, undo bar for 4 s; Undo → row is back, account balance unchanged; without Undo → balance restored (as with long-press revert), the row stays gone.
- Delete, then immediately switch to the Accounts tab → the transfer is still reverted (check the balance).
- Swipe right on a closed row → previous month.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/transfers app/src/test/java/ua/com/radiokot/money/transfers
git commit -m "Swipe transactions to edit or delete, with undo"
```

---

## E5 — Transfer sheet

`AmountInputState` already implements ÷ × − + with currency-precision `BigInteger` arithmetic (`evaluate()`), and `AmountKeyboard` already has operator keys. No new evaluator is needed; Task 20 pins the existing arithmetic with tests and reshapes the keypad.

### Task 20: Calculator keypad with currency and date keys

**Files:**
- Modify: `…/money/currency/view/AmountKeyboard.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/currency/view/AmountInputStateTest.kt`

**Interfaces:**
- Consumes: `AmountInputState` (existing), `itemAccentColor` (Task 5), `R.drawable.ic_tabler_backspace`, `ic_tabler_calendar`, `ic_tabler_check`, `ic_tabler_chevron_right` (Task 6).
- Produces: `AmountKeyboard(modifier, inputState, colorScheme, mainAction = Done, onMainActionClicked = null, onCurrencyClicked: (() -> Unit)? = null, onDateClicked: (() -> Unit)? = null)` — the two new keys are disabled when their callback is null, so `AccountActionSheet` (balance edit) keeps working unchanged.

Layout (5 × 4, the confirm key spans two rows):

```
 ÷   7   8   9   ⌫
 ×   4   5   6   📅
 −   1   2   3  ┌─┐
 +   ₴   0   .  │✓│
                └─┘
```

- [ ] **Step 1: Write the regression tests for the arithmetic**

`app/src/test/java/ua/com/radiokot/money/currency/view/AmountInputStateTest.kt`:

```kotlin
package ua.com.radiokot.money.currency.view

import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class AmountInputStateTest {

    private val usd = ViewCurrency(symbol = "$", precision = 2)
    private val divide = AmountInputState.Operator.Divide.symbol
    private val multiply = AmountInputState.Operator.Multiply.symbol
    private val minus = AmountInputState.Operator.Minus.symbol
    private val plus = AmountInputState.Operator.Plus.symbol

    private fun state() = AmountInputState(
        currency = usd,
        initialValue = BigInteger.ZERO,
        format = ViewAmountFormat(Locale.ENGLISH),
    )

    private fun AmountInputState.type(vararg symbols: Char) = apply {
        symbols.forEach(::acceptInput)
    }

    @Test
    fun plus() {
        Assert.assertEquals("15 ", state().type('1', '2', plus, '3', '=').inputText)
    }

    @Test
    fun minus_CanGoNegative() {
        Assert.assertEquals("-15 ", state().type('1', '0', minus, '2', '5', '=').inputText)
    }

    @Test
    fun multiply_RespectsPrecision() {
        Assert.assertEquals("3 ", state().type('1', '.', '5', multiply, '2', '=').inputText)
    }

    @Test
    fun divide_RespectsPrecision() {
        Assert.assertEquals("2.5 ", state().type('1', '0', divide, '4', '=').inputText)
    }

    @Test
    fun divideByZero_IsZero() {
        Assert.assertEquals("0 ", state().type('5', divide, '0', '=').inputText)
    }

    @Test
    fun nextOperator_EvaluatesThePrevious() {
        val state = state().type('2', plus, '3', multiply)
        Assert.assertEquals("5 $multiply  ", state.inputText)
        Assert.assertTrue(state.isEvaluationNeeded)
    }

    @Test
    fun backspace_RemovesTheOperator() {
        val state = state().type('7', plus, '⌫')
        Assert.assertEquals("7 ", state.inputText)
        Assert.assertFalse(state.isEvaluationNeeded)
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.currency.view.AmountInputStateTest'`
Expected: PASS (7 tests) — they pin existing behaviour. If a test fails, the failure shows the real current behaviour: fix the test only if the app behaves that way on device too and it is acceptable; otherwise fix `AmountInputState`. If the run fails with `Method ... not mocked` from the Compose snapshot runtime, add to `android { }` in `app/build.gradle`: `testOptions { unitTests.returnDefaultValues = true }`.

- [ ] **Step 3: Rewrite the keypad**

Replace everything in `AmountKeyboard.kt` from `@Composable fun AmountKeyboard(` down to (not including) `enum class AmountKeyboardMainAction` with:

```kotlin
@Composable
fun AmountKeyboard(
    modifier: Modifier = Modifier,
    inputState: AmountInputState,
    colorScheme: ItemColorScheme,
    mainAction: AmountKeyboardMainAction = AmountKeyboardMainAction.Done,
    onMainActionClicked: ((AmountKeyboardMainAction) -> Unit)? = null,
    onCurrencyClicked: (() -> Unit)? = null,
    onDateClicked: (() -> Unit)? = null,
) = BoxWithConstraints(
    modifier = modifier,
) {
    val buttonGap = 8.dp
    val buttonWidth = (maxWidth - buttonGap * 4) / 5
    val buttonHeight = (maxHeight - buttonGap * 3) / 4
    val colors = MoneyTheme.colors
    val actionBackground = Modifier.background(colors.surfaceVariant)
    val keySize = Modifier.size(width = buttonWidth, height = buttonHeight)
    val confirmBackground =
        if (colors.isDark)
            itemAccentColor(colorScheme)
        else
            Color(colorScheme.primary)
    val confirmContentColor =
        if (colors.isDark)
            colors.background
        else
            Color(colorScheme.onPrimary)

    val hapticFeedback = LocalHapticFeedback.current
    val onSymbolClicked = remember(inputState) {
        { symbol: Char ->
            hapticFeedback.performHapticFeedback(
                HapticFeedbackType.KeyboardTap
            )
            inputState.acceptInput(symbol)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val animateClear = remember(inputState) {
        {
            coroutineScope.launch {
                inputState.animateClear()
            }
            Unit
        }
    }

    CompositionLocalProvider(
        LocalIndication provides remember(::ScaleIndication),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(buttonGap),
                modifier = Modifier
                    .graphicsLayer()
            ) {
                listOf(
                    listOf(AmountInputState.Operator.Divide.symbol, '7', '8', '9'),
                    listOf(AmountInputState.Operator.Multiply.symbol, '4', '5', '6'),
                    listOf(AmountInputState.Operator.Minus.symbol, '1', '2', '3'),
                ).forEach { rowSymbols ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(buttonGap),
                    ) {
                        rowSymbols.forEachIndexed { index, symbol ->
                            KeyButton(
                                text = symbol.toString(),
                                onClick = { onSymbolClicked(symbol) },
                                modifier = keySize
                                    .then(if (index == 0) actionBackground else Modifier)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(buttonGap),
                ) {
                    KeyButton(
                        text = AmountInputState.Operator.Plus.symbol.toString(),
                        onClick = { onSymbolClicked(AmountInputState.Operator.Plus.symbol) },
                        modifier = keySize.then(actionBackground)
                    )
                    KeyButton(
                        text = inputState.currency.symbol,
                        contentDescription = "Switch currency",
                        fontSize = 20.sp,
                        isEnabled = onCurrencyClicked != null,
                        onClick = { onCurrencyClicked?.invoke() },
                        modifier = keySize.then(actionBackground)
                    )
                    KeyButton(
                        text = "0",
                        onClick = { onSymbolClicked('0') },
                        modifier = keySize
                    )
                    KeyButton(
                        text = inputState.decimalSeparator.toString(),
                        onClick = { onSymbolClicked(inputState.decimalSeparator) },
                        modifier = keySize
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(buttonGap),
            ) {
                KeyButton(
                    icon = R.drawable.ic_tabler_backspace,
                    contentDescription = "Erase",
                    onClick = { onSymbolClicked('⌫') },
                    onLongClick = animateClear,
                    modifier = keySize.then(actionBackground)
                )
                KeyButton(
                    icon = R.drawable.ic_tabler_calendar,
                    contentDescription = "Date",
                    isEnabled = onDateClicked != null,
                    onClick = { onDateClicked?.invoke() },
                    modifier = keySize.then(actionBackground)
                )
                KeyButton(
                    text =
                        if (inputState.isEvaluationNeeded)
                            "="
                        else
                            null,
                    icon =
                        if (inputState.isEvaluationNeeded)
                            null
                        else when (mainAction) {
                            AmountKeyboardMainAction.Done -> R.drawable.ic_tabler_check
                            AmountKeyboardMainAction.Next -> R.drawable.ic_tabler_chevron_right
                        },
                    contentDescription = "Confirm",
                    contentColor = confirmContentColor,
                    onClick = {
                        if (inputState.isEvaluationNeeded) {
                            onSymbolClicked('=')
                        } else {
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.Confirm
                            )
                            onMainActionClicked?.invoke(mainAction)
                        }
                    },
                    modifier = Modifier
                        .size(
                            width = buttonWidth,
                            height = buttonHeight * 2 + buttonGap,
                        )
                        .background(confirmBackground)
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    @DrawableRes icon: Int? = null,
    contentDescription: String? = text,
    contentColor: Color = Color.Unspecified,
    fontSize: TextUnit = 28.sp,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MoneyTheme.colors
    val resolvedContentColor = when {
        !isEnabled -> colors.outlineDisabled
        contentColor != Color.Unspecified -> contentColor
        else -> colors.onBackground
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .then(modifier)
            .then(
                if (onLongClick != null)
                    Modifier.combinedClickable(
                        enabled = isEnabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                else
                    Modifier.clickable(
                        enabled = isEnabled,
                        onClick = onClick,
                    )
            )
            .border(
                width = 1.dp,
                color = colors.outline,
                shape = shape,
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = resolvedContentColor,
                modifier = Modifier.size(26.dp),
            )
        }
        if (text != null) {
            Text(
                text = text,
                fontSize = fontSize,
                color = resolvedContentColor,
            )
        }
    }
}
```

Imports to add: `androidx.annotation.DrawableRes`, `androidx.compose.ui.res.painterResource`, `androidx.compose.ui.unit.TextUnit`, `com.composeunstyled.Icon`, `ua.com.radiokot.money.R`, `ua.com.radiokot.money.colors.view.itemAccentColor`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`; remove `fillMaxHeight` if unused. The `Preview` at the bottom keeps compiling (new parameters are optional).

- [ ] **Step 4: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual: the transfer sheet keypad has the new layout (date/currency keys are wired in Task 21, disabled until then); `12 + 3 =` shows 15 and the confirm key shows "=" while an operator is pending; long-press ⌫ clears; account balance edit (account action sheet) still works.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/currency/view/AmountKeyboard.kt app/src/test/java/ua/com/radiokot/money/currency/view/AmountInputStateTest.kt
git commit -m "Calculator keypad with currency and date keys"
```

---

### Task 21: Transfer sheet redesign

**Files:**
- Create: `…/money/transfers/view/TransferSheetLabels.kt`
- Modify: `…/money/colors/data/ItemColorSchemeAccents.kt` (`onAccent`)
- Modify: `…/money/categories/view/SelectableSubcategoryRow.kt` (outlined chips with icon, single line)
- Modify: `…/money/currency/view/AnimatedAmountInputText.kt` (`color`, `fontSize`, input animation)
- Modify: `…/money/transfers/view/TransferSheetViewModel.kt` (`subcategoriesIcon`)
- Modify: `…/money/transfers/view/TransferSheet.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/transfers/view/TransferSheetLabelsTest.kt`, `app/src/test/java/ua/com/radiokot/money/colors/data/ItemColorSchemeAccentsTest.kt`

**Interfaces:**
- Consumes: `AmountKeyboard(onCurrencyClicked, onDateClicked)` (Task 20), `ItemLogo`, `itemAccentColor`, `ItemColorSchemeAccents` (Task 5), `R.drawable.ic_tabler_arrows_exchange` (Task 6).
- Produces: `enum class TransferKind { Expense, Income, Transfer }`, `fun transferKindOf(source: ViewTransferCounterparty, destination: ViewTransferCounterparty): TransferKind`, `val TransferKind.label: String`, `fun counterpartyHalfLabel(isSource: Boolean, counterparty: ViewTransferCounterparty): String`; `ItemColorSchemeAccents.onAccent(scheme, isDark, schemesByName = default): Long`; `TransferSheetViewModel.subcategoriesIcon: StateFlow<ItemIcon?>`; `SelectableSubcategoryRow(modifier, itemList, colorScheme, icon: ItemIcon? = null, onItemClicked)`; `AnimatedAmountInputText(modifier, amountInputState, color: Color = Color.Unspecified, fontSize: TextUnit = 20.sp)`.

Kept behaviour: tapping a half opens the counterparty selection (`onSourceClicked` / `onDestinationClicked`, unchanged ViewModel logic), account→account swap button, editing (the ViewModel is unchanged except one new property), income direction (source is an income category), two amounts when currencies differ (the currency key switches which amount the keypad edits; the confirm key shows "next" on the source amount as before).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/ua/com/radiokot/money/transfers/view/TransferSheetLabelsTest.kt`:

```kotlin
package ua.com.radiokot.money.transfers.view

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.currency.view.ViewCurrency

class TransferSheetLabelsTest {

    private val scheme = ItemColorScheme(name = "Red2", primary = 0xFFF1B6B7, onPrimary = 0xFFAC2B2E)
    private val usd = ViewCurrency(symbol = "$", precision = 2)
    private val account = ViewTransferCounterparty.Account(
        accountTitle = "Card",
        currency = usd,
        colorScheme = scheme,
        icon = null,
    )
    private val category = ViewTransferCounterparty.Category(
        categoryTitle = "Food",
        subcategoryTitle = null,
        currency = usd,
        colorScheme = scheme,
        icon = null,
    )

    @Test
    fun kind() {
        Assert.assertEquals(TransferKind.Expense, transferKindOf(source = account, destination = category))
        Assert.assertEquals(TransferKind.Income, transferKindOf(source = category, destination = account))
        Assert.assertEquals(TransferKind.Transfer, transferKindOf(source = account, destination = account))
        Assert.assertEquals("Expense", TransferKind.Expense.label)
        Assert.assertEquals("Income", TransferKind.Income.label)
        Assert.assertEquals("Transfer", TransferKind.Transfer.label)
    }

    @Test
    fun halfLabels() {
        Assert.assertEquals("From account", counterpartyHalfLabel(isSource = true, counterparty = account))
        Assert.assertEquals("To category", counterpartyHalfLabel(isSource = false, counterparty = category))
        Assert.assertEquals("From category", counterpartyHalfLabel(isSource = true, counterparty = category))
        Assert.assertEquals("To account", counterpartyHalfLabel(isSource = false, counterparty = account))
    }
}
```

Append to `ItemColorSchemeAccentsTest`:

```kotlin
    @Test
    fun onAccent_IsAccentSchemeOnPrimary() {
        Assert.assertEquals(
            0xFFFFF6F6,
            ItemColorSchemeAccents.onAccent(schemesByName.getValue("Red2"), isDark = false, schemesByName),
        )
        Assert.assertEquals(
            0xFF181818,
            ItemColorSchemeAccents.onAccent(schemesByName.getValue("Black5"), isDark = true, schemesByName),
        )
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.transfers.view.TransferSheetLabelsTest' --tests 'ua.com.radiokot.money.colors.data.ItemColorSchemeAccentsTest'`
Expected: FAIL, unresolved `transferKindOf`, `onAccent`.

- [ ] **Step 3: Implement labels and onAccent**

`…/money/transfers/view/TransferSheetLabels.kt`:

```kotlin
package ua.com.radiokot.money.transfers.view

enum class TransferKind {
    Expense,
    Income,
    Transfer,
    ;

    val label: String
        get() = when (this) {
            Expense -> "Expense"
            Income -> "Income"
            Transfer -> "Transfer"
        }
}

/**
 * Income comes from an income category, expense goes to a category,
 * everything else is a transfer between accounts.
 */
fun transferKindOf(
    source: ViewTransferCounterparty,
    destination: ViewTransferCounterparty,
): TransferKind = when {
    source is ViewTransferCounterparty.Category -> TransferKind.Income
    destination is ViewTransferCounterparty.Category -> TransferKind.Expense
    else -> TransferKind.Transfer
}

fun counterpartyHalfLabel(
    isSource: Boolean,
    counterparty: ViewTransferCounterparty,
): String =
    (if (isSource) "From " else "To ") +
            (if (counterparty is ViewTransferCounterparty.Category) "category" else "account")
```

In `ItemColorSchemeAccents` extract the name choice and add `onAccent`:

```kotlin
    private fun accentSchemeName(scheme: ItemColorScheme, isDark: Boolean): String {
        val family = scheme.name.trimEnd(Char::isDigit)
        return if (family == "Black")
            if (isDark) "Black2" else "Black4"
        else
            family + "4"
    }

    fun accent(
        scheme: ItemColorScheme,
        isDark: Boolean,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): Long =
        schemesByName[accentSchemeName(scheme, isDark)]?.primary
            ?: scheme.primary

    /**
     * @return a color readable on top of [accent].
     */
    fun onAccent(
        scheme: ItemColorScheme,
        isDark: Boolean,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): Long =
        schemesByName[accentSchemeName(scheme, isDark)]?.onPrimary
            ?: scheme.onPrimary
```

(replacing the previous `accent` body).

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.transfers.view.TransferSheetLabelsTest' --tests 'ua.com.radiokot.money.colors.data.ItemColorSchemeAccentsTest'`
Expected: PASS.

- [ ] **Step 5: Chips, amount text, ViewModel**

`SelectableSubcategoryRow.kt`: add parameter `icon: ItemIcon? = null,` (after `colorScheme`) to `SelectableSubcategoryRow`, pass it to each item, and replace `SelectableSubcategoryListItem` with:

```kotlin
@Composable
private fun SelectableSubcategoryListItem(
    modifier: Modifier = Modifier,
    item: ViewSelectableSubcategoryListItem,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
) {
    val isDark = MoneyTheme.colors.isDark
    val accentColor = remember(colorScheme, isDark) {
        Color(ItemColorSchemeAccents.accent(colorScheme, isDark))
    }
    val onAccentColor = remember(colorScheme, isDark) {
        Color(ItemColorSchemeAccents.onAccent(colorScheme, isDark))
    }
    val contentColor =
        if (item.isSelected)
            onAccentColor
        else
            accentColor
    val shape = RoundedCornerShape(percent = 50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .run {
                if (item.isSelected)
                    background(color = accentColor, shape = shape)
                else
                    border(width = 1.dp, color = accentColor, shape = shape)
            }
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp,
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon.resId),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = item.title,
            color = contentColor,
            singleLine = true,
            maxLines = 1,
        )
    }
}
```

Move the `.clickable(onClick = { onItemClicked(item) })` from the item call site to stay as is (it is the `modifier` passed in; `clip` is applied first inside, so ripple/scale follow the pill). Imports: `androidx.compose.foundation.layout.Row`, `Spacer`, `size`, `width`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.res.painterResource`, `com.composeunstyled.Icon`, `ua.com.radiokot.money.colors.data.ItemColorSchemeAccents`, `ua.com.radiokot.money.colors.data.ItemIcon`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`. The `LazyRow` already makes the row a single, horizontally scrollable line.

`AnimatedAmountInputText.kt`: add parameters `color: Color = Color.Unspecified, fontSize: TextUnit = 20.sp,`, build the style as

```kotlin
    val textStyle = TextStyle(
        textAlign = TextAlign.End,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
```

and give the `AnimatedContent` a visible but short input transition:

```kotlin
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(100)) + slideInVertically(tween(100)) { it / 4 },
                        initialContentExit = ExitTransition.None,
                    )
                },
```

(imports `androidx.compose.animation.fadeIn`, `androidx.compose.animation.slideInVertically`, `androidx.compose.animation.core.tween`, `androidx.compose.ui.unit.TextUnit`).

`TransferSheetViewModel.kt`, next to `subcategoriesColorScheme`:

```kotlin
    val subcategoriesIcon: StateFlow<ItemIcon?> =
        combine(
            _sourceCounterparty,
            _destinationCounterparty,
        ) { source, destination ->
            ((source as? TransferCounterparty.Category)
                ?: (destination as? TransferCounterparty.Category))
                ?.category
                ?.icon
        }
            .stateIn(stateFlowScope, SharingStarted.Lazily, null)
```

(import `ua.com.radiokot.money.colors.data.ItemIcon`).

- [ ] **Step 6: The sheet**

In `TransferSheet.kt`:
1. `TransferSheetRoot` passes `subcategoriesIcon = viewModel.subcategoriesIcon.collectAsState(),`; the private `TransferSheet` gets `subcategoriesIcon: State<ItemIcon?>,`; the preview passes `subcategoriesIcon = categoryIcon.let(::mutableStateOf),`.
2. Replace the body of the private `TransferSheet` (everything inside `BoxWithConstraints(...) { … }`) with:

```kotlin
    val maxSheetHeightDp =
        if (maxHeight < 400.dp)
            maxHeight
        else
            maxHeight * 0.85f
    val colors = MoneyTheme.colors
    val kind = remember(source, destination) {
        transferKindOf(source, destination)
    }
    val accentScheme = remember(source, destination) {
        if (source is ViewTransferCounterparty.Category)
            source.colorScheme
        else
            destination.colorScheme
    }
    val accentColor = itemAccentColor(accentScheme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                max = maxSheetHeightDp,
            )
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
            ) {
                CounterpartyHalf(
                    label = counterpartyHalfLabel(isSource = true, counterparty = source),
                    title = (source as? ViewTransferCounterparty.Category)?.categoryTitle
                        ?: source.title,
                    colorScheme = source.colorScheme,
                    icon = source.icon,
                    onClick = onSourceClicked,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                CounterpartyHalf(
                    label = counterpartyHalfLabel(isSource = false, counterparty = destination),
                    title = (destination as? ViewTransferCounterparty.Category)?.categoryTitle
                        ?: destination.title,
                    colorScheme = destination.colorScheme,
                    icon = destination.icon,
                    onClick = onDestinationClicked,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            if (isSwapCounterpartiesShown) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .clickable(onClick = onSwapCounterpartiesClicked)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_arrows_exchange),
                        contentDescription = "Swap",
                        tint = colors.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (subcategoryItemList.value.isNotEmpty()
            && subcategoriesColorScheme.value != null
        ) {
            SelectableSubcategoryRow(
                itemList = subcategoryItemList,
                colorScheme = subcategoriesColorScheme.value!!,
                icon = subcategoriesIcon.value,
                onItemClicked = onSubcategoryItemClicked,
                modifier = Modifier
                    .padding(
                        vertical = 12.dp,
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        val sourceAmountInputState = rememberAmountInputState(
            currency = source.currency,
            initialValue = sourceAmountValue.value,
        )
        LaunchedEffect(sourceAmountInputState) {
            sourceAmountInputState
                .valueFlow
                .collect(onNewSourceAmountValueParsed)
        }
        val destinationAmountInputState = rememberAmountInputState(
            currency = destination.currency,
            initialValue = destinationAmountValue.value,
        )
        LaunchedEffect(destinationAmountInputState) {
            destinationAmountInputState
                .valueFlow
                .collect(onNewDestinationAmountValueParsed)
        }
        var isEnteringSourceAmount by remember(isSourceInputShown) {
            mutableStateOf(isSourceInputShown)
        }

        Text(
            text = kind.label,
            color = accentColor,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isSourceInputShown) {
            // Different currencies: both amounts, the keypad edits the highlighted one.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                listOf(
                    Triple(true, sourceAmountInputState, source),
                    Triple(false, destinationAmountInputState, destination),
                ).forEach { (isSourceAmount, inputState, counterparty) ->
                    val isCurrent = isEnteringSourceAmount == isSourceAmount
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) accentColor else Color.Transparent,
                            )
                            .clickable { isEnteringSourceAmount = isSourceAmount }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = counterparty.title,
                            color = colors.onBackgroundSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        AnimatedAmountInputText(
                            amountInputState = inputState,
                            color = accentColor,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                        )
                    }
                }
            }
        } else {
            AnimatedAmountInputText(
                amountInputState = destinationAmountInputState,
                color = accentColor,
                fontSize = 32.sp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
        ) {
            var emptyMemoFieldOffset by remember {
                mutableStateOf(IntOffset.Zero)
            }

            if (memo.value.isEmpty()) {
                Text(
                    text = "Notes",
                    style = TextStyle(
                        fontStyle = FontStyle.Italic,
                        color = colors.onBackgroundSecondary,
                    ),
                    modifier = Modifier
                        .onSizeChanged { (width, _) ->
                            emptyMemoFieldOffset = IntOffset(
                                x = -width / 2,
                                y = 0,
                            )
                        }
                )
            }

            BasicTextField(
                value = memo.value,
                onValueChange = onMemoUpdated,
                textStyle = TextStyle(
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    color = colors.onBackground,
                ),
                cursorBrush = SolidColor(colors.onBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .offset {
                        if (memo.value.isEmpty())
                            emptyMemoFieldOffset
                        else
                            IntOffset.Zero
                    }
            )
        }

        AmountKeyboard(
            inputState =
                if (isEnteringSourceAmount)
                    sourceAmountInputState
                else
                    destinationAmountInputState,
            colorScheme = accentScheme,
            mainAction =
                if (isEnteringSourceAmount)
                    AmountKeyboardMainAction.Next
                else
                    AmountKeyboardMainAction.Done,
            onMainActionClicked = { action ->
                when (action) {

                    AmountKeyboardMainAction.Done ->
                        onSaveClicked()

                    AmountKeyboardMainAction.Next ->
                        isEnteringSourceAmount = false
                }
            },
            onCurrencyClicked =
                if (isSourceInputShown)
                    { { isEnteringSourceAmount = !isEnteringSourceAmount } }
                else
                    null,
            onDateClicked = onDateClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                )
                .height(maxSheetHeightDp / 2.4f)
        )

        Text(
            text = date.value.getText(),
            color = colors.onBackgroundSecondary,
            modifier = Modifier
                .clickable(onClick = onDateClicked)
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                )
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
```

3. Add the half composable at the end of the file:

```kotlin
@Composable
private fun CounterpartyHalf(
    modifier: Modifier = Modifier,
    label: String,
    title: String,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
    onClick: () -> Unit,
) {
    val isDark = MoneyTheme.colors.isDark
    val (backgroundColor, contentColor) = remember(colorScheme, isDark) {
        if (isDark) {
            val darkColors = ItemColorSchemeAccents.darkLogoColors(colorScheme)
            Color(darkColors.background) to Color(darkColors.foreground)
        } else {
            Color(colorScheme.primary) to Color(colorScheme.onPrimary)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp,
            )
    ) {
        ItemLogo(
            title = title,
            colorScheme = colorScheme,
            icon = icon,
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .border(
                    width = 1.dp,
                    color = contentColor.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                color = contentColor.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

Imports to add: `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.layout.width`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.graphics.SolidColor`, `androidx.compose.ui.res.painterResource`, `androidx.compose.ui.text.style.TextOverflow`, `com.composeunstyled.Icon`, `com.composeunstyled.Text`, `ua.com.radiokot.money.R`, `ua.com.radiokot.money.colors.data.ItemColorSchemeAccents`, `ua.com.radiokot.money.colors.data.ItemIcon`, `ua.com.radiokot.money.colors.view.ItemLogo`, `ua.com.radiokot.money.colors.view.itemAccentColor`, `ua.com.radiokot.money.uikit.theme.MoneyTheme`; remove `focusable`, `focusRequester`, `FocusRequester`, `TextButton` imports if unused. The outer `BoxWithConstraints` modifier uses `.background(MoneyTheme.colors.surface)` (from Task 4).

- [ ] **Step 7: Build and check**

Run: `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Manual, light and dark:
- Expense from a category tap: left half "From account" in the account color, right half "To category" in the category color, both with icon badges; tapping each half opens the counterparty selection.
- Subcategories: one horizontally scrollable line of outlined pills with the category icon in the category color; tap selects (filled), tap again deselects; long titles never wrap.
- "Expense" label and amount in the category color; digits animate in; `12+3` then confirm shows `=` first, then ✓ saves.
- Notes field, then keypad; the calendar key and the date text under the keypad open the date picker; the picked date is shown under the keypad.
- Income (from an income category) shows "Income"; account → account shows "Transfer" with the swap button; different currencies show two amounts and the currency key switches between them; editing an existing transfer pre-fills everything and saves.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/transfers/view app/src/main/java/ua/com/radiokot/money/categories/view/SelectableSubcategoryRow.kt app/src/main/java/ua/com/radiokot/money/currency/view/AnimatedAmountInputText.kt app/src/main/java/ua/com/radiokot/money/colors/data/ItemColorSchemeAccents.kt app/src/test/java/ua/com/radiokot/money
git commit -m "Redesign the transfer sheet in the 1Money style"
```

---

## Self-review notes (spec coverage)

| Spec item | Task |
|---|---|
| E1 MoneyTheme tokens, CompositionLocal, dark following system, light/dark/system preference | 1, 2 |
| E1 replace ≈56 hardcoded colors, white window | 1 (window), 3, 4 |
| E1 dark avatars: circle, dark tint bg, saturated fg | 5 |
| E1 ≈150 Tabler `*_itemicon` drawables in the picker, MIT attribution, reproducible script | 6 |
| E1 bottom bar line icons | 7 (+15) |
| E1 one-off SQL icons by title | 8 |
| E2 4 columns, row + 2 rows around 2×2 ring + rows, shares in colors, center totals, tap toggles | 9, 10, 11 |
| E3 tabs Accounts/Categories/Transactions/Overview, More → profile icon top-left | 15 |
| E3 balance, Expenses/Income cards, stacked daily bars (top categories, rest grey), day/week avg, month total, top 3 with % + More… | 12, 13, 14 |
| E4 period swipe with slide, tabs only by tap, vertical scroll intact | 16 |
| E4 animated numbers; ring and bars animate | 17; 10, 14 |
| E4 slide/fade navigation, animated sheets | 18 |
| E4 swipe row edit/delete with undo | 19 |
| E5 header halves with badges, single-line chips, label + animated amount in category color, notes, calculator keypad (÷ × − +, digits, decimal, currency, backspace, calendar, tall confirm), date under keypad | 20, 21 |


