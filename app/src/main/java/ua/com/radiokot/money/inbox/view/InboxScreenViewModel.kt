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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.AcceptInboxSuggestionUseCase
import ua.com.radiokot.money.inbox.logic.InboxSuggestionLookup
import ua.com.radiokot.money.inbox.logic.InboxTransferPrefill
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.data.Transfer
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.failureText
import kotlin.time.Clock

/**
 * The Inbox tab: pending items with a one-tap suggestion, and the items recorded today.
 * Activity-level: also receives the category picker result.
 */
class InboxScreenViewModel(
    private val inboxRepository: InboxRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
    private val transferHistoryRepository: TransferHistoryRepository,
    private val undoInboxItemUseCase: UndoInboxItemUseCase,
    private val acceptInboxSuggestionUseCase: AcceptInboxSuggestionUseCase,
    private val autoBookPreferences: AutoBookPreferences,
) : ViewModel() {

    private val log by lazyLogger("InboxScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()
    private var itemBeingProcessed: InboxItem? = null

    /** Rules learned by accepts in this session, by item ID, to remove on undo. */
    private val learnedRuleIds = mutableMapOf<String, String>()

    /** Items being recorded: hidden right away. */
    private val inFlightKeys = MutableStateFlow<Set<String>>(emptySet())
    private val history = MutableStateFlow<List<Transfer>>(emptyList())

    private val pendingItems: StateFlow<List<InboxItem>> =
        inboxRepository
            .getPendingItemsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val recentDoneItems: StateFlow<List<InboxItem>> =
        inboxRepository
            .getRecentDoneItemsFlow(limit = RECENT_DONE_LIMIT)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The latest lookup, to compute the rule to learn at accept time. */
    @Volatile
    private var latestLookup: InboxSuggestionLookup? = null

    private val lookupFlow: Flow<InboxSuggestionLookup> =
        combine(
            accountRepository.getAccountsFlow(),
            categoryRepository.getSubcategoriesByCategoriesFlow(),
            payeeRuleRepository.getRulesFlow(),
            history,
        ) { accounts, subcategoriesByCategories, rules, history ->
            InboxSuggestionLookup(
                accountsById = accounts.associateBy(Account::id),
                categoriesById = subcategoriesByCategories.keys.associateBy(Category::id),
                subcategoriesById = subcategoriesByCategories.values
                    .flatten()
                    .associateBy(Subcategory::id),
                rules = rules,
                history = history,
            )
        }
            .onEach { latestLookup = it }

    val pendingItemList: StateFlow<List<ViewInboxTabPending>> =
        combine(
            pendingItems,
            lookupFlow,
            inFlightKeys,
            // So switching "learn from history" changes the suggestions right away.
            autoBookPreferences.getBehaviourFlow(),
        ) { items, lookup, inFlight, behaviour ->
            items
                .filterNot { it.id in inFlight }
                .map { item ->
                    toViewPending(
                        item = item,
                        lookup = lookup,
                        useHistory = behaviour.learnFromHistory,
                    )
                }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The items recorded today, newest first. */
    val doneItemList: StateFlow<List<ViewInboxItem>> =
        combine(
            recentDoneItems,
            accountRepository.getAccountsFlow(),
        ) { items, accounts ->
            val accountsById = accounts.associateBy(Account::id)
            InboxTabItems
                .doneToday(items, localToday())
                .map { item ->
                    ViewInboxItem(
                        item = item,
                        accountCurrencyCode = item.accountId?.let(accountsById::get)?.currency?.code,
                    )
                }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val counts: StateFlow<InboxTabItems.Counts> =
        combine(
            pendingItems,
            recentDoneItems,
        ) { pending, done ->
            InboxTabItems.counts(
                pending = pending,
                done = done,
                today = localToday(),
            )
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, InboxTabItems.Counts(0, 0))

    init {
        viewModelScope.launch {
            history.value = runCatching {
                transferHistoryRepository
                    .getTransferHistoryPage(
                        cursor = null,
                        limit = HISTORY_LIMIT,
                        withinPeriod = HistoryPeriod.Since70th,
                        counterpartyIds = null,
                    )
                    .data
            }
                .onFailure { error ->
                    log.warn(error) {
                        "init(): failed to load the history for suggestions"
                    }
                }
                .getOrDefault(emptyList())
        }
    }

    private fun toViewPending(
        item: InboxItem,
        lookup: InboxSuggestionLookup,
        useHistory: Boolean,
    ): ViewInboxTabPending {
        val account = item.accountId?.let(lookup.accountsById::get)
        val isRecognized = item.amount != null
        val result =
            if (isRecognized)
                lookup.suggest(
                    item = item,
                    useHistory = useHistory,
                )
            else
                null
        val hasPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            ?.isNotEmpty() == true

        return ViewInboxTabPending(
            item = ViewInboxItem(
                item = item,
                accountCurrencyCode = account?.currency?.code,
            ),
            sourceText = listOfNotNull(
                account?.title,
                item.cardLast4?.let { "•$it" }.takeIf { account == null },
            ).joinToString(" · "),
            rawText = item.rawText,
            isRecognized = isRecognized,
            suggestion = result?.suggestion?.category?.let(lookup::viewCategory),
            isRememberOn = hasPayee && result?.rememberDefault == true,
        )
    }

    fun onAcceptClicked(pending: ViewInboxTabPending) {
        val suggestion = pending.suggestion
        if (suggestion == null) {
            onOtherClicked(pending)
            return
        }
        val item = pending.item.source
            ?: return

        if (item.id in inFlightKeys.value) {
            return
        }
        inFlightKeys.value += item.id

        viewModelScope.launch {
            val result = acceptInboxSuggestionUseCase(
                item = item,
                categoryId = suggestion.key.categoryId,
                subcategoryId = suggestion.key.subcategoryId,
                remember =
                    if (pending.isRememberOn)
                        latestLookup
                            ?.defaultRememberSelection(item, suggestion.key.categoryId)
                            ?.toChoice()
                    else
                        null,
            )

            // Once the item leaves the pending list, the in-flight mark is no longer needed.
            inFlightKeys.value -= item.id

            when (result) {
                is AcceptInboxSuggestionUseCase.Result.Recorded -> {
                    // The item moves to the recorded list on its own.
                    result.learnedRuleId?.let { learnedRuleIds[item.id] = it }
                }

                is AcceptInboxSuggestionUseCase.Result.OpenSheet ->
                    // The sheet completes the item; until then it is a regular pending one.
                    _events.emit(Event.ProceedToTransfer(result.route))

                AcceptInboxSuggestionUseCase.Result.NoAccountOrCategory ->
                    _events.emit(Event.ShowError(ViewText.Res(R.string.inbox_no_account_or_category)))

                is AcceptInboxSuggestionUseCase.Result.Failed ->
                    _events.emit(
                        Event.ShowError(
                            failureText(
                                withReasonId = R.string.inbox_cards_record_failed,
                                withoutReasonId = R.string.inbox_cards_record_failed_no_reason,
                                reason = result.error.message,
                            )
                        )
                    )
            }
        }
    }

    /**
     * Pick another category: the category picker, then the prefilled transfer sheet.
     */
    fun onOtherClicked(pending: ViewInboxTabPending) {
        val inboxItem = pending.item.source
            ?: return

        viewModelScope.launch {
            val account = acceptInboxSuggestionUseCase.resolveAccount(inboxItem)

            if (account == null) {
                log.warn {
                    "onOtherClicked(): no account to pay from or receive to"
                }
                _events.emit(Event.ShowError(ViewText.Res(R.string.inbox_no_account)))
                return@launch
            }

            itemBeingProcessed = inboxItem
            _events.emit(
                Event.ProceedToCategorySelection(
                    accountId = TransferCounterpartyId.Account(account.id),
                    isIncome = inboxItem.direction == InboxItem.Direction.Incoming,
                )
            )
        }
    }

    fun onDismissClicked(pending: ViewInboxTabPending) {
        val itemId = pending.item.source?.id
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
                .onSuccess {
                    // An accidental accept must not leave an exact rule behind.
                    // Only once undone: a failed undo keeps the record, so the rule stays.
                    learnedRuleIds.remove(inboxItem.id)?.let { ruleId ->
                        runCatching { acceptInboxSuggestionUseCase.forgetLearnedRule(ruleId) }
                            .onFailure { error ->
                                log.error(error) {
                                    "onUndoClicked(): failed to forget the learned rule"
                                }
                            }
                    }
                }
                .onFailure { error ->
                    log.error(error) {
                        "onUndoClicked(): failed to undo"
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

    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        viewModelScope.launch {
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

    fun onSortAsCardsClicked() {
        _events.tryEmit(Event.ProceedToCards)
    }

    fun onRulesClicked() {
        _events.tryEmit(Event.ProceedToRules)
    }

    private fun localToday(): LocalDate =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

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

        class ProceedToTransfer(
            val route: TransferSheetRoute,
        ) : Event

        object ProceedToCards : Event

        object ProceedToRules : Event

        class ShowError(
            val text: ViewText,
        ) : Event

        class ShowUndoError(
            val technicalReason: String,
        ) : Event
    }

    private companion object {
        /**
         * Recent transfers to learn suggestions from, newest first.
         */
        const val HISTORY_LIMIT = 400

        /**
         * Done items to pick today's from.
         */
        const val RECENT_DONE_LIMIT = 100
    }
}

/**
 * A pending item on the Inbox tab.
 */
@androidx.compose.runtime.Immutable
class ViewInboxTabPending(
    val item: ViewInboxItem,
    /**
     * The account or the card, e.g. "Card"; shown after the time of receiving.
     */
    val sourceText: String,
    val rawText: String,
    /**
     * Whether the amount is parsed. Unrecognized items have no suggestion and show the raw text.
     */
    val isRecognized: Boolean,
    val suggestion: ViewInboxCardCategory?,
    /**
     * Accepting the suggestion also remembers the payee.
     */
    val isRememberOn: Boolean,
)
