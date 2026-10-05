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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.data.InboxItem

class InboxTabItemsTest {

    private val today = LocalDate(2026, 10, 5)

    private fun item(
        id: String,
        receivedAt: LocalDateTime,
        status: InboxItem.Status = InboxItem.Status.Done,
    ) = InboxItem(
        id = id,
        receivedAt = receivedAt,
        sourcePackage = "com.example.bank",
        rawText = "x",
        amount = null,
        currencyCode = null,
        payee = "COFFEE POINT",
        cardLast4 = "0000",
        accountId = null,
        status = status,
        transferId = null,
        dedupHash = id,
    )

    @Test
    fun isToday_UsesTheLocalDateOfReceivedAt() {
        Assert.assertTrue(InboxTabItems.isToday(LocalDateTime(2026, 10, 5, 0, 0), today))
        Assert.assertTrue(InboxTabItems.isToday(LocalDateTime(2026, 10, 5, 23, 59), today))
        Assert.assertFalse(InboxTabItems.isToday(LocalDateTime(2026, 10, 4, 23, 59), today))
        Assert.assertFalse(InboxTabItems.isToday(LocalDateTime(2026, 10, 6, 0, 0), today))
    }

    @Test
    fun doneToday_ExcludesOtherDaysAndKeepsOrder() {
        val items = listOf(
            item("a", LocalDateTime(2026, 10, 5, 12, 30)),
            item("b", LocalDateTime(2026, 10, 4, 12, 30)),
            item("c", LocalDateTime(2026, 10, 5, 8, 1)),
        )

        Assert.assertEquals(
            listOf("a", "c"),
            InboxTabItems.doneToday(items, today).map(InboxItem::id),
        )
    }

    @Test
    fun doneToday_IgnoresNonDoneItems() {
        val items = listOf(
            item("a", LocalDateTime(2026, 10, 5, 12, 30), InboxItem.Status.Pending),
            item("b", LocalDateTime(2026, 10, 5, 12, 31), InboxItem.Status.Dismissed),
            item("c", LocalDateTime(2026, 10, 5, 12, 32)),
        )

        Assert.assertEquals(
            listOf("c"),
            InboxTabItems.doneToday(items, today).map(InboxItem::id),
        )
    }

    @Test
    fun counts_ArePendingAndDoneToday() {
        val counts = InboxTabItems.counts(
            pending = listOf(
                item("p1", LocalDateTime(2026, 10, 5, 9, 0), InboxItem.Status.Pending),
                item("p2", LocalDateTime(2026, 10, 3, 9, 0), InboxItem.Status.Pending),
            ),
            done = listOf(
                item("d1", LocalDateTime(2026, 10, 5, 9, 0)),
                item("d2", LocalDateTime(2026, 10, 2, 9, 0)),
            ),
            today = today,
        )

        Assert.assertEquals(InboxTabItems.Counts(pending = 2, doneToday = 1), counts)
    }

    @Test
    fun badgeText_IsHiddenForNothingAndCappedAt99() {
        Assert.assertNull(InboxTabItems.badgeText(0))
        Assert.assertEquals("1", InboxTabItems.badgeText(1))
        Assert.assertEquals("99", InboxTabItems.badgeText(99))
        Assert.assertEquals("99+", InboxTabItems.badgeText(100))
    }
}
