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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.radiokot.money.inbox.logic.LearnedRule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import ua.com.radiokot.money.inbox.logic.CardAccountResolver
import ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCase
import ua.com.radiokot.money.inbox.logic.InboxCardAcceptance
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.data.Transfer
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    private val cardAccountResolver: CardAccountResolver,
    private val transferFundsUseCase: TransferFundsUseCase,
    private val completeInboxItemUseCase: CompleteInboxItemUseCase,
    private val undoInboxItemUseCase: UndoInboxItemUseCase,
) : ViewModel() {

    private val log by lazyLogger("InboxCardsVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    /** Skipped items go to the end, in the order of skipping. */
    private val skippedKeys = MutableStateFlow<List<String>>(emptyList())

    /** The "Remember" toggles changed by the user, by item ID. */
    private val rememberOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Items being recorded: hidden right away so the next card shows. */
    private val inFlightKeys = MutableStateFlow<Set<String>>(emptySet())
    private val seenKeys = mutableSetOf<String>()
    private val history = MutableStateFlow<List<Transfer>>(emptyList())
    private var lastAction: LastAction? = null
    private var itemBeingPicked: InboxItem? = null

    private val _undo = MutableStateFlow<ViewInboxCardUndo?>(null)
    val undo = _undo.asStateFlow()

    private class Lookup(
        val accountsById: Map<String, Account>,
        val categoriesById: Map<String, Category>,
        val subcategoriesById: Map<String, Subcategory>,
        val rules: List<PayeeRule>,
        val history: List<Transfer>,
    )

    private val lookupFlow: Flow<Lookup> =
        combine(
            accountRepository.getAccountsFlow(),
            categoryRepository.getSubcategoriesByCategoriesFlow(),
            payeeRuleRepository.getRulesFlow(),
            history,
        ) { accounts, subcategoriesByCategories, rules, history ->
            Lookup(
                accountsById = accounts.associateBy(Account::id),
                categoriesById = subcategoriesByCategories.keys.associateBy(Category::id),
                subcategoriesById = subcategoriesByCategories.values
                    .flatten()
                    .associateBy(Subcategory::id),
                rules = rules,
                history = history,
            )
        }

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
        ) { items, lookup, overrides ->
            items.map { item -> toViewCard(item, lookup, overrides[item.id]) to item }
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

    @OptIn(ExperimentalTime::class)
    private fun toViewCard(
        item: InboxItem,
        lookup: Lookup,
        rememberOverride: Boolean?,
    ): ViewInboxCard {
        val isIncoming = item.direction == InboxItem.Direction.Incoming
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            .orEmpty()
        val rulesOfDirection = lookup.rules.filter { rule ->
            val category = rule.categoryId?.let(lookup.categoriesById::get)
            category == null || category.isIncome == isIncoming
        }
        val historyOfDirection = lookup.history.mapNotNull { transfer ->
            val categoryCounterparty =
                if (isIncoming)
                    transfer.source as? TransferCounterparty.Category
                else
                    transfer.destination as? TransferCounterparty.Category
            categoryCounterparty
                ?.takeIf { it.category.isIncome == isIncoming }
                ?.let { counterparty ->
                    InboxCardSuggester.HistoryEntry(
                        normalizedMemo = transfer.memo
                            ?.let(PayeeNormalizer::normalize)
                            .orEmpty(),
                        category = InboxCardSuggester.CategoryKey(
                            categoryId = counterparty.category.id,
                            subcategoryId = counterparty.subcategory?.id,
                        ),
                    )
                }
        }
        val result = InboxCardSuggester.suggest(
            normalizedPayee = normalizedPayee,
            rules = rulesOfDirection,
            history = historyOfDirection,
            amount = item.amount,
            isUsable = { key ->
                val category = lookup.categoriesById[key.categoryId]
                category != null
                        && !category.isArchived
                        && category.isIncome == isIncoming
                        && (key.subcategoryId == null
                        || lookup.subcategoriesById[key.subcategoryId]?.categoryId == category.id)
            },
        )

        fun viewCategory(key: InboxCardSuggester.CategoryKey): ViewInboxCardCategory? {
            val category = lookup.categoriesById[key.categoryId]
                ?: return null
            return ViewInboxCardCategory(
                key = key,
                title = category.title,
                subcategoryTitle = key.subcategoryId?.let(lookup.subcategoriesById::get)?.title,
                colorScheme = category.colorScheme,
                icon = category.icon,
            )
        }

        val suggestion = result.suggestion?.category?.let(::viewCategory)
        val account = item.accountId?.let(lookup.accountsById::get)
        val payeeDisplayName = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?.takeIf(String::isNotEmpty)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

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
            metaText = listOfNotNull(
                formatTime(item.receivedAt, today),
                account?.title,
                item.cardLast4?.let { "•$it" }.takeIf { account == null },
            ).joinToString(" · "),
            suggestion = suggestion,
            reasonText = suggestion?.let { category ->
                when (val reason = result.suggestion.reason) {
                    is InboxCardSuggester.Reason.Rule ->
                        if (reason.rule.amountRange != null)
                            "${describeRange(reason.rule.amountRange, item.currencyCode)} at $payeeDisplayName → ${category.fullTitle}"
                        else if (reason.rule.matchType == PayeeRule.MatchType.Exact)
                            "Remembered payee → ${category.fullTitle}"
                        else
                            "Payee contains “${reason.rule.payeePattern}” → ${category.fullTitle}"

                    is InboxCardSuggester.Reason.PayeeHistory ->
                        "Recorded to ${category.fullTitle} ${reason.count}× before"

                    InboxCardSuggester.Reason.MostUsed ->
                        "New payee: your most used category"
                }
            },
            alternatives = result.alternatives.mapNotNull(::viewCategory),
            isRememberOn = result.rememberDefault
                ?.let { default -> rememberOverride ?: default }
                ?.takeIf { normalizedPayee.isNotEmpty() },
            isAmountRulesHinted = result.isPayeeHistoryMixed && normalizedPayee.isNotEmpty(),
        )
    }

    private fun formatTime(
        dateTime: LocalDateTime,
        today: kotlinx.datetime.LocalDate,
    ): String {
        val time = dateTime.time.toString().take(5)
        return when (dateTime.date) {
            today -> "Today $time"
            else -> "${dateTime.date} $time"
        }
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
        showUndo("Skipped, it stays in the inbox")
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
        inFlightKeys.value += item.id

        viewModelScope.launch {
            val account = resolveAccount(item)
            val category = categoryRepository.getCategory(categoryKey.categoryId)
            val subcategory = categoryKey.subcategoryId?.let { categoryRepository.getSubcategory(it) }

            if (account == null || category == null) {
                inFlightKeys.value -= item.id
                _events.emit(Event.ShowError("No account or category to record to"))
                return@launch
            }

            when (val decision = InboxCardAcceptance.decide(item, account, category, subcategory)) {
                is InboxCardAcceptance.Decision.OpenSheet -> {
                    // The sheet completes the item; until then it is a regular pending card.
                    inFlightKeys.value -= item.id
                    _events.emit(Event.ProceedToTransfer(decision.route))
                }

                is InboxCardAcceptance.Decision.Record -> {
                    val transferId = UUID.randomUUID().toString()
                    // Learn an exact rule only when the card asks to remember.
                    val rememberPattern = item.payee
                        ?.let(PayeeNormalizer::normalize)
                        ?.takeIf(String::isNotEmpty)
                        ?.takeIf {
                            cardsWithItems.value
                                .firstOrNull { (_, cardItem) -> cardItem.id == item.id }
                                ?.first
                                ?.isRememberOn == true
                        }
                    val rulesBefore = payeeRuleRepository.getRules()
                    var learnedRuleId: String? = null

                    log.debug {
                        "record(): recording:" +
                                "\nitem=$item," +
                                "\ndecision=$decision"
                    }

                    val recorded = transferFundsUseCase(
                        sourceId = decision.sourceId,
                        sourceAmount = decision.sourceAmount,
                        destinationId = decision.destinationId,
                        destinationAmount = decision.destinationAmount,
                        memo = decision.memo,
                        dateTime = item.receivedAt,
                        transferId = transferId,
                    ).onSuccess {
                        completeInboxItemUseCase(
                            itemId = item.id,
                            transferId = transferId,
                            rememberPayeePattern = rememberPattern,
                            sourceId = decision.sourceId,
                            destinationId = decision.destinationId,
                        ).onFailure { error ->
                            log.error(error) {
                                "record(): failed to complete the item"
                            }
                        }

                        if (rememberPattern != null) {
                            // The rule cache is refreshed by the database watch, wait for it a bit.
                            val rulesAfter = withTimeoutOrNull(RULES_REFRESH_TIMEOUT_MS) {
                                payeeRuleRepository
                                    .getRulesFlow()
                                    .first { rules ->
                                        rules.any { rule ->
                                            rule.payeePattern == rememberPattern
                                                    && rule.matchType == PayeeRule.MatchType.Exact
                                                    && rule.amountRange == null
                                        }
                                    }
                            } ?: payeeRuleRepository.getRules()
                            learnedRuleId = LearnedRule.createdRuleId(
                                rulesBefore = rulesBefore,
                                rulesAfter = rulesAfter,
                                payeePattern = rememberPattern,
                            )
                        }

                        recordRuleHitIfFollowed(item, categoryKey)
                    }

                    // Once the item leaves the pending list, the in-flight mark is no longer needed.
                    inFlightKeys.value -= item.id

                    recorded
                        .onSuccess {
                            lastAction = LastAction.Recorded(
                                item = item.copy(
                                    status = InboxItem.Status.Done,
                                    transferId = transferId,
                                ),
                                learnedRuleId = learnedRuleId,
                            )
                            showUndo(
                                "Recorded to ${categoryTitle ?: category.title}" +
                                        if (learnedRuleId != null) ", remembered" else ""
                            )
                        }
                        .onFailure { error ->
                            log.error(error) {
                                "record(): failed to record"
                            }
                            _events.emit(Event.ShowError("Failed to record: ${error.message}"))
                        }
                }
            }
        }
    }

    private suspend fun recordRuleHitIfFollowed(
        item: InboxItem,
        categoryKey: InboxCardSuggester.CategoryKey,
    ) {
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            ?: return
        val rule = ua.com.radiokot.money.inbox.logic.PayeeRuleMatcher.match(
            normalizedPayee = normalizedPayee,
            rules = payeeRuleRepository.getRules(),
            amount = item.amount,
        ) ?: return
        if (rule.categoryId == categoryKey.categoryId) {
            payeeRuleRepository.recordHit(rule.id, item.receivedAt)
        }
    }

    private suspend fun resolveAccount(item: InboxItem): Account? {
        val usableAccounts = accountRepository
            .getAccounts()
            .filterNot(Account::isArchived)
        val accountId = item.accountId
            ?.takeIf { id -> usableAccounts.any { it.id == id } }
            ?: cardAccountResolver.resolve(
                cardLast4 = item.cardLast4,
                ruleAccountId = null,
                usableAccountIds = usableAccounts.mapTo(mutableSetOf(), Account::id),
            )

        val account = usableAccounts.firstOrNull { it.id == accountId }
        if (account == null) {
            log.warn {
                "resolveAccount(): no account to pay from or receive to"
            }
            _events.emit(Event.ShowError("No account to record to"))
        }
        return account
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
                    // Only a rule this accept created is removed, never an older one.
                    action.learnedRuleId?.let { ruleId ->
                        payeeRuleRepository.deleteRule(ruleId)
                    }
                    undoInboxItemUseCase(action.item)
                        .onFailure { error ->
                            log.error(error) {
                                "onUndoClicked(): failed to undo"
                            }
                            _events.emit(Event.ShowError("Failed to undo: ${error.message}"))
                        }
                }
        }
    }

    fun onUndoTimedOut(undo: ViewInboxCardUndo) {
        if (_undo.value?.id == undo.id) {
            _undo.value = null
        }
    }

    private fun showUndo(text: String) {
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
            val text: String,
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

        const val RULES_REFRESH_TIMEOUT_MS = 3000L
    }
}
