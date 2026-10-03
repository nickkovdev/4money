package ua.com.radiokot.money.inbox.logic

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.EUR
import ua.com.radiokot.money.inbox.USD
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.testAccount
import ua.com.radiokot.money.inbox.testCategory
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal
import java.math.BigInteger

class InboxTransferPrefillTest {

    private val item = InboxItem(
        id = "item",
        receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
        sourcePackage = "se.seb.latvia",
        rawText = "x",
        amount = BigDecimal("2.12"),
        currencyCode = "EUR",
        payee = "DEEPSEERWEA ",
        cardLast4 = "0000",
        accountId = "acc",
        status = InboxItem.Status.Pending,
        transferId = null,
        dedupHash = "h",
    )

    @Test
    fun sameCurrencyPrefillsAmounts() {
        val route = InboxTransferPrefill.buildRoute(item, testAccount("acc", EUR), testCategory("cat", EUR))

        assertEquals(TransferCounterpartyId.Account("acc"), route.sourceId)
        assertEquals(TransferCounterpartyId.Category("cat", null), route.destinationId)
        assertEquals(BigInteger("212"), route.sourceAmount)
        assertEquals(BigInteger("212"), route.destinationAmount)
        assertEquals("DEEPSEERWEA", route.memo)
        assertEquals(LocalDateTime(2026, 10, 2, 8, 6), route.dateTime)
        assertEquals("item", route.inboxItemId)
        assertEquals("deepseerwea", route.rememberPayee)
        assertEquals("DEEPSEERWEA", route.rememberPayeeDisplayName)
    }

    @Test
    fun foreignCurrencyLeavesAmountsEmptyAndShowsOriginal() {
        val route = InboxTransferPrefill.buildRoute(
            item.copy(currencyCode = "USD"),
            testAccount("acc", EUR),
            testCategory("cat", EUR),
        )

        assertNull(route.sourceAmount)
        assertNull(route.destinationAmount)
        assertEquals("DEEPSEERWEA · 2,12 USD", route.memo)
    }

    @Test
    fun categoryInOtherCurrencyPrefillsSourceOnly() {
        val route = InboxTransferPrefill.buildRoute(item, testAccount("acc", EUR), testCategory("cat", USD))

        assertEquals(BigInteger("212"), route.sourceAmount)
        assertNull(route.destinationAmount)
    }

    @Test
    fun displayAmountTextFollowsLocale() {
        val big = item.copy(amount = BigDecimal("1234.5"))
        val ru = java.util.Locale.forLanguageTag("ru")
        val ruGrouping = java.text.DecimalFormatSymbols.getInstance(ru).groupingSeparator

        assertTrue(Character.isSpaceChar(ruGrouping))
        assertEquals("1,234.50 EUR", big.displayAmountText(java.util.Locale.ENGLISH))
        assertEquals("1${ruGrouping}234,50 EUR", big.displayAmountText(ru))
        assertEquals("2.12 EUR", item.displayAmountText(java.util.Locale.ENGLISH))
        assertEquals("EUR", item.copy(amount = null).displayAmountText(ru))
        assertEquals("", item.copy(amount = null, currencyCode = null).displayAmountText(ru))
    }

    @Test
    fun originalAmountTextUsesDecimalCommaAndCode() {
        assertEquals("2,12 EUR", item.originalAmountText())
        assertEquals("EUR", item.copy(amount = null).originalAmountText())
        assertEquals("", item.copy(amount = null, currencyCode = null).originalAmountText())
    }

    @Test
    fun incomingItemGoesFromCategoryToAccount() {
        val route = InboxTransferPrefill.buildRoute(
            item.copy(direction = InboxItem.Direction.Incoming),
            testAccount("acc", EUR),
            testCategory("cat", EUR, isIncome = true),
        )

        assertEquals(TransferCounterpartyId.Category("cat", null), route.sourceId)
        assertEquals(TransferCounterpartyId.Account("acc"), route.destinationId)
        assertEquals(BigInteger("212"), route.sourceAmount)
        assertEquals(BigInteger("212"), route.destinationAmount)
        assertEquals("deepseerwea", route.rememberPayee)
    }

    @Test
    fun incomingItemWithCategoryInOtherCurrencyPrefillsAccountSideOnly() {
        val route = InboxTransferPrefill.buildRoute(
            item.copy(direction = InboxItem.Direction.Incoming),
            testAccount("acc", EUR),
            testCategory("cat", USD, isIncome = true),
        )

        assertNull(route.sourceAmount)
        assertEquals(BigInteger("212"), route.destinationAmount)
    }
}
