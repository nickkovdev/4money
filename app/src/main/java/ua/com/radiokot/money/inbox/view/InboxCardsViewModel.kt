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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.radiokot.money.R
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.AcceptInboxSuggestionUseCase
import ua.com.radiokot.money.inbox.logic.InboxSuggestionLookup
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.logic.PayeeWordSelection
import ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase
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

/**
 * The inbox as swipe cards: accept the suggestion, skip to the end, or pick a category.
 * Activity-level: also receives the category picker result.
 */
class InboxCardsViewModel(
    private val inboxRepository: InboxRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transferHistoryRepository: TransferHistoryRepository,
    private val acceptInboxSuggestionUseCase: AcceptInboxSuggestionUseCase,
    private val undoInboxItemUseCase: UndoInboxItemUseCase,
    private val autoBookPreferences: AutoBookPreferences,
) : ViewModel() {

    private val log by lazyLogger("InboxCardsVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    /** Skipped items go to the end, in the order of skipping. */
    private val skippedKeys = MutableStateFlow<List<String>>(emptyList())

    /** The "Remember" toggles changed by the user, by item ID. */
    private val rememberOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** The words to remember touched by the user, by item ID. */
    private val rememberSelectionOverrides =
        MutableStateFlow<Map<String, PayeeWordSelection>>(emptyMap())

    /** Items being recorded: hidden right away so the next card shows. */
    private val inFlightKeys = MutableStateFlow<Set<String>>(emptySet())
    private val seenKeys = mutableSetOf<String>()
    private val history = MutableStateFlow<List<Transfer>>(emptyList())
    private var lastAction: LastAction? = null
    private var itemBeingPicked: InboxItem? = null

    private val _undo = MutableStateFlow<ViewInboxCardUndo?>(null)
    val undo = _undo.asStateFlow()

    /** The latest lookup, to compute the rule to learn at record time. */
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

    private val orderedPendingItems: Flow<List<InboxItem>> =
        combine(
            inboxRepository.getPendingItemsFlow(),
            skippedKeys,
            inFlightKeys,
        ) { pending, skipped, inFlight ->
            val visible = pending.filterNot { it.id in inFlight }
            val skippedOrder = skipped.withIndex().associate { (index, key) -> key to index }
            visible.sortedBy { skippedOrder[it.id] ?: -1 }
        }

    private val cardsWithItems: StateFlow<List<Pair<ViewInboxCard, InboxItem>>> =
        combine(
            orderedPendingItems,
            lookupFlow,
            rememberOverrides,
            rememberSelectionOverrides,
        ) { items, lookup, overrides, selectionOverrides ->
            items.map { item ->
                toViewCard(
                    item = item,
                    lookup = lookup,
                    rememberOverride = overrides[item.id],
                    rememberSelectionOverride = selectionOverrides[item.id],
                ) to item
            }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cards: StateFlow<List<ViewInboxCard>> =
        cardsWithItems
            .map { list -> list.map { it.first } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<ViewInboxCardsProgress> =
        combine(
            inboxRepository.getPendingItemsFlow(),
            inFlightKeys,
        ) { pending, inFlight ->
            seenKeys += pending.map(InboxItem::id)
            val remaining = pending.count { it.id !in inFlight }
            ViewInboxCardsProgress(
                sortedCount = seenKeys.size - remaining,
                totalCount = seenKeys.size,
            )
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ViewInboxCardsProgress(0, 0))

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

    private fun toViewCard(
        item: InboxItem,
        lookup: InboxSuggestionLookup,
        rememberOverride: Boolean?,
        rememberSelectionOverride: PayeeWordSelection?,
    ): ViewInboxCard {
        val isIncoming = item.direction == InboxItem.Direction.Incoming
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            .orEmpty()
        val result = lookup.suggest(
            item = item,
            useHistory = autoBookPreferences.isLearnFromHistoryEnabled,
        )

        fun viewCategory(key: InboxCardSuggester.CategoryKey) = lookup.viewCategory(key)

        val suggestion = result.suggestion?.category?.let(::viewCategory)
        val isRememberOn = result.rememberDefault
            ?.let { default -> rememberOverride ?: default }
            ?.takeIf { normalizedPayee.isNotEmpty() }
        val rememberSelection =
            if (isRememberOn == true)
                rememberSelectionOverride
                    ?: result.suggestion?.category
                        ?.let { key ->
                            lookup.defaultRememberSelection(item, key.categoryId, key.subcategoryId)
                        }
                    ?: PayeeWordSelection.whole(normalizedPayee)
            else
                null
        val account = item.accountId?.let(lookup.accountsById::get)
        val payeeDisplayName = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?.takeIf(String::isNotEmpty)

        return ViewInboxCard(
            key = item.id,
            title = payeeDisplayName
                ?: item.rawText.lineSequence().last().take(120),
            isTitleRawText = payeeDisplayName == null,
            amount = viewAmountOf(item),
            isIncoming = isIncoming,
            isForeignCurrency = account != null
                    && item.currencyCode != null
                    && !item.currencyCode.equals(account.currency.code, ignoreCase = true),
            receivedAt = item.receivedAt,
            sourceText = listOfNotNull(
                account?.title,
                item.cardLast4?.let { "•$it" }.takeIf { account == null },
            ).joinToString(" · "),
            suggestion = suggestion,
            reasonText = suggestion?.let { category ->
                when (val reason = result.suggestion.reason) {
                    is InboxCardSuggester.Reason.Rule ->
                        if (reason.rule.amountRange != null)
                            ViewText.Res(
                                id = R.string.inbox_reason_range,
                                args = listOf(
                                    ViewText.Dynamic(
                                        describeRangeText(reason.rule.amountRange, item.currencyCode)
                                    ),
                                    payeeDisplayName.orEmpty(),
                                    category.fullTitle,
                                ),
                            )
                        else if (reason.rule.matchType == PayeeRule.MatchType.Exact)
                            ViewText.Res(
                                id = R.string.inbox_reason_remembered,
                                args = listOf(category.fullTitle),
                            )
                        else
                            ViewText.Res(
                                id = R.string.inbox_reason_contains,
                                args = listOf(reason.rule.payeePattern, category.fullTitle),
                            )

                    is InboxCardSuggester.Reason.PayeeHistory ->
                        ViewText.Plural(
                            id = R.plurals.inbox_reason_history,
                            count = reason.count,
                            args = listOf(category.fullTitle, reason.count),
                        )

                    InboxCardSuggester.Reason.MostUsed ->
                        ViewText.Res(R.string.inbox_reason_most_used)
                }
            },
            alternatives = result.alternatives.mapNotNull(::viewCategory),
            isRememberOn = isRememberOn,
            rememberWords = rememberSelection?.let(::ViewRememberPayee),
            isAmountRulesHinted = result.isPayeeHistoryMixed && normalizedPayee.isNotEmpty(),
        )
    }

    private fun itemOf(card: ViewInboxCard): InboxItem? =
        cardsWithItems.value.firstOrNull { it.first.key == card.key }?.second

    fun onAcceptClicked(card: ViewInboxCard) {
        val suggestion = card.suggestion
        if (suggestion == null) {
            onPickClicked(card)
            return
        }

        record(card, suggestion.key)
    }

    fun onAlternativeClicked(
        card: ViewInboxCard,
        category: ViewInboxCardCategory,
    ) {
        record(card, category.key)
    }

    fun onRememberToggled(
        card: ViewInboxCard,
        isOn: Boolean,
    ) {
        rememberOverrides.value += card.key to isOn
    }

    fun onRememberWordClicked(
        card: ViewInboxCard,
        index: Int,
    ) {
        val words = card.rememberWords
            ?: return
        val selection = PayeeWordSelection(
            words = words.words,
            first = words.first,
            last = words.last,
        )
        rememberSelectionOverrides.value += card.key to selection.toggle(index)
    }

    fun onAmountRulesClicked(card: ViewInboxCard) {
        val item = itemOf(card)
            ?: return
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            ?.takeIf(String::isNotEmpty)
            ?: return

        _events.tryEmit(
            Event.ProceedToAmountRules(
                payeePattern = normalizedPayee,
                displayPattern = card.title,
                currencyCode = item.currencyCode,
                isIncome = card.isIncoming,
                categoryOptions = (listOfNotNull(card.suggestion) + card.alternatives)
                    .map { category ->
                        ViewRangeTarget.Category(
                            categoryId = category.key.categoryId,
                            subcategoryId = category.key.subcategoryId,
                            title = category.fullTitle,
                        )
                    }
                    .distinct(),
            )
        )
    }

    fun onSkipClicked(card: ViewInboxCard) {
        val previousSkipped = skippedKeys.value
        skippedKeys.value = previousSkipped - card.key + card.key
        lastAction = LastAction.Skipped(
            key = card.key,
            previousSkipped = previousSkipped,
        )
        showUndo(ViewText.Res(R.string.inbox_cards_skipped))
    }

    fun onPickClicked(card: ViewInboxCard) {
        val item = itemOf(card)
            ?: return

        viewModelScope.launch {
            val account = resolveAccount(item)
                ?: return@launch

            itemBeingPicked = item
            _events.emit(
                Event.ProceedToCategorySelection(
                    accountId = TransferCounterpartyId.Account(account.id),
                    isIncome = item.direction == InboxItem.Direction.Incoming,
                )
            )
        }
    }

    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        val item = itemBeingPicked
            ?: return
        itemBeingPicked = null

        val category = result.selectedCounterparty as? TransferCounterparty.Category
            ?: return

        record(
            item = item,
            categoryKey = InboxCardSuggester.CategoryKey(
                categoryId = category.category.id,
                subcategoryId = category.subcategory?.id,
            ),
            categoryTitle = category.category.title,
        )
    }

    private fun record(
        card: ViewInboxCard,
        categoryKey: InboxCardSuggester.CategoryKey,
    ) {
        val item = itemOf(card)
            ?: return
        val title = (listOf(card.suggestion) + card.alternatives)
            .firstOrNull { it?.key == categoryKey }
            ?.title
        record(item, categoryKey, title)
    }

    private fun record(
        item: InboxItem,
        categoryKey: InboxCardSuggester.CategoryKey,
        categoryTitle: String?,
    ) {
        if (item.id in inFlightKeys.value) {
            return
        }
        // Learn a rule only when the card asks to remember.
        // Read before the card is hidden by the in-flight mark.
        // The touched words, or the defaults for the category being accepted.
        val isRememberOn = cardsWithItems.value
            .firstOrNull { (_, cardItem) -> cardItem.id == item.id }
            ?.first
            ?.isRememberOn == true
        val remember =
            if (isRememberOn)
                (rememberSelectionOverrides.value[item.id]
                    ?: latestLookup
                        ?.defaultRememberSelection(
                            item,
                            categoryKey.categoryId,
                            categoryKey.subcategoryId,
                        ))
                    ?.toChoice()
            else
                null
        inFlightKeys.value += item.id

        viewModelScope.launch {
            val result = acceptInboxSuggestionUseCase(
                item = item,
                categoryId = categoryKey.categoryId,
                subcategoryId = categoryKey.subcategoryId,
                remember = remember,
            )

            // Once the item leaves the pending list, the in-flight mark is no longer needed.
            inFlightKeys.value -= item.id

            when (result) {
                AcceptInboxSuggestionUseCase.Result.NoAccountOrCategory ->
                    _events.emit(Event.ShowError(ViewText.Res(R.string.inbox_no_account_or_category)))

                is AcceptInboxSuggestionUseCase.Result.OpenSheet ->
                    // The sheet completes the item; until then it is a regular pending card.
                    _events.emit(Event.ProceedToTransfer(result.route))

                is AcceptInboxSuggestionUseCase.Result.Recorded -> {
                    lastAction = LastAction.Recorded(
                        item = result.item,
                        learnedRuleId = result.learnedRuleId,
                    )
                    showUndo(
                        ViewText.Res(
                            id =
                                if (result.learnedRuleId != null)
                                    R.string.inbox_cards_recorded_remembered
                                else
                                    R.string.inbox_cards_recorded,
                            args = listOf(categoryTitle ?: result.categoryTitle),
                        )
                    )
                }

                is AcceptInboxSuggestionUseCase.Result.Failed ->
                    _events.emit(Event.ShowError(
                        failureText(
                            withReasonId = R.string.inbox_cards_record_failed,
                            withoutReasonId = R.string.inbox_cards_record_failed_no_reason,
                            reason = result.error.message,
                        )
                    ))
            }
        }
    }

    private suspend fun resolveAccount(item: InboxItem): Account? =
        acceptInboxSuggestionUseCase
            .resolveAccount(item)
            .also { account ->
                if (account == null) {
                    _events.emit(Event.ShowError(ViewText.Res(R.string.inbox_no_account)))
                }
            }

    fun onUndoClicked() {
        val action = lastAction
            ?: return
        lastAction = null
        _undo.value = null

        when (action) {
            is LastAction.Skipped ->
                skippedKeys.value = action.previousSkipped

            is LastAction.Recorded ->
                viewModelScope.launch {
                    undoInboxItemUseCase(action.item)
                        .onSuccess {
                            // Only a rule this accept created is removed, never an older one,
                            // and only once undone: a failed undo keeps the record and the rule.
                            action.learnedRuleId?.let { ruleId ->
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
                            _events.emit(Event.ShowError(
                                failureText(
                                    withReasonId = R.string.inbox_undo_failed,
                                    withoutReasonId = R.string.inbox_undo_failed_no_reason,
                                    reason = error.message,
                                )
                            ))
                        }
                }
        }
    }

    fun onUndoTimedOut(undo: ViewInboxCardUndo) {
        if (_undo.value?.id == undo.id) {
            _undo.value = null
        }
    }

    private fun showUndo(text: ViewText) {
        _undo.value = ViewInboxCardUndo(
            text = text,
            id = System.nanoTime(),
        )
    }

    private sealed interface LastAction {
        class Skipped(
            val key: String,
            val previousSkipped: List<String>,
        ) : LastAction

        class Recorded(
            val item: InboxItem,
            val learnedRuleId: String?,
        ) : LastAction
    }

    sealed interface Event {

        /**
         * Pass the result to [onCounterpartySelected].
         */
        class ProceedToCategorySelection(
            val accountId: TransferCounterpartyId.Account,
            val isIncome: Boolean,
        ) : Event

        class ProceedToTransfer(
            val route: TransferSheetRoute,
        ) : Event

        class ShowError(
            val text: ViewText,
        ) : Event

        /**
         * Open the payee rules with a new amount range for this payee.
         */
        class ProceedToAmountRules(
            val payeePattern: String,
            val displayPattern: String,
            val currencyCode: String?,
            val isIncome: Boolean,
            val categoryOptions: List<ViewRangeTarget.Category>,
        ) : Event
    }

    private companion object {
        /**
         * Recent transfers to learn suggestions from, newest first.
         */
        const val HISTORY_LIMIT = 400
    }
}
