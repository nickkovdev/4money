/* Copyright 2026 Nikita Kovalovs

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

package ua.com.radiokot.money.preferences.view

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UnsentChangesWarningTest {

    private fun warningFor(count: suspend () -> Int) = runBlocking {
        UnsentChangesWarning.check(count)
    }

    @Test
    fun noUnsentChangesMeansNoWarning() {
        assertEquals(UnsentChangesWarning.None, warningFor { 0 })
    }

    @Test
    fun unsentChangesAreCounted() {
        assertEquals(UnsentChangesWarning.Count(3), warningFor { 3 })
    }

    @Test
    fun failedCheckStillWarns() {
        assertEquals(UnsentChangesWarning.Unknown, warningFor { error("database is closed") })
    }
}
