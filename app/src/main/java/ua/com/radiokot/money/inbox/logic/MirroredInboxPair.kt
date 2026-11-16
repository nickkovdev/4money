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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import ua.com.radiokot.money.inbox.data.InboxItem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Finds the other half of money moved between the user's own accounts,
 * e.g. a round-up to a savings account: the bank notifies of the same amount
 * leaving and coming back from the same payee within minutes.
 * Such a pair is not an expense nor an income, so it does not belong to the inbox.
 */
object MirroredInboxPair {

    val MAX_TIME_APART: Duration = 10.minutes

    /**
     * @return the pending item mirroring [item], the closest in time if there are several, or null
     */
    @OptIn(ExperimentalTime::class)
    fun findCounterpart(
        item: InboxItem,
        pendingItems: List<InboxItem>,
    ): InboxItem? {
        val amount = item.amount ?: return null
        val payee = item.payee?.let(PayeeNormalizer::normalize) ?: return null

        return pendingItems
            .filter { other ->
                other.id != item.id
                        && other.status == InboxItem.Status.Pending
                        && other.direction != item.direction
                        && other.sourcePackage == item.sourcePackage
                        && other.currencyCode == item.currencyCode
                        && other.amount?.compareTo(amount) == 0
                        && other.payee?.let(PayeeNormalizer::normalize) == payee
                        && timeApart(other.receivedAt, item.receivedAt) <= MAX_TIME_APART
            }
            .minByOrNull { timeApart(it.receivedAt, item.receivedAt) }
    }

    @OptIn(ExperimentalTime::class)
    private fun timeApart(a: LocalDateTime, b: LocalDateTime): Duration =
        // Both are local wall-clock times of the same device, UTC keeps the difference.
        (a.toInstant(TimeZone.UTC) - b.toInstant(TimeZone.UTC)).absoluteValue
}
