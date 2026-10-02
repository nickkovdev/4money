/* Copyright 2025 Oleg Koretsky

   This file is part of the 4Money,
   a budget tracking Android app.

   4Money is free software: you can redistribute it
   and/or modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation, either version 3 of the License,
   or (at your option) any later version.

   4Money is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
   See the GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with 4Money. If not, see <http://www.gnu.org/licenses/>.
*/

package ua.com.radiokot.money.theme.view

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import org.koin.compose.koinInject
import ua.com.radiokot.money.privacy.data.PrivacyPreferences
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.theme.data.ThemeMode
import ua.com.radiokot.money.theme.data.ThemePreferences
import ua.com.radiokot.money.uikit.theme.AuroraMoneyColors
import ua.com.radiokot.money.uikit.theme.EmberMoneyColors
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import ua.com.radiokot.money.uikit.theme.PaperMoneyColors

/**
 * [MoneyTheme] with the palette chosen in Settings → Appearance.
 *
 * @param paintWindow whether to paint the window background with the palette,
 * so there is no flash of another color behind the content. Off for translucent activities.
 */
@Composable
fun MoneyAppTheme(
    paintWindow: Boolean = true,
    content: @Composable () -> Unit,
) {
    val themePreferences = koinInject<ThemePreferences>()
    val themeMode by themePreferences.themeMode.collectAsState()
    val colors = moneyColorsOf(
        themeMode = themeMode,
        isSystemDark = isSystemInDarkTheme(),
    )

    if (paintWindow) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                (view.context as? Activity)
                    ?.window
                    ?.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            }
        }
    }

    val privacyPreferences = koinInject<PrivacyPreferences>()
    val isPrivate by privacyPreferences.isPrivacyModeEnabled.collectAsState()

    CompositionLocalProvider(LocalPrivacyMode provides isPrivate) {
        MoneyTheme(
            colors = colors,
            content = content,
        )
    }
}

/**
 * @param isSystemDark the configuration night mode,
 * which AppCompat already overrides for the explicit modes.
 */
fun moneyColorsOf(
    themeMode: ThemeMode,
    isSystemDark: Boolean,
): MoneyColors = when (themeMode) {
    ThemeMode.System ->
        if (isSystemDark)
            MidnightMoneyColors
        else
            PaperMoneyColors

    ThemeMode.Light -> PaperMoneyColors
    ThemeMode.Dark -> MidnightMoneyColors
    ThemeMode.Ember -> EmberMoneyColors
    ThemeMode.Aurora -> AuroraMoneyColors
}
