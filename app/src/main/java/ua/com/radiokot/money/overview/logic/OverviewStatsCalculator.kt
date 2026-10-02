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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import java.math.BigInteger

object OverviewStatsCalculator {

    const val MAX_CHART_DAYS = 31
    private val SEVEN = BigInteger.valueOf(7)
    private val HUNDRED = BigInteger.valueOf(100)

    // BigInteger.TWO needs API 33.
    private val TWO = BigInteger.valueOf(2)

    /**
     * @param amountsByCategoryId daily amounts in one currency by category ID
     * @param firstDay first day of the period, inclusive
     * @param lastDay last day of the period, inclusive
     */
    fun calculate(
        amountsByCategoryId: Map<String, Map<LocalDate, BigInteger>>,
        firstDay: LocalDate,
        lastDay: LocalDate,
        today: LocalDate,
        stackCategoryCount: Int = 4,
        topCategoryCount: Int = 3,
    ): OverviewStats {
        val bounds = firstDay..lastDay

        val totalsByCategoryId: Map<String, BigInteger> =
            amountsByCategoryId
                .mapValues { (_, amountsByDay) ->
                    amountsByDay.entries
                        .filter { it.key in bounds }
                        .fold(BigInteger.ZERO) { sum, entry -> sum + entry.value }
                }
                .filterValues { it.signum() > 0 }

        val total = totalsByCategoryId.values.fold(BigInteger.ZERO, BigInteger::add)

        val rankedIds: List<String> =
            totalsByCategoryId.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, BigInteger>> { it.value }
                        .thenBy { it.key }
                )
                .map { it.key }

        val stackIds = rankedIds.take(stackCategoryCount)
        val periodLength = firstDay.daysUntil(lastDay) + 1

        val days: List<OverviewDay> =
            if (periodLength in 1..MAX_CHART_DAYS)
                (0 until periodLength).map { offset ->
                    val date = firstDay.plus(offset, DateTimeUnit.DAY)
                    val dayTotal = totalsByCategoryId.keys.fold(BigInteger.ZERO) { sum, categoryId ->
                        sum + (amountsByCategoryId.getValue(categoryId)[date] ?: BigInteger.ZERO)
                    }
                    val stackSegments = stackIds.map { categoryId ->
                        OverviewDaySegment(
                            categoryId = categoryId,
                            amount = amountsByCategoryId.getValue(categoryId)[date] ?: BigInteger.ZERO,
                        )
                    }
                    val rest = stackSegments.fold(dayTotal) { left, segment -> left - segment.amount }

                    OverviewDay(
                        date = date,
                        total = dayTotal,
                        segments = (stackSegments + OverviewDaySegment(null, rest))
                            .filter { it.amount.signum() > 0 },
                    )
                }
            else
                emptyList()

        val elapsedDays = when {
            today < firstDay -> 0
            today > lastDay -> periodLength
            else -> firstDay.daysUntil(today) + 1
        }
        val dayAverage =
            if (elapsedDays > 0)
                total / BigInteger.valueOf(elapsedDays.toLong())
            else
                BigInteger.ZERO

        return OverviewStats(
            total = total,
            days = days,
            stackCategoryIds = stackIds,
            dayAverage = dayAverage,
            weekAverage = dayAverage * SEVEN,
            topCategories = rankedIds
                .take(topCategoryCount)
                .map { categoryId ->
                    val amount = totalsByCategoryId.getValue(categoryId)
                    OverviewCategoryShare(
                        categoryId = categoryId,
                        amount = amount,
                        percent = percentOf(amount, total),
                    )
                },
            categoryCount = totalsByCategoryId.size,
        )
    }

    /**
     * @return inclusive first and last days to aggregate for the [period].
     * "The entire time" spans from the first day with data to today.
     */
    fun bounds(
        period: HistoryPeriod,
        dataDays: Collection<LocalDate>,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate> = when (period) {
        HistoryPeriod.Since70th ->
            (dataDays.minOrNull() ?: today) to maxOf(today, dataDays.maxOrNull() ?: today)

        else ->
            period.startInclusive.date to period.endExclusive.date.minus(1, DateTimeUnit.DAY)
    }

    /**
     * @return [part] of [total] in percent, rounded half-up, 0 for zero total.
     */
    fun percentOf(part: BigInteger, total: BigInteger): Int =
        if (total.signum() == 0)
            0
        else
            ((part * HUNDRED + total / TWO) / total).toInt()
}
