package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.inbox.FakeAccountRepository
import ua.com.radiokot.money.inbox.FakeCategoryRepository
import ua.com.radiokot.money.inbox.FakeInboxRepository
import ua.com.radiokot.money.inbox.FakePayeeRuleRepository
import ua.com.radiokot.money.inbox.RecordingTransferFundsUseCase
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.PendingReason
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase.Outcome
import ua.com.radiokot.money.inbox.testAccount
import ua.com.radiokot.money.inbox.testCategory
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProcessBankNotificationUseCaseTest {

    private val postTime = Instant.parse("2026-10-02T08:06:00Z").toEpochMilliseconds()
    private val eurPayment = IncomingBankNotification(
        packageName = "se.seb.latvia",
        postTimeMillis = postTime,
        title = "Jauna rezervācija",
        text = "Jūs samaksājāt 2,12 EUR par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .",
    )
    private val rule = PayeeRule(
        payeePattern = "deepseerwea",
        matchType = PayeeRule.MatchType.Exact,
        categoryId = "cat-food",
        subcategoryId = null,
        accountId = null,
        hits = 3,
        lastUsedAt = null,
        id = "rule-1",
    )

    private val inbox = FakeInboxRepository()
    private val transfers = RecordingTransferFundsUseCase()
    private var nextId = 0

    private fun useCase(
        rules: List<PayeeRule> = listOf(rule),
        categories: List<Category> = listOf(testCategory("cat-food")),
        ruleRepository: FakePayeeRuleRepository = FakePayeeRuleRepository(rules),
    ) = ProcessBankNotificationUseCase(
        parsers = listOf(SebLatviaNotificationParser()),
        inboxRepository = inbox,
        payeeRuleRepository = ruleRepository,
        accountRepository = FakeAccountRepository(listOf(testAccount("acc-main"))),
        categoryRepository = FakeCategoryRepository(categories),
        cardAccountResolver = object : CardAccountResolver {
            override suspend fun resolve(cardLast4: String?, ruleAccountId: String?) =
                ruleAccountId ?: "acc-main"
        },
        transferFundsUseCase = transfers,
        timeZone = TimeZone.UTC,
        newId = { "id-${nextId++}" },
    )

    @Test
    fun autoRecordsWhenRuleMatches() = runBlocking {
        val ruleRepository = FakePayeeRuleRepository(listOf(rule))

        val outcome = useCase(ruleRepository = ruleRepository).invoke(eurPayment).getOrThrow()

        val call = transfers.calls.single()
        assertEquals(Outcome.AutoRecorded(itemId = "id-0", transferId = call.transferId, ruleId = "rule-1"), outcome)
        assertEquals(TransferCounterpartyId.Account("acc-main"), call.sourceId)
        assertEquals(TransferCounterpartyId.Category("cat-food", null), call.destinationId)
        assertEquals(BigInteger("212"), call.sourceAmount)
        assertEquals(BigInteger("212"), call.destinationAmount)
        assertEquals("DEEPSEERWEA", call.memo)
        // Post time, not the time in the text.
        assertEquals(LocalDateTime(2026, 10, 2, 8, 6), call.dateTime)

        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Done, item.status)
        assertEquals(call.transferId, item.transferId)
        assertEquals("rule-1" to LocalDateTime(2026, 10, 2, 8, 6), ruleRepository.hits.single())
    }

    @Test
    fun pendingWithoutRule() = runBlocking {
        val outcome = useCase(rules = emptyList()).invoke(eurPayment).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.NoRule), outcome)
        assertTrue(transfers.calls.isEmpty())
        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Pending, item.status)
        assertEquals(BigDecimal("2.12"), item.amount)
        assertEquals("EUR", item.currencyCode)
        assertEquals("DEEPSEERWEA", item.payee)
        assertEquals("0000", item.cardLast4)
        assertEquals("acc-main", item.accountId)
        assertNull(item.transferId)
    }

    @Test
    fun foreignCurrencyGoesToPending() = runBlocking {
        val usdPayment = eurPayment.copy(text = eurPayment.text.replace("EUR", "USD"))

        val outcome = useCase().invoke(usdPayment).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.ForeignCurrency), outcome)
        assertTrue(transfers.calls.isEmpty())
        assertEquals("USD", inbox.items.value.single().currencyCode)
    }

    @Test
    fun unrecognizedSebTextIsKeptRaw() = runBlocking {
        val other = eurPayment.copy(title = "SEB", text = "Jums ir jauns ziņojums internetbankā")

        val outcome = useCase().invoke(other).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.NotParsed), outcome)
        val item = inbox.items.value.single()
        assertEquals("SEB\nJums ir jauns ziņojums internetbankā", item.rawText)
        assertNull(item.amount)
        assertNull(item.payee)
    }

    @Test
    fun duplicateIsSkipped() = runBlocking {
        val processor = useCase()

        processor(eurPayment).getOrThrow()
        val repost = processor(eurPayment.copy(postTimeMillis = postTime + 5_000)).getOrThrow()

        assertEquals(Outcome.Duplicate, repost)
        assertEquals(1, inbox.items.value.size)
        assertEquals(1, transfers.calls.size)
    }

    @Test
    fun failedTransferLeavesOnePendingItemAndRepostIsDuplicate() = runBlocking {
        val processor = useCase()
        transfers.failWith = IllegalStateException("boom")

        val result = processor(eurPayment)

        assertTrue(result.isFailure)
        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Pending, item.status)
        assertNull(item.transferId)

        transfers.failWith = null
        val repost = processor(eurPayment.copy(postTimeMillis = postTime + 5_000)).getOrThrow()

        assertEquals(Outcome.Duplicate, repost)
        assertEquals(1, inbox.items.value.size)
        assertEquals(1, transfers.calls.size)
    }

    @Test
    fun otherPackagesAreIgnored() = runBlocking {
        val wallet = eurPayment.copy(packageName = "com.google.android.apps.walletnfcrel")

        assertEquals(Outcome.Ignored, useCase().invoke(wallet).getOrThrow())
        assertTrue(inbox.items.value.isEmpty())
    }

    @Test
    fun archivedRuleCategoryGoesToPending() = runBlocking {
        val outcome = useCase(categories = listOf(testCategory("cat-food", isArchived = true)))
            .invoke(eurPayment).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.CategoryMissing), outcome)
        assertTrue(transfers.calls.isEmpty())
    }
}
