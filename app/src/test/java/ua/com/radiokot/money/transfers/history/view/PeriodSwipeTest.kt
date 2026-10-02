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
