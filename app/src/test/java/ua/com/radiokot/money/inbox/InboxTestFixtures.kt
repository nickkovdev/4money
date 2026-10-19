package ua.com.radiokot.money.inbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.currency.data.Amount
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.SourceStats
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.data.Transfer
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.TransferHistoryPage
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import java.math.BigInteger

val EUR = Currency(code = "EUR", symbol = "\u20AC", precision = 2, id = "cur-eur")
val USD = Currency(code = "USD", symbol = "$", precision = 2, id = "cur-usd")
private val colorScheme = ItemColorScheme(name = "Blue1", primary = 0, onPrimary = 0)

fun testAccount(
    id: String,
    currency: Currency = EUR,
    isArchived: Boolean = false,
) = Account(
    title = "Account $id",
    balance = Amount(value = BigInteger.ZERO, currency = currency),
    position = 0.0,
    colorScheme = colorScheme,
    icon = null,
    type = Account.Type.Regular,
    isArchived = isArchived,
    id = id,
)

fun testCategory(
    id: String,
    currency: Currency = EUR,
    isArchived: Boolean = false,
    isIncome: Boolean = false,
) = Category(
    title = "Category $id",
    currency = currency,
    isIncome = isIncome,
    colorScheme = colorScheme,
    icon = null,
    isArchived = isArchived,
    position = 0.0,
    id = id,
)

class FakeInboxRepository : InboxRepository {
    val items = MutableStateFlow<List<InboxItem>>(emptyList())

    override suspend fun existsWithDedupHash(dedupHash: String) =
        items.value.any { it.dedupHash == dedupHash }

    override suspend fun addItem(item: InboxItem) {
        items.value += item
    }

    override fun getPendingItemsFlow(): Flow<List<InboxItem>> =
        items.map { list -> list.filter { it.status == InboxItem.Status.Pending } }

    override fun getRecentDoneItemsFlow(limit: Int): Flow<List<InboxItem>> =
        items.map { list -> list.filter { it.status == InboxItem.Status.Done }.take(limit) }

    override fun getPendingCountFlow(): Flow<Long> =
        getPendingItemsFlow().map { it.size.toLong() }

    override fun getKnownCardLast4Flow(): Flow<List<String>> =
        items.map { list -> list.mapNotNull(InboxItem::cardLast4).distinct().sorted() }

    val sourceStats = MutableStateFlow<Map<String, SourceStats>>(emptyMap())

    override fun getSourceStatsFlow(since: LocalDateTime): Flow<Map<String, SourceStats>> =
        sourceStats

    override suspend fun markDone(itemId: String, transferId: String) =
        update(itemId) { it.copy(status = InboxItem.Status.Done, transferId = transferId) }

    override suspend fun markPending(itemId: String) =
        update(itemId) { it.copy(status = InboxItem.Status.Pending, transferId = null) }

    override suspend fun dismiss(itemId: String) =
        update(itemId) { it.copy(status = InboxItem.Status.Dismissed) }

    private fun update(itemId: String, transform: (InboxItem) -> InboxItem) {
        items.value = items.value.map { if (it.id == itemId) transform(it) else it }
    }
}

class FakePayeeRuleRepository(
    initialRules: List<PayeeRule> = emptyList(),
) : PayeeRuleRepository {
    val rules = MutableStateFlow(initialRules)
    val hits = mutableListOf<Pair<String, LocalDateTime>>()

    override suspend fun getRules() = rules.value

    override fun getRulesFlow(): Flow<List<PayeeRule>> = rules

    override suspend fun saveRuleForPayee(
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        categoryId: String,
        subcategoryId: String?,
        accountId: String?,
    ) {
        val existing = rules.value.find {
            it.payeePattern == payeePattern && it.matchType == matchType && it.amountRange == null
        }
        rules.value =
            if (existing != null)
                rules.value.map {
                    if (it == existing)
                        it.copy(categoryId = categoryId, subcategoryId = subcategoryId, accountId = accountId)
                    else
                        it
                }
            else
                rules.value + PayeeRule(
                    payeePattern = payeePattern,
                    matchType = matchType,
                    categoryId = categoryId,
                    subcategoryId = subcategoryId,
                    accountId = accountId,
                    hits = 0,
                    lastUsedAt = null,
                )
    }

    override suspend fun updateRule(ruleId: String, payeePattern: String, matchType: PayeeRule.MatchType) {
        rules.value = rules.value.map {
            if (it.id == ruleId) it.copy(payeePattern = payeePattern, matchType = matchType) else it
        }
    }

    override suspend fun saveRangeRule(
        ruleId: String?,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        amountRange: ua.com.radiokot.money.inbox.data.AmountRange,
        action: PayeeRule.Action,
        categoryId: String?,
        subcategoryId: String?,
    ) {
        val rule = PayeeRule(
            payeePattern = payeePattern,
            matchType = matchType,
            categoryId = categoryId.takeIf { action == PayeeRule.Action.Record },
            subcategoryId = subcategoryId,
            accountId = null,
            hits = 0,
            lastUsedAt = null,
            id = ruleId ?: java.util.UUID.randomUUID().toString(),
            amountRange = amountRange,
            action = action,
        )
        rules.value = rules.value.filterNot { it.id == rule.id } + rule
    }

    override suspend fun deleteRule(ruleId: String) {
        rules.value = rules.value.filterNot { it.id == ruleId }
    }

    override suspend fun recordHit(ruleId: String, at: LocalDateTime) {
        hits += ruleId to at
    }
}

