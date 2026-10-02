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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.logic.GetCategoryAmountsBySubcategoryUseCase
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.overview.logic.GetOverviewStatsUseCase
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.HistoryStatsRepository
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod

class CategoryStatsSheetViewModel(
    parameters: Parameters,
    getCategoryAmountsBySubcategoryUseCase: GetCategoryAmountsBySubcategoryUseCase,
    historyStatsRepository: HistoryStatsRepository,
    getOverviewStatsUseCase: GetOverviewStatsUseCase,
) : ViewModel() {

    private val log by lazyLogger("CategoryStatsSheetVM")
    private val viewPeriod = ViewHistoryPeriod.fromHistoryPeriod(parameters.statsPeriod)

    /**
     * Null while loading.
     */
    val stats: StateFlow<ViewCategoryStats?> =
        combine(
            getCategoryAmountsBySubcategoryUseCase(
                categoryId = parameters.categoryId,
                period = parameters.statsPeriod,
            ),
            historyStatsRepository.getCategoryTransferCountFlow(
                categoryId = parameters.categoryId,
                isIncome = parameters.isIncome,
                period = parameters.statsPeriod,
            ),
            getOverviewStatsUseCase(
                period = parameters.statsPeriod,
            ),
        ) { categoryWithAmounts, transferCount, overview ->
            ViewCategoryStats.from(
                categoryWithAmounts = categoryWithAmounts,
                transferCount = transferCount,
                overview = overview,
                period = viewPeriod,
            )
        }
            .map<ViewCategoryStats, ViewCategoryStats?> { it }
            .retryWhen { error, _ ->
                log.error(error) {
                    "stats: failed getting stats of ${parameters.categoryId}"
                }
                emit(null)
                delay(5_000)
                true
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    fun onProceedToTransferClicked() {
        val category = stats.value?.category
            ?: return
        _events.tryEmit(Event.ProceedToTransfer(category))
    }

    fun onTransactionsClicked() {
        val category = stats.value?.category
            ?: return
        _events.tryEmit(
            Event.ProceedToFilteredActivity(
                categoryCounterparty = TransferCounterparty.Category(category),
            )
        )
    }

    sealed interface Event {

        class ProceedToTransfer(
            val category: Category,
        ) : Event

        class ProceedToFilteredActivity(
            val categoryCounterparty: TransferCounterparty.Category,
        ) : Event
    }

    class Parameters(
        val categoryId: String,
        val isIncome: Boolean,
        val statsPeriod: HistoryPeriod,
    )
}
