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

package ua.com.radiokot.money.transfers.history.view

import kotlinx.datetime.LocalDate
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod

class PeriodSwipeTest {

    @Test
    fun resolvePeriodSwipe_Thresholds() {
        Assert.assertNull(resolvePeriodSwipe(totalDragX = 0f, thresholdPx = 100f))
        Assert.assertNull(resolvePeriodSwipe(totalDragX = 99f, thresholdPx = 100f))
        Assert.assertNull(resolvePeriodSwipe(totalDragX = -99f, thresholdPx = 100f))
        Assert.assertEquals(PeriodSwipe.Previous, resolvePeriodSwipe(totalDragX = 100f, thresholdPx = 100f))
        Assert.assertEquals(PeriodSwipe.Previous, resolvePeriodSwipe(totalDragX = 400f, thresholdPx = 100f))
        Assert.assertEquals(PeriodSwipe.Next, resolvePeriodSwipe(totalDragX = -100f, thresholdPx = 100f))
    }

    @Test
    fun periodChangeDirection() {
        val september = HistoryPeriod.Month(LocalDate(2026, 9, 15))

        Assert.assertEquals(1, periodChangeDirection(september, september.getNext()))
        Assert.assertEquals(-1, periodChangeDirection(september, september.getPrevious()))
        Assert.assertEquals(0, periodChangeDirection(september, HistoryPeriod.Month(LocalDate(2026, 9, 1))))
        Assert.assertEquals(-1, periodChangeDirection(september, HistoryPeriod.Since70th))
    }
}
