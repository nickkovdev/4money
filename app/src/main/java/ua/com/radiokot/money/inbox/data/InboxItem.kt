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

package ua.com.radiokot.money.inbox.data

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

/**
 * A bank notification kept for review or as a record of an auto-created expense.
 *
 * @param receivedAt notification post time, local wall-clock
 * @param amount decimal amount as written by the bank, null if not parsed
 * @param accountId account resolved from the card hint at receive time
 * @param transferId the transfer created from this item, if [status] is [Status.Done]
 * @param direction whether the money left the account (an expense) or came to it (an income)
 */
data class InboxItem(
    val id: String,
    val receivedAt: LocalDateTime,
    val sourcePackage: String,
    val rawText: String,
    val amount: BigDecimal?,
    val currencyCode: String?,
    val payee: String?,
    val cardLast4: String?,
    val accountId: String?,
    val status: Status,
    val transferId: String?,
    val dedupHash: String,
    val direction: Direction = Direction.Outgoing,
) {
    enum class Direction(val slug: String) {
        Outgoing("outgoing"),
        Incoming("incoming"),
        ;

        companion object {
            /**
             * Rows created before the direction was introduced are outgoing.
             */
            fun fromSlug(slug: String?): Direction =
                entries.firstOrNull { it.slug == slug }
                    ?: Outgoing
        }
    }

    enum class Status(val slug: String) {
        Pending("pending"),
        Done("done"),
        Dismissed("dismissed"),
        ;

        companion object {
            fun fromSlug(slug: String): Status =
                entries.firstOrNull { it.slug == slug }
                    ?: throw IllegalArgumentException("Unknown inbox status slug '$slug'")
        }
    }
}
