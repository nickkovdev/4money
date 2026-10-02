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

package ua.com.radiokot.money.inbox.logic

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.data.AmountRange
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.view.RangeTexts
import ua.com.radiokot.money.inbox.view.describeRange
import java.math.BigDecimal
import java.util.Locale

class AskRuleTest {

    private val askRule = PayeeRule(
        payeePattern = "fuelstop",
        matchType = PayeeRule.MatchType.Exact,
        categoryId = null,
        subcategoryId = null,
        accountId = null,
        hits = 0,
        lastUsedAt = null,
        amountRange = AmountRange(min = BigDecimal("10"), max = BigDecimal("35")),
        action = PayeeRule.Action.Ask,
    )

    @Test
    fun askRule_KeepsThePaymentPending() {
        val resolution = AutoExpenseResolver.resolve(
            payment = ParsedBankNotification.Payment(
                amount = BigDecimal("18.40"),
                currencyCode = "EUR",
                payee = "FUELSTOP",
                cardLast4 = "0000",
            ),
            rule = askRule,
            account = AutoExpenseResolver.AccountRef(id = "acc", currencyCode = "EUR", precision = 2),
            category = null,
        )

        Assert.assertEquals(
            AutoExpenseResolver.Resolution.Pending(AutoExpenseResolver.PendingReason.AskRequested),
            resolution,
        )
    }

    @Test
    fun askRule_IsNotASuggestion() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = listOf(askRule),
            history = listOf(
                InboxCardSuggester.HistoryEntry("fuelstop", InboxCardSuggester.CategoryKey("moto", null)),
            ),
            amount = BigDecimal("18.40"),
        )

        Assert.assertEquals(
            InboxCardSuggester.CategoryKey("moto", null),
            result.suggestion?.category,
        )
        Assert.assertTrue(result.suggestion?.reason is InboxCardSuggester.Reason.PayeeHistory)
    }

    private val enTexts = RangeTexts(
        between = "%1\$s–%2\$s",
        upTo = "Up to %1\$s",
        under = "Under %1\$s",
        from = "From %1\$s",
        over = "Over %1\$s",
        any = "Any amount",
    )
    private val ruTexts = RangeTexts(
        between = "%1\$s–%2\$s",
        upTo = "до %1\$s",
        under = "меньше %1\$s",
        from = "от %1\$s",
        over = "больше %1\$s",
        any = "Любая сумма",
    )
    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun describeRange_Texts() {
        fun describe(range: AmountRange, code: String?) =
            describeRange(range, code, Locale.ENGLISH, enTexts)

        Assert.assertEquals("10–35 €", describe(AmountRange(BigDecimal("10"), max = BigDecimal("35.00")), "EUR"))
        Assert.assertEquals("Under 10 €", describe(AmountRange(null, max = BigDecimal("10")), "EUR"))
        Assert.assertEquals(
            "Up to 10",
            describe(AmountRange(null, max = BigDecimal("10"), isMaxInclusive = true), null),
        )
        Assert.assertEquals(
            "Over 35 €",
            describe(AmountRange(BigDecimal("35"), isMinInclusive = false, max = null), "EUR"),
        )
    }

    @Test
    fun describeRange_Russian() {
        fun describe(range: AmountRange, code: String?) =
            describeRange(range, code, ru, ruTexts)

        Assert.assertEquals(
            "до 10,5 €",
            describe(AmountRange(null, max = BigDecimal("10.50"), isMaxInclusive = true), "EUR"),
        )
        Assert.assertEquals(
            "меньше 10 €",
            describe(AmountRange(null, max = BigDecimal("10")), "EUR"),
        )
        Assert.assertEquals(
            "10–35,25 €",
            describe(AmountRange(BigDecimal("10"), max = BigDecimal("35.25")), "EUR"),
        )
        Assert.assertEquals(
            "больше 35 €",
            describe(AmountRange(BigDecimal("35"), isMinInclusive = false, max = null), "EUR"),
        )
    }
}
