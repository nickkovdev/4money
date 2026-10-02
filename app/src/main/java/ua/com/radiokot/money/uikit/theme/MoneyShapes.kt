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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Corner shapes: soft rounded controls, lists in rounded groups.
 */
object MoneyShapes {
    /** Small tiles, swatches. */
    val small: Shape = RoundedCornerShape(12.dp)

    /** Text fields, notes, hint boxes. */
    val medium: Shape = RoundedCornerShape(16.dp)

    /** Keypad keys, transfer sheet header halves. */
    val key: Shape = RoundedCornerShape(18.dp)

    /** List groups, cards. */
    val large: Shape = RoundedCornerShape(20.dp)

    /** Dialogs, swipe cards. */
    val extraLarge: Shape = RoundedCornerShape(28.dp)

    /** Top corners of a bottom sheet. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Buttons, chips, segmented controls. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    val circle: Shape = CircleShape

    /** Tinted tile behind an account or category icon, scales with its size. */
    val itemTile: Shape = RoundedCornerShape(percent = 34)
}

/**
 * Spacing and component sizes.
 */
object MoneySpacing {
    /** Horizontal padding of a screen. */
    val screen = 20.dp

    /** Horizontal padding inside a list row. */
    val rowHorizontal = 16.dp

    /** Vertical padding inside a list row. */
    val rowVertical = 14.dp

    /** Between sections. */
    val section = 20.dp

    /** Between related elements. */
    val gap = 8.dp
    val gapSmall = 4.dp
    val gapLarge = 12.dp

    val iconButton = 44.dp
    val icon = 22.dp
    val iconSmall = 18.dp
    val itemTile = 42.dp
    val keypadKeyHeight = 54.dp
    val buttonHeight = 48.dp
    val chipHeight = 36.dp
}
