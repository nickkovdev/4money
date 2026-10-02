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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.isSameDayAs
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.map
import ua.com.radiokot.money.transfers.data.Transfer
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.history.data.HistoryStatsRepository
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository
import ua.com.radiokot.money.transfers.logic.RevertTransferUseCase
import ua.com.radiokot.money.transfers.view.ViewDate
import ua.com.radiokot.money.transfers.view.ViewTransferListItem
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DELETION_UNDO_TIMEOUT_MS = 4000L

// Look mum, I'm an experimentator 🤦🏻
@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalTime::class,
)
class ActivityViewModel(
    historyStatsPeriodViewModel: HistoryStatsPeriodViewModel,
    private val activityFilterViewModelDelegate: ActivityFilterViewModelDelegate,
    private val transferHistoryRepository: TransferHistoryRepository,
    private val historyStatsRepository: HistoryStatsRepository,
    private val revertTransferUseCase: RevertTransferUseCase,
) : ViewModel(),
    HistoryStatsPeriodViewModel by historyStatsPeriodViewModel,
    ActivityFilterViewModel by activityFilterViewModelDelegate {

    private val log by lazyLogger("ActivityVM")
    private val localTimeZone = TimeZone.currentSystemDefault()
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()
    val isBackHandlerEnabled: StateFlow<Boolean> =
        activityFilterViewModelDelegate.activityFilterTransferCounterparties
            .map(viewModelScope) { counterparties ->
                !counterparties.isNullOrEmpty()
            }

    private val transferHistoryPagerFlow: Flow<Pager<*, Transfer>> =
        combine(
            historyStatsPeriod,
            activityFilterViewModelDelegate.activityFilterTransferCounterparties,
            transform = ::Pair
        )
            .mapLatest { (period, counterparties) ->
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                    ),
                    pagingSourceFactory =
                        {
                            transferHistoryRepository.getTransferHistoryPagingSource(
                                withinPeriod = period,
                                counterpartyIds = counterparties
                                    ?.mapTo(mutableSetOf()) { counterparty ->
                                        counterparty.id.toString()
                                    },
                            )
                        },
                )
            }

    private val separatedTransferItemPagingFlow: Flow<PagingData<ViewTransferListItem>> =
        transferHistoryPagerFlow
            .flatMapLatest { it.flow }
            .map { pagingData ->
                val today = Clock.System.now().toLocalDateTime(localTimeZone).date
                val yesterday = today.minus(1, DateTimeUnit.DAY)

                pagingData
                    .map<Transfer, Pair<ViewTransferListItem, LocalDate>> { transfer ->
                        val transferListItem =
                            ViewTransferListItem.Transfer.fromTransfer(transfer)
                        transferListItem to transfer.dateTime.date
                    }
                    .insertSeparators { previousItemDatePair, nextItemDatePair ->
                        val previousLocalDate = previousItemDatePair?.second
                        val nextLocalDate = nextItemDatePair?.second

                        if (nextLocalDate != null &&
                            (previousLocalDate == null || !nextLocalDate.isSameDayAs(
                                previousLocalDate
                            ))
                        ) {
                            val header = ViewTransferListItem.Header(
                                date = ViewDate(
                                    localDate = nextLocalDate,
                                    today = today,
                                    yesterday = yesterday,
                                ),
                            )

                            header to nextLocalDate
                        } else {
                            null
                        }
                    }
                    .map { it.first }
            }
            .flowOn(Dispatchers.Default)
            .cachedIn(viewModelScope)

    private val pendingDeletions = PendingTransferDeletions()
    private val deletionUndoJobs = mutableMapOf<String, Job>()
    private val undoableDeletionTransferId: MutableStateFlow<String?> = MutableStateFlow(null)

    val isUndoDeletionVisible: StateFlow<Boolean> =
        undoableDeletionTransferId
            .map(viewModelScope) { it != null }

    val transferItemPagingFlow: Flow<PagingData<ViewTransferListItem>> =
        combine(
            separatedTransferItemPagingFlow,
            pendingDeletions.hiddenIdsFlow,
        ) { pagingData, hiddenIds ->
            if (hiddenIds.isEmpty())
                pagingData
            else
                pagingData.filter { item ->
                    item !is ViewTransferListItem.Transfer
                            || item.source?.id !in hiddenIds
                }
        }

    val totalIncomeAndExpense: StateFlow<ViewTotalIncomeAndExpense?> =
        combine(
            historyStatsPeriod,
            activityFilterViewModelDelegate.activityFilterTransferCounterparties,
            transform = ::Pair
        )
            .flatMapLatest { (period, counterparties) ->
                val accountCounterparty =
                    counterparties
                        ?.first() as? TransferCounterparty.Account
                        ?: return@flatMapLatest flowOf(null)

                historyStatsRepository
                    .getAccountTotalIncomeAndExpense(
                        accountId = accountCounterparty.id.toString(),
                        period = period,
                    )
                    .map { incomeAndExpense ->
                        ViewTotalIncomeAndExpense(
                            income = incomeAndExpense.income,
                            expense = incomeAndExpense.expense,
                            currency = ViewCurrency(accountCounterparty.account.currency),
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onTransferItemClicked(item: ViewTransferListItem.Transfer) {
        val transfer = item.source
        if (transfer == null) {
            log.debug {
                "onTransferItemClicked(): missing transfer source"
            }
            return
        }

        _events.tryEmit(
            Event.ProceedToEditingTransfer(
                transferToEdit = transfer,
            )
        )
    }

    fun onTransferItemLongClicked(item: ViewTransferListItem.Transfer) {
        val transfer = item.source
        if (transfer == null) {
            log.debug {
                "onTransferItemLongClicked(): missing transfer source"
            }
            return
        }

        _events.tryEmit(
            Event.ProceedToRevertingTransferConfirmation(
                transferToRevertId = transfer.id,
            )
        )
    }

    fun onTransferRevertConfirmed(
        transferToRevertId: String,
    ) {
        revertTransfer(
            id = transferToRevertId,
        )
    }

    private var revertTransferJob: Job? = null
    private fun revertTransfer(id: String) {
        revertTransferJob?.cancel()
        revertTransferJob = viewModelScope.launch {
            log.debug {
                "revertTransfer(): reverting:" +
                        "\nid=$id"
            }

            revertTransferUseCase(
                transferId = id,
            )
                .onFailure { error ->
                    log.error(error) {
                        "revertTransfer(): failed to revert transfer"
                    }
                }
                .onSuccess {
                    log.info {
                        "Reverted transfer $id"
                    }

                    log.debug {
                        "revertTransfer(): transfer reverted"
                    }
                }
        }
    }

    fun onTransferItemDeleteClicked(item: ViewTransferListItem.Transfer) {
        val transferId = item.source?.id
        if (transferId == null) {
            log.debug { "onTransferItemDeleteClicked(): missing transfer source" }
            return
        }

        if (!pendingDeletions.schedule(transferId)) {
            return
        }

        log.debug {
            "onTransferItemDeleteClicked(): scheduled deletion:" +
                    "\ntransferId=$transferId"
        }

        // Only the latest deletion can be undone from the bar,
        // an older pending one gets committed right away.
        undoableDeletionTransferId.value
            ?.also { previousTransferId ->
                deletionUndoJobs.remove(previousTransferId)?.cancel()
                commitDeletion(previousTransferId)
            }
        undoableDeletionTransferId.value = transferId

        deletionUndoJobs[transferId] = viewModelScope.launch {
            delay(DELETION_UNDO_TIMEOUT_MS)
            if (undoableDeletionTransferId.value == transferId) {
                undoableDeletionTransferId.value = null
            }
            commitDeletion(transferId)
        }
    }

    fun onUndoDeletionClicked() {
        val transferId = undoableDeletionTransferId.value
            ?: return
        undoableDeletionTransferId.value = null

        if (pendingDeletions.undo(transferId)) {
            deletionUndoJobs.remove(transferId)?.cancel()

            log.debug {
                "onUndoDeletionClicked(): undone:" +
                        "\ntransferId=$transferId"
            }
        }
    }

    /**
     * Commits the deletion exactly once: [PendingTransferDeletions.take]
     * guards against the timer, the undo and [onCleared] racing each other.
     */
    private fun commitDeletion(transferId: String) {
        if (!pendingDeletions.take(transferId)) {
            return
        }
        deletionUndoJobs.remove(transferId)

        revertTakenDeletion(transferId)
    }

    // The app-lifetime scope is needed so the revert survives the cleared ViewModel,
    // there is no application-level scope in the project to use instead.
    @OptIn(DelicateCoroutinesApi::class)
    private fun revertTakenDeletion(transferId: String) {
        GlobalScope.launch {
            // Leaving the screen mid-revert must not cancel it.
            withContext(NonCancellable) {
                revertTransferUseCase(
                    transferId = transferId,
                )
                    .onSuccess {
                        log.info { "Deleted (reverted) transfer $transferId" }
                    }
                    .onFailure { error ->
                        log.error(error) { "commitDeletion(): failed to revert transfer" }
                        pendingDeletions.restore(transferId)
                    }
            }
        }
    }

    override fun onCleared() {
        // Leaving the screen commits what is still pending.
        pendingDeletions.takeAll().forEach(::revertTakenDeletion)
        super.onCleared()
    }

    fun onBack() {

        if (!isBackHandlerEnabled.value) {
            log.warn {
                "onBack(): ignoring as handler is disabled"
            }
            return
        }

        activityFilterViewModelDelegate
            .activityFilterTransferCounterparties
            .value
            ?.forEach(activityFilterViewModelDelegate::removeCounterpartyFromActivityFilter)
    }

    sealed interface Event {

        class ProceedToEditingTransfer(
            val transferToEdit: Transfer,
        ) : Event

        /**
         * Pass the confirmation to [onTransferRevertConfirmed].
         */
        class ProceedToRevertingTransferConfirmation(
            val transferToRevertId: String,
        ) : Event
    }
}
