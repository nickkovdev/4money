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

import kotlinx.datetime.LocalDateTime
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.inbox.USD
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.testAccount
import ua.com.radiokot.money.inbox.testCategory
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal
import java.math.BigInteger

class InboxCardAcceptanceTest {

    private fun item(
        amount: String? = "18.90",
        currencyCode: String? = "EUR",
        direction: InboxItem.Direction = InboxItem.Direction.Outgoing,
    ) = InboxItem(
        id = "item",
        receivedAt = LocalDateTime(2026, 10, 2, 11, 55),
        sourcePackage = "se.seb.latvia",
        rawText = "x",
        amount = amount?.let(::BigDecimal),
        currencyCode = currencyCode,
        payee = "FUELSTOP ",
        cardLast4 = "0000",
        accountId = "acc",
        status = InboxItem.Status.Pending,
        transferId = null,
        dedupHash = "h",
        direction = direction,
    )

    @Test
    fun sameCurrencyExpense_IsRecorded() {
        val decision = InboxCardAcceptance.decide(
            item = item(),
            account = testAccount("acc"),
            category = testCategory("food"),
            subcategory = null,
        )

        Assert.assertEquals(
            InboxCardAcceptance.Decision.Record(
                sourceId = TransferCounterpartyId.Account("acc"),
                sourceAmount = BigInteger("1890"),
                destinationId = TransferCounterpartyId.Category("food", null),
                destinationAmount = BigInteger("1890"),
                memo = "FUELSTOP",
            ),
            decision,
        )
    }

    @Test
    fun income_GoesFromCategoryToAccount() {
        val decision = InboxCardAcceptance.decide(
            item = item(direction = InboxItem.Direction.Incoming),
            account = testAccount("acc"),
            category = testCategory("salary", isIncome = true),
            subcategory = null,
        ) as InboxCardAcceptance.Decision.Record

        Assert.assertEquals(TransferCounterpartyId.Category("salary", null), decision.sourceId)
        Assert.assertEquals(TransferCounterpartyId.Account("acc"), decision.destinationId)
    }

    @Test
    fun subcategory_IsKept() {
        val decision = InboxCardAcceptance.decide(
            item = item(),
            account = testAccount("acc"),
            category = testCategory("food"),
            subcategory = Subcategory(
                title = "Snacks",
                categoryId = "food",
                position = 0.0,
                id = "snacks",
            ),
        ) as InboxCardAcceptance.Decision.Record

        Assert.assertEquals(TransferCounterpartyId.Category("food", "snacks"), decision.destinationId)
    }

    @Test
    fun foreignCurrency_OpensSheet() {
        val decision = InboxCardAcceptance.decide(
            item = item(currencyCode = "USD"),
            account = testAccount("acc"),
            category = testCategory("food"),
            subcategory = null,
        )

        Assert.assertTrue(decision is InboxCardAcceptance.Decision.OpenSheet)
        Assert.assertEquals("item", (decision as InboxCardAcceptance.Decision.OpenSheet).route.inboxItemId)
    }

    @Test
    fun categoryInOtherCurrency_OpensSheet() {
        val decision = InboxCardAcceptance.decide(
            item = item(),
            account = testAccount("acc"),
            category = testCategory("food", currency = USD),
            subcategory = null,
        )

        Assert.assertTrue(decision is InboxCardAcceptance.Decision.OpenSheet)
    }

    @Test
    fun unparsedOrTooPreciseAmount_OpensSheet() {
        listOf(null, "1.234").forEach { amount ->
            val decision = InboxCardAcceptance.decide(
                item = item(amount = amount),
                account = testAccount("acc"),
                category = testCategory("food"),
                subcategory = null,
            )

            Assert.assertTrue("amount=$amount", decision is InboxCardAcceptance.Decision.OpenSheet)
        }
    }
}
