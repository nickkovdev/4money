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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.overview.logic.GetOverviewStatsUseCase
import ua.com.radiokot.money.transfers.history.view.HistoryStatsPeriodViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewScreenViewModel(
    historyStatsPeriodViewModel: HistoryStatsPeriodViewModel,
    getOverviewStatsUseCase: GetOverviewStatsUseCase,
) : ViewModel(),
    HistoryStatsPeriodViewModel by historyStatsPeriodViewModel {

    private val log by lazyLogger("OverviewScreenVM")
    private val _isIncome: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isIncome = _isIncome.asStateFlow()
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    private val overviewDataFlow =
        historyStatsPeriod
            .flatMapLatest { period ->
                getOverviewStatsUseCase(
                    period = period,
                )
                    .catch { error ->
                        log.error(error) {
                            "overviewDataFlow(): failed getting overview stats for $period"
                        }
                        emit(null)
                    }
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val state: StateFlow<OverviewScreenState> =
        combine(
            overviewDataFlow,
            isIncome,
        ) { data, isIncome ->
            if (data == null)
                OverviewScreenState.NoPrimaryCurrency
            else
                OverviewScreenState.Loaded(
                    ViewOverview.fromData(
                        data = data,
                        isIncome = isIncome,
                    )
                )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, OverviewScreenState.Loading)

    fun onExpensesCardClicked() {
        log.debug { "onExpensesCardClicked(): switching to expenses" }
        _isIncome.value = false
    }

    fun onIncomeCardClicked() {
        log.debug { "onIncomeCardClicked(): switching to income" }
        _isIncome.value = true
    }

    fun onMoreCategoriesClicked() {
        _events.tryEmit(Event.ProceedToCategories)
    }

    sealed interface Event {
        object ProceedToCategories : Event
    }
}
