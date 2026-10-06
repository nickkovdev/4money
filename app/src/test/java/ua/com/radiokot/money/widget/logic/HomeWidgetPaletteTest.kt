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

package ua.com.radiokot.money.widget.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.com.radiokot.money.theme.data.ThemeMode
import ua.com.radiokot.money.uikit.theme.AuroraMoneyColors
import ua.com.radiokot.money.uikit.theme.EmberMoneyColors
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.PaperMoneyColors

class HomeWidgetPaletteTest {
    @Test
    fun systemFollowsNightMode() {
        val palette = HomeWidgetPalette.of(ThemeMode.System)
        assertEquals(PaperMoneyColors.income, palette.day.income)
        assertEquals(MidnightMoneyColors.income, palette.night.income)
    }

    @Test
    fun explicitModeIsFixed() {
        val palette = HomeWidgetPalette.of(ThemeMode.Ember)
        assertEquals(palette.day, palette.night)
        assertEquals(EmberMoneyColors.expense, palette.day.expense)
        assertEquals(EmberMoneyColors.onAccent, palette.day.onButton)
        assertEquals(EmberMoneyColors.accent, palette.day.badge)
    }

    @Test
    fun lightIsPaperBoth() {
        val palette = HomeWidgetPalette.of(ThemeMode.Light)
        assertEquals(PaperMoneyColors.income, palette.night.income)
    }
}
