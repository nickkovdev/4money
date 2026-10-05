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

package ua.com.radiokot.money.inbox.ask

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.PendingReason
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester

class PaymentQuestionTest {

    @Test
    fun asksOnlyForAskRules() {
        Assert.assertTrue(PaymentQuestion.shouldAsk(PendingReason.AskRequested))
        Assert.assertFalse(PaymentQuestion.shouldAsk(PendingReason.NoRule))
        listOf(
            PendingReason.NotParsed,
            PendingReason.NoAccount,
            PendingReason.ForeignCurrency,
            PendingReason.CategoryMissing,
            PendingReason.CategoryDirectionMismatch,
            PendingReason.CategoryCurrencyMismatch,
            PendingReason.UnsupportedPrecision,
            PendingReason.AutoRecordDisabled,
        ).forEach { reason ->
            Assert.assertFalse(reason.name, PaymentQuestion.shouldAsk(reason))
        }
    }

    @Test
    fun actions_SuggestionFirst_AtMostThree() {
        fun key(id: String) = InboxCardSuggester.CategoryKey(id, null)

        val actions = PaymentQuestion.actionCategories(
            InboxCardSuggester.Result(
                suggestion = InboxCardSuggester.Suggestion(key("moto"), InboxCardSuggester.Reason.MostUsed),
                alternatives = listOf(key("car"), key("food"), key("home")),
            )
        )

        Assert.assertEquals(listOf(key("moto"), key("car"), key("food")), actions)
    }

    @Test
    fun notificationIds_ArePositiveAndStable() {
        val id = PaymentQuestionNotifier.notificationIdOf("item-1")
        Assert.assertTrue(id > 0)
        Assert.assertEquals(id, PaymentQuestionNotifier.notificationIdOf("item-1"))
    }

    @Test
    fun `title hides the amount in privacy mode`() {
        Assert.assertEquals(
            "Fuelstop · −18.40 €",
            PaymentQuestion.notificationTitle("Fuelstop", "−18.40 €", isPrivate = false)
        )
        Assert.assertEquals(
            "Fuelstop",
            PaymentQuestion.notificationTitle("Fuelstop", "−18.40 €", isPrivate = true)
        )
    }
}
