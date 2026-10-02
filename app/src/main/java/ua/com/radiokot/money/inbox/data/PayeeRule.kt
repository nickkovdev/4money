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
import java.util.UUID

/**
 * Maps a normalized payee to a category (and optionally an account),
 * or to asking the user. A payee may have several rules with amount ranges
 * (a snack vs. fuel at the same station) and one plain rule without a range.
 *
 * @param payeePattern normalized, see [ua.com.radiokot.money.inbox.logic.PayeeNormalizer]
 * @param categoryId null only for [Action.Ask]
 * @param amountRange the payment amounts this rule is for, null for any amount
 */
data class PayeeRule(
    val payeePattern: String,
    val matchType: MatchType,
    val categoryId: String?,
    val subcategoryId: String?,
    val accountId: String?,
    val hits: Long,
    val lastUsedAt: LocalDateTime?,
    val id: String = UUID.randomUUID().toString(),
    val amountRange: AmountRange? = null,
    val action: Action = Action.Record,
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

    enum class Action(val slug: String) {
        /**
         * Record to the rule category without asking.
         */
        Record("record"),

        /**
         * Keep the payment pending and ask in a notification.
         */
        Ask("ask"),
        ;

        companion object {
            /**
             * Rows written before actions existed record.
             */
            fun fromSlug(slug: String?): Action =
                entries.firstOrNull { it.slug == slug }
                    ?: Record
        }
    }
}

/**
 * Payment amounts in the payment currency, in major units ("10.00" for 10 EUR).
 * A missing edge is unbounded.
 */
data class AmountRange(
    val min: BigDecimal?,
    val isMinInclusive: Boolean = true,
    val max: BigDecimal?,
    val isMaxInclusive: Boolean = false,
) {
    init {
        require(min != null || max != null) {
            "A range needs at least one edge"
        }
        require(min == null || max == null || min <= max) {
            "min must not exceed max"
        }
    }

    operator fun contains(amount: BigDecimal): Boolean {
        val aboveMin = min == null
                || (if (isMinInclusive) amount >= min else amount > min)
        val belowMax = max == null
                || (if (isMaxInclusive) amount <= max else amount < max)
        return aboveMin && belowMax
    }

    /**
     * For picking the narrowest of overlapping ranges, unbounded is the widest.
     */
    val width: BigDecimal?
        get() =
            if (min != null && max != null)
                max - min
            else
                null

    /**
     * The bounds in interval notation, as stored: "[)", "[]", "(]" or "()".
     */
    val boundsSlug: String
        get() = (if (isMinInclusive) "[" else "(") + (if (isMaxInclusive) "]" else ")")

    companion object {
        const val DEFAULT_BOUNDS_SLUG = "[)"

        /**
         * @return a range from the stored columns, null if both edges are missing.
         */
        fun fromColumns(
            min: BigDecimal?,
            max: BigDecimal?,
            boundsSlug: String?,
        ): AmountRange? {
            if (min == null && max == null) {
                return null
            }
            val bounds = boundsSlug
                ?.takeIf { it.length == 2 }
                ?: DEFAULT_BOUNDS_SLUG
            return AmountRange(
                min = min,
                isMinInclusive = bounds[0] == '[',
                max = max,
                isMaxInclusive = bounds[1] == ']',
            )
        }
    }
}
