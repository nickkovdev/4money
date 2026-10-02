package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.FakeInboxRepository
import ua.com.radiokot.money.inbox.FakePayeeRuleRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal

class CompleteInboxItemUseCaseTest {

    private val inbox = FakeInboxRepository()
    private val rules = FakePayeeRuleRepository()
    private val useCase = CompleteInboxItemUseCase(inbox, rules)
    private val account = TransferCounterpartyId.Account("acc")
    private val category = TransferCounterpartyId.Category("cat", "sub")

    private fun addPendingItem() = runBlocking {
        inbox.addItem(
            InboxItem(
                id = "item",
                receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
                sourcePackage = "se.seb.latvia",
                rawText = "x",
                amount = BigDecimal("2.12"),
                currencyCode = "EUR",
                payee = "DEEPSEERWEA",
                cardLast4 = "0000",
                accountId = "acc",
                status = InboxItem.Status.Pending,
                transferId = null,
                dedupHash = "h",
            )
        )
    }

    @Test
    fun marksDoneAndLearnsRule() = runBlocking {
        addPendingItem()

        useCase("item", "tr", "deepseerwea", account, category).getOrThrow()

        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Done, item.status)
        assertEquals("tr", item.transferId)
        val rule = rules.rules.value.single()
        assertEquals("deepseerwea", rule.payeePattern)
        assertEquals(PayeeRule.MatchType.Exact, rule.matchType)
        assertEquals("cat", rule.categoryId)
        assertEquals("sub", rule.subcategoryId)
        assertEquals("acc", rule.accountId)
    }

    @Test
    fun relearningUpdatesTheSameRule() = runBlocking {
        addPendingItem()

        useCase("item", "tr1", "deepseerwea", account, category).getOrThrow()
        useCase("item", "tr2", "deepseerwea", account, TransferCounterpartyId.Category("cat2", null)).getOrThrow()

        assertEquals("cat2", rules.rules.value.single().categoryId)
    }

    @Test
    fun noRuleWhenNotRememberedOrNotAnExpense() = runBlocking {
        addPendingItem()

        useCase("item", "tr", null, account, category).getOrThrow()
        useCase("item", "tr", "deepseerwea", account, TransferCounterpartyId.Account("acc2")).getOrThrow()

        assertTrue(rules.rules.value.isEmpty())
    }
}
