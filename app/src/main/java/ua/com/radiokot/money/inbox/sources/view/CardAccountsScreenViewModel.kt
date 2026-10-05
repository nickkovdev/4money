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

package ua.com.radiokot.money.inbox.sources.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.radiokot.money.R
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.sources.data.AppInfoSource
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.uikit.ViewText

/**
 * Activity-level: also receives the account selection result.
 */
class CardAccountsScreenViewModel(
    inboxRepository: InboxRepository,
    accountRepository: AccountRepository,
    registry: NotificationSourceRegistry,
    private val cardAccountPreferences: CardAccountPreferences,
    private val autoBookPreferences: AutoBookPreferences,
    private val appInfoSource: AppInfoSource,
) : ViewModel() {

    private val log by lazyLogger("CardAccountsScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()
    private var rowBeingMapped: ViewAccountMappingRow.Target? = null

    /**
     * Cards seen in the inbox plus the mapped ones, then a no-card row per source.
     */
    val mapping: StateFlow<ViewAccountMapping> =
        combine(
            inboxRepository.getKnownCardLast4Flow(),
            cardAccountPreferences.getCardAccountsFlow(),
            cardAccountPreferences.getSourceAccountsFlow(),
            accountRepository.getAccountsFlow(),
            registry.sourcesFlow,
        ) { knownCards, accountIdsByCard, accountIdsBySource, accounts, sources ->
            val accountsById = accounts.associateBy(Account::id)

            ViewAccountMapping(
                cardRows = (knownCards + accountIdsByCard.keys)
                    .distinct()
                    .sorted()
                    .map { cardLast4 ->
                        ViewAccountMappingRow(
                            target = ViewAccountMappingRow.Target.Card(cardLast4),
                            title = ViewText.Res(R.string.inbox_card_title, listOf(cardLast4)),
                            accountTitle = accountIdsByCard[cardLast4]
                                ?.let(accountsById::get)
                                ?.title,
                        )
                    },
                sourceRows = sources.map { source ->
                    val label = appInfoSource.getLabel(source.packageName)
                        ?: source.packageName

                    ViewAccountMappingRow(
                        target = ViewAccountMappingRow.Target.Source(source.packageName),
                        title = ViewText.Res(R.string.cards_without_card, listOf(label)),
                        accountTitle = accountIdsBySource[source.packageName]
                            ?.let(accountsById::get)
                            ?.title,
                    )
                },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = ViewAccountMapping(emptyList(), emptyList()),
            )

    val behaviour: StateFlow<AutoBookBehaviour> =
        autoBookPreferences
            .getBehaviourFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AutoBookBehaviour(
                    recordKnownPayees = autoBookPreferences.isRecordKnownPayeesEnabled,
                    askInNotification = autoBookPreferences.isAskInNotificationEnabled,
                    learnFromHistory = autoBookPreferences.isLearnFromHistoryEnabled,
                ),
            )

    fun onRowClicked(row: ViewAccountMappingRow) {
        rowBeingMapped = row.target
        _events.tryEmit(Event.ProceedToAccountSelection)
    }

    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        val target = rowBeingMapped
            ?: return
        rowBeingMapped = null

        val account = (result.selectedCounterparty as? TransferCounterparty.Account)
            ?.account
            ?: return

        viewModelScope.launch {
            log.debug {
                "onCounterpartySelected(): mapping:" +
                        "\ntarget=$target," +
                        "\naccountId=${account.id}"
            }

            when (target) {
                is ViewAccountMappingRow.Target.Card ->
                    cardAccountPreferences.setAccountIdForCard(target.cardLast4, account.id)

                is ViewAccountMappingRow.Target.Source ->
                    cardAccountPreferences.setAccountIdForSource(target.packageName, account.id)
            }
        }
    }

    fun onRecordKnownPayeesChanged(isEnabled: Boolean) {
        autoBookPreferences.isRecordKnownPayeesEnabled = isEnabled
    }

    fun onAskInNotificationChanged(isEnabled: Boolean) {
        autoBookPreferences.isAskInNotificationEnabled = isEnabled
    }

    fun onLearnFromHistoryChanged(isEnabled: Boolean) {
        autoBookPreferences.isLearnFromHistoryEnabled = isEnabled
    }

    fun onBackClicked() {
        _events.tryEmit(Event.Close)
    }

    sealed interface Event {

        /**
         * Pass the result to [onCounterpartySelected].
         */
        object ProceedToAccountSelection : Event

        object Close : Event
    }
}
