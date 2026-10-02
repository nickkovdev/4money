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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.logic.CardAccountResolver
import ua.com.radiokot.money.inbox.logic.InboxTransferPrefill
import ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.transfers.view.TransferSheetRoute

/**
 * Activity-level: also receives counterparty selection results for the inbox flows.
 */
class InboxScreenViewModel(
    private val inboxRepository: InboxRepository,
    private val accountRepository: AccountRepository,
    private val cardAccountPreferences: CardAccountPreferences,
    private val cardAccountResolver: CardAccountResolver,
    private val undoInboxItemUseCase: UndoInboxItemUseCase,
) : ViewModel() {

    private val log by lazyLogger("InboxScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()
    private var itemBeingProcessed: InboxItem? = null
    private var cardBeingMapped: String? = null

    private val accountsByIdFlow: Flow<Map<String, Account>> =
        accountRepository
            .getAccountsFlow()
            .map { accounts -> accounts.associateBy(Account::id) }

    val pendingItemList: StateFlow<List<ViewInboxItem>> =
        inboxRepository
            .getPendingItemsFlow()
            .toViewItemsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val doneItemList: StateFlow<List<ViewInboxItem>> =
        inboxRepository
            .getRecentDoneItemsFlow(limit = 30)
            .toViewItemsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val cardItemList: StateFlow<List<ViewCardAccountItem>> =
        combine(
            inboxRepository.getKnownCardLast4Flow(),
            cardAccountPreferences.getCardAccountsFlow(),
            accountsByIdFlow,
        ) { cards, accountIdsByCard, accountsById ->
            cards.map { cardLast4 ->
                ViewCardAccountItem(
                    cardLast4 = cardLast4,
                    accountTitle = accountIdsByCard[cardLast4]?.let(accountsById::get)?.title,
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun Flow<List<InboxItem>>.toViewItemsFlow(): Flow<List<ViewInboxItem>> =
        combine(
            this,
            accountsByIdFlow,
        ) { items, accountsById ->
            items.map { item ->
                ViewInboxItem(
                    item = item,
                    accountCurrencyCode = item.accountId?.let(accountsById::get)?.currency?.code,
                )
            }
        }

    fun onPendingItemClicked(item: ViewInboxItem) {
        val inboxItem = item.source
            ?: return

        viewModelScope.launch {
            val accountId = inboxItem.accountId
                ?.takeIf { accountRepository.getAccount(it)?.isArchived == false }
                ?: cardAccountResolver.resolve(
                    cardLast4 = inboxItem.cardLast4,
                    ruleAccountId = null,
                    usableAccountIds = accountRepository
                        .getAccounts()
                        .filterNot(Account::isArchived)
                        .mapTo(mutableSetOf(), Account::id),
                )

            if (accountId == null) {
                log.warn {
                    "onPendingItemClicked(): no account to pay from or receive to"
                }
                return@launch
            }

            log.debug {
                "onPendingItemClicked(): proceeding to category selection:" +
                        "\nitem=$inboxItem," +
                        "\naccountId=$accountId"
            }

            itemBeingProcessed = inboxItem
            cardBeingMapped = null
            _events.emit(
                Event.ProceedToCategorySelection(
                    accountId = TransferCounterpartyId.Account(accountId),
                    isIncome = inboxItem.direction == InboxItem.Direction.Incoming,
                )
            )
        }
    }

    fun onDismissClicked(item: ViewInboxItem) {
        val itemId = item.source?.id
            ?: return

        viewModelScope.launch {
            inboxRepository.dismiss(itemId)
        }
    }

    fun onUndoClicked(item: ViewInboxItem) {
        val inboxItem = item.source
            ?: return

        viewModelScope.launch {
            undoInboxItemUseCase(inboxItem)
                .onFailure { error ->
                    log.error(error) {
                        "onUndoClicked(): failed to undo:" +
                                "\nitem=$inboxItem"
                    }

                    _events.emit(
                        Event.ShowUndoError(
                            technicalReason = error.message
                                ?: error::class.simpleName
                                ?: error.toString(),
                        )
                    )
                }
        }
    }

    fun onCardItemClicked(item: ViewCardAccountItem) {
        cardBeingMapped = item.cardLast4
        itemBeingProcessed = null
        _events.tryEmit(Event.ProceedToAccountSelection)
    }

    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        viewModelScope.launch {
            val cardLast4 = cardBeingMapped
            if (cardLast4 != null) {
                cardBeingMapped = null
                val account = (result.selectedCounterparty as? TransferCounterparty.Account)
                    ?.account
                    ?: return@launch

                log.debug {
                    "onCounterpartySelected(): mapping card:" +
                            "\ncardLast4=$cardLast4," +
                            "\naccount=$account"
                }

                cardAccountPreferences.setAccountIdForCard(cardLast4, account.id)
                return@launch
            }

            val inboxItem = itemBeingProcessed
                ?: return@launch
            itemBeingProcessed = null

            val category = (result.selectedCounterparty as? TransferCounterparty.Category)
                ?.category
                ?: return@launch
            val accountId = (result.otherSelectedCounterpartyId as? TransferCounterpartyId.Account)
                ?.accountId
                ?: return@launch
            val account = accountRepository.getAccount(accountId)
                ?: return@launch

            _events.emit(
                Event.ProceedToTransfer(
                    route = InboxTransferPrefill.buildRoute(
                        item = inboxItem,
                        account = account,
                        category = category,
                    )
                )
            )
        }
    }

    fun onRulesClicked() {
        _events.tryEmit(Event.ProceedToRules)
    }

    fun onCloseClicked() {
        _events.tryEmit(Event.Close)
    }

    sealed interface Event {

        /**
         * Pass the result to [onCounterpartySelected].
         *
         * @param isIncome whether to select an income category (the source)
         * rather than an expense one (the destination)
         */
        class ProceedToCategorySelection(
            val accountId: TransferCounterpartyId.Account,
            val isIncome: Boolean,
        ) : Event

        /**
         * Pass the result to [onCounterpartySelected].
         */
        object ProceedToAccountSelection : Event

        class ProceedToTransfer(
            val route: TransferSheetRoute,
        ) : Event

        object ProceedToRules : Event

        class ShowUndoError(
            val technicalReason: String,
        ) : Event

        object Close : Event
    }
}
