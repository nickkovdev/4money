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

package ua.com.radiokot.money.overview.logic

import kotlinx.datetime.LocalDate
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import java.math.BigInteger

class OverviewStatsCalculatorTest {

    private fun d(day: Int, month: Int = 9) = LocalDate(2026, month, day)
    private fun bi(value: Long) = BigInteger.valueOf(value)

    private val september = mapOf(
        "food" to mapOf(d(1) to bi(1000), d(2) to bi(500)),
        "car" to mapOf(d(1) to bi(3000)),
        "fun" to mapOf(d(3) to bi(200)),
        "gift" to mapOf(d(3) to bi(100)),
        "misc" to mapOf(d(2) to bi(50), d(31, month = 8) to bi(999_999)),
    )

    private fun calculateSeptember(today: LocalDate) =
        OverviewStatsCalculator.calculate(
            amountsByCategoryId = september,
            firstDay = d(1),
            lastDay = d(30),
            today = today,
            stackCategoryCount = 2,
            topCategoryCount = 3,
        )

    @Test
    fun totals_IgnoreDaysOutsideBounds() {
        val stats = calculateSeptember(today = d(10))
        Assert.assertEquals(bi(4850), stats.total)
        Assert.assertEquals(5, stats.categoryCount)
    }

    @Test
    fun days_StackedByTopCategories_RestGrouped() {
        val stats = calculateSeptember(today = d(10))

        Assert.assertEquals(listOf("car", "food"), stats.stackCategoryIds)
        Assert.assertEquals(30, stats.days.size)
        Assert.assertEquals(
            OverviewDay(d(1), bi(4000), listOf(OverviewDaySegment("car", bi(3000)), OverviewDaySegment("food", bi(1000)))),
            stats.days[0],
        )
        Assert.assertEquals(
            OverviewDay(d(2), bi(550), listOf(OverviewDaySegment("food", bi(500)), OverviewDaySegment(null, bi(50)))),
            stats.days[1],
        )
        Assert.assertEquals(
            OverviewDay(d(3), bi(300), listOf(OverviewDaySegment(null, bi(300)))),
            stats.days[2],
        )
        Assert.assertEquals(OverviewDay(d(4), bi(0), emptyList()), stats.days[3])
        Assert.assertEquals(d(30), stats.days.last().date)
    }

    @Test
    fun averages_CurrentPeriod_UseElapsedDays() {
        val stats = calculateSeptember(today = d(10))
        Assert.assertEquals(bi(485), stats.dayAverage)
        Assert.assertEquals(bi(3395), stats.weekAverage)
    }

    @Test
    fun averages_FirstDaysOfPeriod_WeekAverageIsCappedByTotal() {
        val stats = calculateSeptember(today = d(2))
        Assert.assertEquals(stats.total, stats.weekAverage)
        Assert.assertTrue(stats.dayAverage * BigInteger.valueOf(7) > stats.total)
    }

    @Test
    fun averages_PastPeriod_UseAllDays() {
        val stats = calculateSeptember(today = d(5, month = 10))
        Assert.assertEquals(bi(161), stats.dayAverage)
        Assert.assertEquals(bi(1127), stats.weekAverage)
    }

    @Test
    fun averages_FuturePeriod_AreZero() {
        val stats = calculateSeptember(today = d(20, month = 8))
        Assert.assertEquals(bi(0), stats.dayAverage)
        Assert.assertEquals(bi(0), stats.weekAverage)
    }

    @Test
    fun topCategories_WithRoundedPercent() {
        Assert.assertEquals(
            listOf(
                OverviewCategoryShare("car", bi(3000), 62),
                OverviewCategoryShare("food", bi(1500), 31),
                OverviewCategoryShare("fun", bi(200), 4),
            ),
            calculateSeptember(today = d(10)).topCategories,
        )
    }

    @Test
    fun ties_AreOrderedById() {
        val stats = OverviewStatsCalculator.calculate(
            amountsByCategoryId = mapOf("b" to mapOf(d(1) to bi(10)), "a" to mapOf(d(1) to bi(10))),
            firstDay = d(1),
            lastDay = d(30),
            today = d(10),
        )
        Assert.assertEquals(listOf("a", "b"), stats.stackCategoryIds)
    }

    @Test
    fun empty() {
        val stats = OverviewStatsCalculator.calculate(emptyMap(), d(1), d(30), d(10))
        Assert.assertEquals(bi(0), stats.total)
        Assert.assertEquals(emptyList<OverviewCategoryShare>(), stats.topCategories)
        Assert.assertEquals(30, stats.days.size)
        Assert.assertTrue(stats.days.all { it.segments.isEmpty() })
        Assert.assertEquals(bi(0), stats.dayAverage)
    }

    @Test
    fun longRange_HasNoBars() {
        val stats = OverviewStatsCalculator.calculate(september, d(1), d(2, month = 10), d(2, month = 10))
        Assert.assertEquals(emptyList<OverviewDay>(), stats.days)
        Assert.assertEquals(bi(4850 / 32), stats.dayAverage)
    }

    @Test
    fun bounds_Month() {
        Assert.assertEquals(
            d(1) to d(30),
            OverviewStatsCalculator.bounds(HistoryPeriod.Month(d(15)), emptyList(), d(10)),
        )
    }

    @Test
    fun bounds_EntireTime_FromFirstDataDayToToday() {
        Assert.assertEquals(
            d(3) to d(2, month = 10),
            OverviewStatsCalculator.bounds(HistoryPeriod.Since70th, listOf(d(5), d(3)), d(2, month = 10)),
        )
        Assert.assertEquals(
            d(2, month = 10) to d(2, month = 10),
            OverviewStatsCalculator.bounds(HistoryPeriod.Since70th, emptyList(), d(2, month = 10)),
        )
    }

    @Test
    fun percentOf() {
        Assert.assertEquals(0, OverviewStatsCalculator.percentOf(bi(5), bi(0)))
        Assert.assertEquals(50, OverviewStatsCalculator.percentOf(bi(1), bi(2)))
        Assert.assertEquals(33, OverviewStatsCalculator.percentOf(bi(1), bi(3)))
        Assert.assertEquals(67, OverviewStatsCalculator.percentOf(bi(2), bi(3)))
    }

    @Test
    fun `categories lists every positive category ranked, top is its head`() {
        val d = LocalDate(2026, 10, 1)
        val stats = OverviewStatsCalculator.calculate(
            amountsByCategoryId = mapOf(
                "a" to mapOf(d to BigInteger.valueOf(10)),
                "b" to mapOf(d to BigInteger.valueOf(40)),
                "c" to mapOf(d to BigInteger.valueOf(30)),
                "d" to mapOf(d to BigInteger.valueOf(20)),
                "z" to mapOf(d to BigInteger.ZERO),
            ),
            firstDay = d,
            lastDay = d,
            today = d,
        )
        Assert.assertEquals(listOf("b", "c", "d", "a"), stats.categories.map { it.categoryId })
        Assert.assertEquals(listOf(40, 30, 20, 10), stats.categories.map { it.percent })
        Assert.assertEquals(stats.categories.take(3), stats.topCategories)
    }
}
