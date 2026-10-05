package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.AccountRef
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.CategoryRef
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.PendingReason
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.Resolution
import java.math.BigDecimal
import java.math.BigInteger

class AutoExpenseResolverTest {

    private val payment = ParsedBankNotification.Payment(
        amount = BigDecimal("2.12"),
        currencyCode = "EUR",
        cardLast4 = "0000",
        payee = "DEEPSEERWEA",
    )
    private val rule = PayeeRule(
        payeePattern = "deepseerwea",
        matchType = PayeeRule.MatchType.Exact,
        categoryId = "cat",
        subcategoryId = "sub",
        accountId = null,
        hits = 0,
        lastUsedAt = null,
        id = "rule",
    )
    private val eurAccount = AccountRef(id = "acc", currencyCode = "EUR", precision = 2)
    private val eurCategory = CategoryRef(categoryId = "cat", subcategoryId = "sub", currencyCode = "EUR", precision = 2)

    @Test
    fun createsInMinorUnits() {
        assertEquals(
            Resolution.Create(
                rule = rule,
                account = eurAccount,
                category = eurCategory,
                accountAmount = BigInteger("212"),
                categoryAmount = BigInteger("212"),
            ),
            AutoExpenseResolver.resolve(payment, rule, eurAccount, eurCategory),
        )
    }

    @Test
    fun pendingReasons() {
        assertEquals(Resolution.Pending(PendingReason.NotParsed), AutoExpenseResolver.resolve(null, rule, eurAccount, eurCategory))
        assertEquals(Resolution.Pending(PendingReason.NoRule), AutoExpenseResolver.resolve(payment, null, eurAccount, null))
        assertEquals(Resolution.Pending(PendingReason.NoAccount), AutoExpenseResolver.resolve(payment, rule, null, eurCategory))
        assertEquals(Resolution.Pending(PendingReason.CategoryMissing), AutoExpenseResolver.resolve(payment, rule, eurAccount, null))
    }

    @Test
    fun foreignCurrencyIsAlwaysPending() {
        val usdPayment = payment.copy(currencyCode = "USD")
        assertEquals(
            Resolution.Pending(PendingReason.ForeignCurrency),
            AutoExpenseResolver.resolve(usdPayment, rule, eurAccount, eurCategory),
        )
    }

    @Test
    fun categoryInOtherCurrencyIsPending() {
        assertEquals(
            Resolution.Pending(PendingReason.CategoryCurrencyMismatch),
            AutoExpenseResolver.resolve(payment, rule, eurAccount, eurCategory.copy(currencyCode = "USD")),
        )
    }

    @Test
    fun tooManyDecimalsIsPendingNotRounded() {
        assertEquals(
            Resolution.Pending(PendingReason.UnsupportedPrecision),
            AutoExpenseResolver.resolve(payment.copy(amount = BigDecimal("2.125")), rule, eurAccount, eurCategory),
        )
    }

    @Test
    fun minorUnits() {
        assertEquals(BigInteger("1500"), AutoExpenseResolver.toMinorUnits(BigDecimal("15"), 2))
        assertEquals(BigInteger("210"), AutoExpenseResolver.toMinorUnits(BigDecimal("2.1"), 2))
        assertEquals(BigInteger("123456"), AutoExpenseResolver.toMinorUnits(BigDecimal("1234.56"), 2))
        assertNull(AutoExpenseResolver.toMinorUnits(BigDecimal("2.125"), 2))
    }

    @Test
    fun categoryMustMatchPaymentDirection() {
        val incomeCategory = eurCategory.copy(isIncome = true)
        val incomingPayment = payment.copy(isIncoming = true)

        assertEquals(
            Resolution.Pending(PendingReason.CategoryDirectionMismatch),
            AutoExpenseResolver.resolve(payment, rule, eurAccount, incomeCategory),
        )
        assertEquals(
            Resolution.Pending(PendingReason.CategoryDirectionMismatch),
            AutoExpenseResolver.resolve(incomingPayment, rule, eurAccount, eurCategory),
        )
        assertTrue(AutoExpenseResolver.resolve(incomingPayment, rule, eurAccount, incomeCategory) is Resolution.Create)
    }

    @Test
    fun recordRuleIsPendingWhenRecordingIsOff() {
        assertEquals(
            Resolution.Pending(PendingReason.AutoRecordDisabled),
            AutoExpenseResolver.resolve(payment, rule, eurAccount, eurCategory, recordKnownPayees = false),
        )
        // Not matched: the earlier reasons still win.
        assertEquals(
            Resolution.Pending(PendingReason.NoRule),
            AutoExpenseResolver.resolve(payment, null, eurAccount, null, recordKnownPayees = false),
        )
    }

    @Test
    fun askRuleStaysAskRequestedWhenRecordingIsOff() {
        val askRule = rule.copy(action = PayeeRule.Action.Ask)
        assertEquals(
            Resolution.Pending(PendingReason.AskRequested),
            AutoExpenseResolver.resolve(payment, askRule, eurAccount, null, recordKnownPayees = false),
        )
    }
}
