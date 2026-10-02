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
