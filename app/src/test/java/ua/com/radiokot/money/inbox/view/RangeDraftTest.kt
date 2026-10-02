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

package ua.com.radiokot.money.inbox.view

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.data.AmountRange
import java.math.BigDecimal

class RangeDraftTest {

    @Test
    fun validRanges() {
        Assert.assertEquals(
            AmountRange(min = BigDecimal("10"), max = BigDecimal("35.5")),
            parseRangeDraft("10", "35,5").getOrThrow(),
        )
        Assert.assertEquals(
            AmountRange(min = null, max = BigDecimal("10")),
            parseRangeDraft("", "10").getOrThrow(),
        )
        Assert.assertEquals(
            AmountRange(min = BigDecimal("35"), max = null),
            parseRangeDraft(" 35 ", "").getOrThrow(),
        )
    }

    @Test
    fun invalidRanges() {
        listOf(
            "" to "",
            "abc" to "",
            "20" to "10",
            "10" to "10",
            "-1" to "",
        ).forEach { (from, under) ->
            Assert.assertTrue("$from..$under", parseRangeDraft(from, under).isFailure)
        }
    }
}
