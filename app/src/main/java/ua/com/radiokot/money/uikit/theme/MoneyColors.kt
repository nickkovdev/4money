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
