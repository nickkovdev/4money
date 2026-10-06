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
 * Suggests which words of a payee to remember: the leading words it shares
 * with other known payees of the same category ("mcdonalds" for
 * "mcdonalds akropole" when "mcdonalds alfa" is also food), or the whole payee.
 */
object PayeeRulePatternSuggester {

    /**
     * A payee (or a rule pattern) known to belong to a category.
     */
    data class KnownPayee(
        val normalizedPayee: String,
        val categoryId: String,
    )

    private val genericTokens = setOf(
        "sia", "uab", "ltd", "llc", "inc", "gmbh", "plc", "ooo", "the", "www", "com",
    )

    private fun isSignificant(token: String): Boolean =
        token.length > 2
                && !token.all(Char::isDigit)
                && token !in genericTokens

    /**
     * @return how many leading words to preselect, 1..word count (= whole payee);
     * 0 for an empty payee.
     */
    fun suggestWordCount(
        normalizedPayee: String,
        categoryId: String,
        known: Collection<KnownPayee>,
    ): Int {
        val words = PayeeWordSelection.words(normalizedPayee)
        if (words.isEmpty()) {
            return 0
        }
        val joined = words.joinToString(" ")

        val best = known
            .asSequence()
            .filter { it.categoryId == categoryId }
            .map { PayeeWordSelection.words(it.normalizedPayee) }
            .filter { it.isNotEmpty() && it.joinToString(" ") != joined }
            .map { otherWords -> significantSharedRun(words, otherWords) }
            .maxOrNull()
            ?: 0

        return if (best in 1 until words.size)
            best
        else
            words.size
    }

    /**
     * @return the common leading word count trimmed of trailing insignificant tokens,
     * 0 if no significant token is left.
     */
    private fun significantSharedRun(
        words: List<String>,
        otherWords: List<String>,
    ): Int {
        var count = words
            .zip(otherWords)
            .takeWhile { (a, b) -> a == b }
            .size
        while (count > 0 && !isSignificant(words[count - 1])) {
            count--
        }
        // Trimming stops at a significant token, so a non-zero run has one.
        return count
    }

    /**
     * @return Record rules with a category, plus history entries with a non-empty memo; distinct.
     */
    fun knownPayees(
        rules: List<PayeeRule>,
        history: List<InboxCardSuggester.HistoryEntry>,
    ): List<KnownPayee> {
        val fromRules = rules
            .asSequence()
            .filter { it.action == PayeeRule.Action.Record }
            .mapNotNull { rule ->
                rule.categoryId?.let { KnownPayee(rule.payeePattern, it) }
            }
        val fromHistory = history
            .asSequence()
            .filter { it.normalizedMemo.isNotBlank() }
            .map { KnownPayee(it.normalizedMemo, it.category.categoryId) }

        return (fromRules + fromHistory)
            .distinct()
            .toList()
    }

    /**
     * @return the default selection for remembering [normalizedPayee] into [categoryId],
     * null for an empty payee.
     */
    fun defaultSelection(
        normalizedPayee: String,
        categoryId: String,
        known: Collection<KnownPayee>,
    ): PayeeWordSelection? =
        PayeeWordSelection.leading(
            normalizedPayee = normalizedPayee,
            leadingWordCount = suggestWordCount(normalizedPayee, categoryId, known),
        )
}
