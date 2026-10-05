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
import ua.com.radiokot.money.inbox.data.InboxItem

/**
 * Pure helpers of the Inbox tab.
 */
object InboxTabItems {

    /**
     * Whether the item was received on the given local date.
     */
    fun isToday(receivedAt: LocalDateTime, today: LocalDate): Boolean =
        receivedAt.date == today

    /**
     * The done items received on [today], in the given order.
     */
    fun doneToday(items: List<InboxItem>, today: LocalDate): List<InboxItem> =
        items.filter { item ->
            item.status == InboxItem.Status.Done && isToday(item.receivedAt, today)
        }

    fun counts(
        pending: List<InboxItem>,
        done: List<InboxItem>,
        today: LocalDate,
    ) = Counts(
        pending = pending.size,
        doneToday = doneToday(done, today).size,
    )

    /**
     * The counts for the tab subtitle.
     */
    data class Counts(
        val pending: Int,
        val doneToday: Int,
    )

    /**
     * The text for the numeric badge of the tab, null when there is nothing to show.
     */
    fun badgeText(pendingCount: Long): String? =
        when {
            pendingCount <= 0L -> null
            pendingCount > MAX_BADGE -> "$MAX_BADGE+"
            else -> pendingCount.toString()
        }

    private const val MAX_BADGE = 99L
}
