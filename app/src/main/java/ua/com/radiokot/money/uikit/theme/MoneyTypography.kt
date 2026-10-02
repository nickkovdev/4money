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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ua.com.radiokot.money.R

/**
 * Onest (SIL Open Font License 1.1, see assets/licenses/onest-OFL.txt):
 * one variable font file, instanced per weight.
 */
val OnestFontFamily: FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(
            resId = R.font.onest,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
            ),
        )
    }
)

/**
 * The type scale. Amount-bearing styles use tabular figures,
 * so digits line up in columns and do not jump while animating.
 */
@Immutable
class MoneyTypography(
    /** The total balance, the amount being typed. */
    val display: TextStyle,
    /** The big amount on an inbox card. */
    val amountLarge: TextStyle,
    /** Screen titles. */
    val headline: TextStyle,
    /** Sheet and dialog titles, large row titles. */
    val title: TextStyle,
    /** Period bar, small titles. */
    val titleSmall: TextStyle,
    /** Row titles and row amounts. */
    val bodyStrong: TextStyle,
    /** Regular text. */
    val body: TextStyle,
    /** Buttons, chips, segments. */
    val label: TextStyle,
    /** Secondary lines at the label size. */
    val labelRegular: TextStyle,
    /** Row subtitles, hints. */
    val caption: TextStyle,
    /** Section headers, shown upper-cased. */
    val overline: TextStyle,
    /** Bottom bar labels, tiny captions. */
    val small: TextStyle,
)

private const val TABULAR_FIGURES = "tnum"

val DefaultMoneyTypography = MoneyTypography(
    display = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountLarge = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    headline = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.01).em,
    ),
    title = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleSmall = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyStrong = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    body = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    label = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelRegular = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    caption = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    overline = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.06.em,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    small = TextStyle(
        fontFamily = OnestFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)
