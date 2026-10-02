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
import com.composeunstyled.LocalTextStyle

/**
 * Paper by default so previews without [MoneyTheme] look light.
 */
val LocalMoneyColors = staticCompositionLocalOf { PaperMoneyColors }
val LocalMoneyTypography = staticCompositionLocalOf { DefaultMoneyTypography }

/**
 * Provides the tokens. With no [colors] given, picks Paper or Midnight
 * by the configuration's night mode, which AppCompat overrides
 * with the stored [ua.com.radiokot.money.theme.data.ThemeMode], see MoneyApp.initTheme().
 * The app passes the chosen palette, see `MoneyAppTheme`.
 *
 * `com.composeunstyled.Text` and `Icon` read [LocalContentColor] and [LocalTextStyle],
 * so they get the font and the text color without passing them explicitly.
 */
@Composable
fun MoneyTheme(
    colors: MoneyColors =
        if (isSystemInDarkTheme())
            MidnightMoneyColors
        else
            PaperMoneyColors,
    typography: MoneyTypography = DefaultMoneyTypography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMoneyColors provides colors,
        LocalMoneyTypography provides typography,
        LocalContentColor provides colors.ink,
        LocalTextStyle provides typography.body,
        content = content,
    )
}

object MoneyTheme {

    val colors: MoneyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMoneyColors.current

    val typography: MoneyTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMoneyTypography.current

    val shapes: MoneyShapes
        get() = MoneyShapes

    val spacing: MoneySpacing
        get() = MoneySpacing
}
