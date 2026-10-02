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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.currency.data.CurrencyPairMap
import ua.com.radiokot.money.currency.data.CurrencyPreferences
import ua.com.radiokot.money.currency.data.CurrencyPriceRepository
import ua.com.radiokot.money.currency.data.CurrencyRepository
import ua.com.radiokot.money.currency.logic.convertDailyAmount
import ua.com.radiokot.money.transfers.history.data.DailyAmountsByCategoryId
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.HistoryStatsRepository
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OverviewData(
    val primaryCurrency: Currency,
    val period: HistoryPeriod,
    val expense: OverviewStats,
    val income: OverviewStats,
    val categoriesById: Map<String, Category>,
)

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class GetOverviewStatsUseCase(
    private val currencyPreferences: CurrencyPreferences,
    private val currencyRepository: CurrencyRepository,
    private val currencyPriceRepository: CurrencyPriceRepository,
    private val categoryRepository: CategoryRepository,
    private val historyStatsRepository: HistoryStatsRepository,
) {

    /**
     * @return expense and income stats for the [period] in the primary currency,
     * or null if the primary currency doesn't exist.
     */
    operator fun invoke(
        period: HistoryPeriod,
    ): Flow<OverviewData?> =
        combine(
            currencyPreferences
                .primaryCurrencyCode
                .mapLatest(currencyRepository::getCurrencyByCode),
            categoryRepository.getCategoriesFlow(isIncome = false),
            categoryRepository.getCategoriesFlow(isIncome = true),
            historyStatsRepository.getCategoryDailyAmountsFlow(isIncome = false, period = period),
            historyStatsRepository.getCategoryDailyAmountsFlow(isIncome = true, period = period),
        ) { primaryCurrency, expenseCategories, incomeCategories, expenseDailyAmounts, incomeDailyAmounts ->
            Input(
                primaryCurrency = primaryCurrency,
                categoriesById = (expenseCategories + incomeCategories).associateBy(Category::id),
                expenseDailyAmounts = expenseDailyAmounts,
                incomeDailyAmounts = incomeDailyAmounts,
            )
        }
            .mapLatest { input ->
                val primaryCurrency = input.primaryCurrency
                    ?: return@mapLatest null

                val today = Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date

                val dailyPrices: Map<String, CurrencyPairMap> =
                    currencyPriceRepository.getDailyPrices(
                        period = period,
                        currencyCodes = input.categoriesById.values
                            .mapTo(mutableSetOf(primaryCurrency.code)) { it.currency.code },
                    )

                fun toPrimaryCurrency(
                    dailyAmounts: DailyAmountsByCategoryId,
                ): Map<String, Map<LocalDate, BigInteger>> =
                    dailyAmounts.entries
                        .mapNotNull { (categoryId, amountsByDay) ->
                            val category = input.categoriesById[categoryId]
                                ?: return@mapNotNull null

                            categoryId to amountsByDay.entries.associate { (dayString, amount) ->
                                LocalDate.parse(dayString, LocalDate.Formats.ISO) to
                                        (convertDailyAmount(
                                            dayString = dayString,
                                            amount = amount,
                                            base = category.currency,
                                            quote = primaryCurrency,
                                            dailyPrices = dailyPrices,
                                        ) ?: BigInteger.ZERO)
                            }
                        }
                        .toMap()

                val expense = toPrimaryCurrency(input.expenseDailyAmounts)
                val income = toPrimaryCurrency(input.incomeDailyAmounts)
                val (firstDay, lastDay) = OverviewStatsCalculator.bounds(
                    period = period,
                    dataDays = (expense.values + income.values).flatMap { it.keys },
                    today = today,
                )

                OverviewData(
                    primaryCurrency = primaryCurrency,
                    period = period,
                    expense = OverviewStatsCalculator.calculate(expense, firstDay, lastDay, today),
                    income = OverviewStatsCalculator.calculate(income, firstDay, lastDay, today),
                    categoriesById = input.categoriesById,
                )
            }
            .flowOn(Dispatchers.Default)

    private class Input(
        val primaryCurrency: Currency?,
        val categoriesById: Map<String, Category>,
        val expenseDailyAmounts: DailyAmountsByCategoryId,
        val incomeDailyAmounts: DailyAmountsByCategoryId,
    )
}
