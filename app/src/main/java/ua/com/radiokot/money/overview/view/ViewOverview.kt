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

package ua.com.radiokot.money.overview.view

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.overview.logic.OverviewCategoryShare
import ua.com.radiokot.money.overview.logic.OverviewData
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import java.math.BigInteger

@Immutable
sealed interface OverviewScreenState {
    data object Loading : OverviewScreenState
    data object NoPrimaryCurrency : OverviewScreenState
    class Loaded(val overview: ViewOverview) : OverviewScreenState
}

@Immutable
class ViewOverview(
    val isIncome: Boolean,
    val isMonth: Boolean,
    val balance: ViewAmount,
    val expenseTotal: ViewAmount,
    val incomeTotal: ViewAmount,
    val bars: List<ViewOverviewBar>,
    val dayAverage: ViewAmount,
    val weekAverage: ViewAmount,
    val periodTotal: ViewAmount,
    val topCategories: List<ViewOverviewTopCategory>,
    /**
     * Every category with a positive total, shown when expanded.
     */
    val allCategories: List<ViewOverviewTopCategory>,
    val hasMoreCategories: Boolean,
    /**
     * Changes when the chart must re-animate: period or mode.
     */
    val animationKey: Any,
) {
    companion object {
        fun fromData(
            data: OverviewData,
            isIncome: Boolean,
        ): ViewOverview {
            val currency = ViewCurrency(data.primaryCurrency)
            fun amount(value: BigInteger) = ViewAmount(value = value, currency = currency)
            val stats =
                if (isIncome)
                    data.income
                else
                    data.expense
            fun toTopCategory(share: OverviewCategoryShare): ViewOverviewTopCategory? {
                val category = data.categoriesById[share.categoryId]
                    ?: return null

                return ViewOverviewTopCategory(
                    key = category.id,
                    title = category.title,
                    colorScheme = category.colorScheme,
                    icon = category.icon,
                    amount = amount(share.amount),
                    percent = share.percent,
                )
            }

            return ViewOverview(
                isIncome = isIncome,
                isMonth = data.period is HistoryPeriod.Month,
                balance = amount(data.income.total - data.expense.total),
                expenseTotal = amount(data.expense.total),
                incomeTotal = amount(data.income.total),
                bars = stats.days.map { day ->
                    ViewOverviewBar(
                        dayOfMonth = day.date.day,
                        segments = day.segments.map { segment ->
                            ViewOverviewBarSegment(
                                colorScheme = segment.categoryId
                                    ?.let(data.categoriesById::get)
                                    ?.colorScheme,
                                value = segment.amount.toFloat(),
                            )
                        },
                    )
                },
                dayAverage = amount(stats.dayAverage),
                weekAverage = amount(stats.weekAverage),
                periodTotal = amount(stats.total),
                topCategories = stats.topCategories.mapNotNull(::toTopCategory),
                allCategories = stats.categories.mapNotNull(::toTopCategory),
                hasMoreCategories = stats.categoryCount > stats.topCategories.size,
                animationKey = data.period.startInclusive to isIncome,
            )
        }
    }
}

@Immutable
class ViewOverviewBar(
    val dayOfMonth: Int,
    val segments: List<ViewOverviewBarSegment>,
)

@Immutable
class ViewOverviewBarSegment(
    /**
     * Null for the rest of the categories.
     */
    val colorScheme: ItemColorScheme?,
    val value: Float,
)

@Immutable
class ViewOverviewTopCategory(
    val key: String,
    val title: String,
    val colorScheme: ItemColorScheme,
    val icon: ItemIcon?,
    val amount: ViewAmount,
    val percent: Int,
)