class FakeAccountRepository(
    private val accounts: List<Account>,
) : AccountRepository {
    override suspend fun getAccounts() = accounts
    override fun getAccountsFlow(): Flow<List<Account>> = MutableStateFlow(accounts)
    override suspend fun getAccount(accountId: String) = accounts.find { it.id == accountId }
    override fun getAccountFlow(accountId: String): Flow<Account> =
        MutableStateFlow(accounts.first { it.id == accountId })

    override suspend fun updateBalance(accountId: String, newValue: BigInteger) = error("Not used")
    override suspend fun archive(accountId: String) = error("Not used")
    override suspend fun unarchive(accountId: String, newPosition: Double) = error("Not used")
}

class FakeCategoryRepository(
    private val categories: List<Category>,
    private val subcategories: List<Subcategory> = emptyList(),
) : CategoryRepository {
    override suspend fun getCategories(isIncome: Boolean) = categories.filter { it.isIncome == isIncome }
    override fun getCategoriesFlow(isIncome: Boolean): Flow<List<Category>> =
        MutableStateFlow(categories.filter { it.isIncome == isIncome })

    override suspend fun getCategory(categoryId: String) = categories.find { it.id == categoryId }
    override suspend fun getSubcategory(subcategoryId: String) = subcategories.find { it.id == subcategoryId }
    override fun getSubcategoriesFlow(categoryId: String): Flow<List<Subcategory>> =
        MutableStateFlow(subcategories.filter { it.categoryId == categoryId })

    override fun getSubcategoriesByCategoriesFlow(): Flow<Map<Category, List<Subcategory>>> =
        MutableStateFlow(categories.associateWith { category -> subcategories.filter { it.categoryId == category.id } })

    override suspend fun archiveCategory(categoryId: String) = error("Not used")
    override suspend fun unarchiveCategory(categoryId: String, newPosition: Double) = error("Not used")
}

class RecordingTransferFundsUseCase : TransferFundsUseCase {

    data class Call(
        val sourceId: TransferCounterpartyId,
        val sourceAmount: BigInteger,
        val destinationId: TransferCounterpartyId,
        val destinationAmount: BigInteger,
        val memo: String?,
        val dateTime: LocalDateTime,
        val transferId: String,
    )

    val calls = mutableListOf<Call>()
    var failWith: Throwable? = null

    override suspend fun invoke(
        sourceId: TransferCounterpartyId,
        sourceAmount: BigInteger,
        destinationId: TransferCounterpartyId,
        destinationAmount: BigInteger,
        memo: String?,
        dateTime: LocalDateTime,
        transferId: String,
    ): Result<Unit> {
        calls += Call(sourceId, sourceAmount, destinationId, destinationAmount, memo, dateTime, transferId)
        return failWith?.let(Result.Companion::failure) ?: Result.success(Unit)
    }
}

class FakeTransferHistoryRepository(
    var transfers: List<Transfer> = emptyList(),
    var failWith: Throwable? = null,
) : TransferHistoryRepository {

    override suspend fun getTransferHistoryPage(
        cursor: TransferHistoryPage.Cursor?,
        limit: Int,
        withinPeriod: HistoryPeriod,
        counterpartyIds: Set<String>?,
    ): TransferHistoryPage {
        failWith?.let { throw it }
        return TransferHistoryPage(
            data = transfers.take(limit),
            nextPageCursor = null,
            previousPageCursor = null,
        )
    }

    override fun getTransferHistoryPagingSource(
        withinPeriod: HistoryPeriod,
        counterpartyIds: Set<String>?,
    ) = error("Not used")

    override suspend fun getTransfer(transferId: String) = error("Not used")

    override suspend fun getTransferOrNull(transferId: String): Transfer? = error("Not used")
}
