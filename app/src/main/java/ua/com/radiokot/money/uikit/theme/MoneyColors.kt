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
import androidx.compose.ui.graphics.Color

/**
 * Color tokens of one palette. A theme is only a token set:
 * every component looks the same in every palette.
 *
 * The first block holds the design tokens, the second one
 * keeps the names the older screens use, derived from the first.
 */
@Immutable
class MoneyColors(
    val name: String,
    val isDark: Boolean,
    /** Screen background (window). */
    val background: Color,
    /** Cards, list groups, tonal buttons, keypad digit keys. */
    val surface: Color,
    /** Raised surfaces: selected segment, operator keys, dialogs, top card. */
    val surface2: Color,
    /** Hairlines between rows, sheet handle. */
    val line: Color,
    /** Behind modal sheets and dialogs. */
    val scrim: Color,
    /** Primary text and icons. */
    val ink: Color,
    /** Secondary text. */
    val ink2: Color,
    /** Tertiary text: captions, section headers, placeholders. */
    val ink3: Color,
    val accent: Color,
    val onAccent: Color,
    /** Selected chips, the bottom bar pill, tonal accent surfaces. */
    val accentTint: Color,
    val income: Color,
    val incomeTint: Color,
    val expense: Color,
    val expenseTint: Color,
    val warning: Color,
    /**
     * Alpha of the tinted tile behind an item (account, category) icon,
     * the icon itself is drawn with the item accent color.
     */
    val itemTintAlpha: Float,
) {
    // Older names, kept so the screens migrate one by one.

    val surfaceVariant: Color get() = surface2
    val onBackground: Color get() = ink
    val onBackgroundSecondary: Color get() = ink2
    val outline: Color get() = ink3
    val outlineDisabled: Color get() = line
    val divider: Color get() = line

    /** Zero amounts and account-to-account transfers. */
    val neutralAmount: Color get() = ink

    val bottomBar: Color get() = surface
    val bottomBarIndicator: Color get() = accentTint

    /** Notice dot. */
    val notice: Color get() = expense
    val onWarning: Color get() = onAccent
    val tooltip: Color get() = surface2
    val onTooltip: Color get() = ink
    val actionSheet: Color get() = background
    val selection: Color get() = accentTint
    val selectionIdle: Color get() = surface

    /** "Rest" segments in charts, empty ring track. */
    val chartOther: Color get() = surface2

    override fun toString(): String = "MoneyColors($name)"
}

/**
 * Dark, cool near-black with a periwinkle accent. The default dark palette.
 */
val MidnightMoneyColors = MoneyColors(
    name = "Midnight",
    isDark = true,
    background = Color(0xFF0E0F13),
    surface = Color(0xFF181A21),
    surface2 = Color(0xFF22252E),
    line = Color(0xFF2C303A),
    scrim = Color(0xB307080B),
    ink = Color(0xFFF2F3F5),
    ink2 = Color(0xFFA6ABB7),
    ink3 = Color(0xFF878D9B),
    accent = Color(0xFF93ABFF),
    onAccent = Color(0xFF0B0D14),
    accentTint = Color(0x2993ABFF),
    income = Color(0xFF4FDDA0),
    incomeTint = Color(0x1F4FDDA0),
    expense = Color(0xFFFF8A70),
    expenseTint = Color(0x1FFF8A70),
    warning = Color(0xFFF6BC45),
    itemTintAlpha = 0x29 / 255f,
)

/**
 * Dark, warm brown-black with an amber accent.
 */
val EmberMoneyColors = MoneyColors(
    name = "Ember",
    isDark = true,
    background = Color(0xFF15110E),
    surface = Color(0xFF201A16),
    surface2 = Color(0xFF2B231D),
    line = Color(0xFF3A2F26),
    scrim = Color(0xB30A0806),
    ink = Color(0xFFF7EFE6),
    ink2 = Color(0xFFC2B3A3),
    ink3 = Color(0xFFA08F7F),
    accent = Color(0xFFF5A524),
    onAccent = Color(0xFF1A1206),
    accentTint = Color(0x29F5A524),
    income = Color(0xFFA8DC72),
    incomeTint = Color(0x1FA8DC72),
    expense = Color(0xFFFF7F61),
    expenseTint = Color(0x1FFF7F61),
    warning = Color(0xFFE8C26A),
    itemTintAlpha = 0x2E / 255f,
)

/**
 * Light, warm paper with an ink-blue accent. The light palette.
 */
val PaperMoneyColors = MoneyColors(
    name = "Paper",
    isDark = false,
    background = Color(0xFFF5F3EE),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECE8E0),
    line = Color(0xFFDDD8CE),
    scrim = Color(0x8C5E5A52),
    ink = Color(0xFF17171B),
    ink2 = Color(0xFF55565E),
    ink3 = Color(0xFF6B6C74),
    accent = Color(0xFF2456D3),
    onAccent = Color(0xFFFFFFFF),
    accentTint = Color(0x1A2456D3),
    income = Color(0xFF137A4C),
    incomeTint = Color(0x14137A4C),
    expense = Color(0xFFBF4310),
    expenseTint = Color(0x14BF4310),
    warning = Color(0xFFA86A00),
    itemTintAlpha = 0x1C / 255f,
)

/**
 * Dark, deep teal-navy with a mint accent.
 */
val AuroraMoneyColors = MoneyColors(
    name = "Aurora",
    isDark = true,
    background = Color(0xFF0A181C),
    surface = Color(0xFF112429),
    surface2 = Color(0xFF183138),
    line = Color(0xFF1F3D45),
    scrim = Color(0xB3050D10),
    ink = Color(0xFFE8F4F3),
    ink2 = Color(0xFFA2C0BF),
    ink3 = Color(0xFF82A3A2),
    accent = Color(0xFF5EEAD4),
    onAccent = Color(0xFF04201C),
    accentTint = Color(0x245EEAD4),
    income = Color(0xFF7FE0A8),
    incomeTint = Color(0x1F7FE0A8),
    expense = Color(0xFFFF9580),
    expenseTint = Color(0x1FFF9580),
    warning = Color(0xFFF2C55C),
    itemTintAlpha = 0x29 / 255f,
)
