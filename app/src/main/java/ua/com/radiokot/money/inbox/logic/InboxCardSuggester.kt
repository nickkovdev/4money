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

import ua.com.radiokot.money.inbox.data.PayeeRule

/**
 * Picks the category an inbox card suggests and a few alternatives,
 * from the payee rules and the recent history. Pure, no I/O.
 */
object InboxCardSuggester {

    data class CategoryKey(
        val categoryId: String,
        val subcategoryId: String?,
    )

    /**
     * A recent transfer reduced to what matters here.
     *
     * @param normalizedMemo the memo through [PayeeNormalizer.normalize], empty if none
     * @param category the category side of the transfer
     */
    data class HistoryEntry(
        val normalizedMemo: String,
        val category: CategoryKey,
    )

    sealed interface Reason {
        /** A payee rule matched. */
        data class Rule(val rule: PayeeRule) : Reason

        /** No rule, but this payee was recorded to this category before. */
        data class PayeeHistory(val count: Int) : Reason

        /** Nothing known about the payee: the most used category overall. */
        data object MostUsed : Reason
    }

    data class Suggestion(
        val category: CategoryKey,
        val reason: Reason,
    )

    data class Result(
        val suggestion: Suggestion?,
        /**
         * Other likely categories, most used for this payee first, then most used overall,
         * never including the suggested one.
         */
        val alternatives: List<CategoryKey>,
    )

    /**
     * @param normalizedPayee [PayeeNormalizer.normalize] of the card payee, empty if unknown
     * @param rules rules of the card direction (expense or income)
     * @param history recent transfers of the card direction, newest first
     * @param isUsable whether a category still exists and is not archived
     * @param amount the payment amount for amount range rules, null if unknown
     */
    fun suggest(
        normalizedPayee: String,
        rules: List<PayeeRule>,
        history: List<HistoryEntry>,
        isUsable: (CategoryKey) -> Boolean = { true },
        maxAlternatives: Int = 3,
        amount: java.math.BigDecimal? = null,
        ruleMatcher: (normalizedPayee: String, rules: List<PayeeRule>, amount: java.math.BigDecimal?) -> PayeeRule? =
            PayeeRuleMatcher::match,
    ): Result {
        val payeeRanking = rank(
            history
                .filter { normalizedPayee.isNotEmpty() && it.normalizedMemo == normalizedPayee }
                .map(HistoryEntry::category)
                .filter(isUsable)
        )
        val overallRanking = rank(
            history
                .map(HistoryEntry::category)
                .filter(isUsable)
        )

        // An Ask rule has no category to suggest: the history decides then.
        val rule = ruleMatcher(normalizedPayee, rules, amount)
            ?.takeIf { it.action == PayeeRule.Action.Record }
        val ruleCategory = rule
            ?.categoryId
            ?.let { CategoryKey(it, rule.subcategoryId) }
            ?.takeIf(isUsable)

        val suggestion: Suggestion? = when {
            rule != null && ruleCategory != null ->
                Suggestion(ruleCategory, Reason.Rule(rule))

            payeeRanking.isNotEmpty() ->
                Suggestion(
                    category = payeeRanking.first().first,
                    reason = Reason.PayeeHistory(payeeRanking.first().second),
                )

            overallRanking.isNotEmpty() ->
                Suggestion(overallRanking.first().first, Reason.MostUsed)

            else ->
                null
        }

        val alternatives = (payeeRanking.map { it.first } + overallRanking.map { it.first })
            .distinct()
            .filter { it != suggestion?.category }
            .take(maxAlternatives)

        return Result(
            suggestion = suggestion,
            alternatives = alternatives,
        )
    }

    /**
     * @return categories by use count, ties broken by the most recent use (the input is newest first).
     */
    private fun rank(categories: List<CategoryKey>): List<Pair<CategoryKey, Int>> {
        val counts = LinkedHashMap<CategoryKey, Int>()
        categories.forEach { key ->
            counts[key] = (counts[key] ?: 0) + 1
        }
        val firstSeenIndex = counts.keys.withIndex().associate { (index, key) -> key to index }
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<CategoryKey, Int>> { it.value }
                    .thenBy { firstSeenIndex.getValue(it.key) }
            )
            .map { it.key to it.value }
    }
}
