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
import java.util.UUID

/**
 * Maps a normalized payee to a category (and optionally an account).
 *
 * @param payeePattern normalized, see [ua.com.radiokot.money.inbox.logic.PayeeNormalizer]
 */
data class PayeeRule(
    val payeePattern: String,
    val matchType: MatchType,
    val categoryId: String,
    val subcategoryId: String?,
    val accountId: String?,
    val hits: Long,
    val lastUsedAt: LocalDateTime?,
    val id: String = UUID.randomUUID().toString(),
) {
    enum class MatchType(val slug: String) {
        Exact("exact"),
        Contains("contains"),
        ;

        companion object {
            fun fromSlug(slug: String): MatchType =
                entries.firstOrNull { it.slug == slug }
                    ?: throw IllegalArgumentException("Unknown match type slug '$slug'")
        }
    }
}
