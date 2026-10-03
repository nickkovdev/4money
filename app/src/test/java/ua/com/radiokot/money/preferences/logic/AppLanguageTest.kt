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

package ua.com.radiokot.money.preferences.logic

import org.junit.Assert
import org.junit.Test

class AppLanguageTest {

    @Test
    fun fromTags_Empty_IsSystem() {
        Assert.assertEquals(AppLanguage.System, AppLanguage.fromTags(""))
    }

    @Test
    fun fromTags_SupportedLanguages() {
        Assert.assertEquals(AppLanguage.English, AppLanguage.fromTags("en"))
        Assert.assertEquals(AppLanguage.English, AppLanguage.fromTags("en-GB"))
        Assert.assertEquals(AppLanguage.Russian, AppLanguage.fromTags("ru"))
        Assert.assertEquals(AppLanguage.Russian, AppLanguage.fromTags("ru-RU,en"))
    }

    @Test
    fun fromTags_UnsupportedLanguage_IsSystem() {
        Assert.assertEquals(AppLanguage.System, AppLanguage.fromTags("lv"))
    }

    @Test
    fun fromTags_OnlyFirstTagCounts() {
        Assert.assertEquals(AppLanguage.System, AppLanguage.fromTags("uk,ru"))
    }
}
