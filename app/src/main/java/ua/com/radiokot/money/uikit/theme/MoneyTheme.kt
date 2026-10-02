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
