/* Copyright 2026 Nikita Kovalovs

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.InboxItem
import java.math.BigDecimal

class MirroredInboxPairTest {

    private fun item(
        id: String,
        direction: InboxItem.Direction,
        minute: Int = 51,
        amount: String? = "1.24",
        payee: String? = "JOHN DOE",
        currencyCode: String? = "EUR",
        sourcePackage: String = "se.seb.latvia",
        status: InboxItem.Status = InboxItem.Status.Pending,
    ) = InboxItem(
        id = id,
        receivedAt = LocalDateTime(2026, 11, 16, 1, minute),
        sourcePackage = sourcePackage,
        rawText = "",
        amount = amount?.let(::BigDecimal),
        currencyCode = currencyCode,
        payee = payee,
        cardLast4 = null,
        accountId = null,
        status = status,
        transferId = null,
        dedupHash = id,
        direction = direction,
    )

    private val outgoing = item("out", InboxItem.Direction.Outgoing)

    @Test
    fun incomingOfSameAmountAndPayeeMirrorsOutgoing() {
        val incoming = item("in", InboxItem.Direction.Incoming)
        assertEquals(incoming, MirroredInboxPair.findCounterpart(outgoing, listOf(incoming)))
        assertEquals(outgoing, MirroredInboxPair.findCounterpart(incoming, listOf(outgoing)))
    }

    @Test
    fun payeeIsComparedNormalized() {
        val incoming = item("in", InboxItem.Direction.Incoming, payee = "john  doe.")
        assertEquals(incoming, MirroredInboxPair.findCounterpart(outgoing, listOf(incoming)))
    }

    @Test
    fun amountScaleDoesNotMatter() {
        val incoming = item("in", InboxItem.Direction.Incoming, amount = "1.240")
        assertEquals(incoming, MirroredInboxPair.findCounterpart(outgoing, listOf(incoming)))
    }

    @Test
    fun sameDirectionIsNotAPair() {
        val another = item("another", InboxItem.Direction.Outgoing)
        assertNull(MirroredInboxPair.findCounterpart(outgoing, listOf(another)))
    }

    @Test
    fun differentAmountPayeeCurrencyOrSourceIsNotAPair() {
        listOf(
            item("in", InboxItem.Direction.Incoming, amount = "1.25"),
            item("in", InboxItem.Direction.Incoming, payee = "JANE DOE"),
            item("in", InboxItem.Direction.Incoming, currencyCode = "USD"),
            item("in", InboxItem.Direction.Incoming, sourcePackage = "other.bank"),
        ).forEach { candidate ->
            assertNull(candidate.toString(), MirroredInboxPair.findCounterpart(outgoing, listOf(candidate)))
        }
    }

    @Test
    fun unparsedItemsAreNotPaired() {
        val noAmount = item("in", InboxItem.Direction.Incoming, amount = null)
        val noPayee = item("in2", InboxItem.Direction.Incoming, payee = null)
        assertNull(MirroredInboxPair.findCounterpart(outgoing, listOf(noAmount, noPayee)))
        assertNull(
            MirroredInboxPair.findCounterpart(
                outgoing.copy(amount = null),
                listOf(item("in", InboxItem.Direction.Incoming)),
            )
        )
    }

    @Test
    fun onlyPendingItemsWithinTenMinutesArePaired() {
        val sorted = item("done", InboxItem.Direction.Incoming, status = InboxItem.Status.Done)
        val tooLate = item("late", InboxItem.Direction.Incoming, minute = 51 - 11)
        val inTime = item("in", InboxItem.Direction.Incoming, minute = 51 - 10)
        assertNull(MirroredInboxPair.findCounterpart(outgoing, listOf(sorted, tooLate)))
        assertEquals(inTime, MirroredInboxPair.findCounterpart(outgoing, listOf(tooLate, inTime)))
    }

    @Test
    fun closestInTimeIsChosen() {
        val earlier = item("earlier", InboxItem.Direction.Incoming, minute = 45)
        val closest = item("closest", InboxItem.Direction.Incoming, minute = 52)
        assertEquals(closest, MirroredInboxPair.findCounterpart(outgoing, listOf(earlier, closest)))
    }
}
