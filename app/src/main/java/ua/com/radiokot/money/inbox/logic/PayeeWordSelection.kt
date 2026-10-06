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
 * A contiguous run of at least one word of a normalized payee.
 *
 * @param first index of the first selected word
 * @param last index of the last selected word, inclusive
 */
data class PayeeWordSelection(
    val words: List<String>,
    val first: Int,
    val last: Int,
) {
    init {
        require(words.isNotEmpty()) {
            "A selection needs at least one word"
        }
        require(first in 0..last && last < words.size) {
            "The selection $first..$last is out of 0..${words.lastIndex}"
        }
    }

    val isWhole: Boolean
        get() = first == 0 && last == words.lastIndex

    fun isSelected(index: Int): Boolean =
        index in first..last

    /**
     * Tapping an unselected word extends the run to include it,
     * tapping the first or the last selected word removes it
     * unless it is the only one, tapping an interior selected word
     * or an index out of range changes nothing.
     */
    fun toggle(index: Int): PayeeWordSelection = when {
        index !in words.indices ->
            this

        index < first ->
            copy(first = index)

        index > last ->
            copy(last = index)

        first == last ->
            this

        index == first ->
            copy(first = first + 1)

        index == last ->
            copy(last = last - 1)

        else ->
            this
    }

    private val selectedText: String
        get() = words.subList(first, last + 1).joinToString(" ")

    /**
     * Whether a part of the payee is selected that has no significant token,
     * like "rig" or "22": such a rule would match far too many payees.
     */
    val isTooBroad: Boolean
        get() = !isWhole
                && words.subList(first, last + 1).none(PayeeRulePatternSuggester::isSignificant)

    /**
     * «mcdonalds …» argument of the hint, null when the whole payee is selected
     * or the selected part is too broad (the choice is exact then).
     */
    val hintPattern: String?
        get() =
            if (isWhole || isTooBroad)
                null
            else
                buildString {
                    if (first > 0) {
                        append("… ")
                    }
                    append(selectedText)
                    if (last < words.lastIndex) {
                        append(" …")
                    }
                }

    /**
     * @return an exact choice for the whole payee (also if the selected part is too broad),
     * a contains one for a part of it.
     */
    fun toChoice(): PayeeRememberChoice =
        if (isWhole || isTooBroad)
            PayeeRememberChoice(
                pattern = words.joinToString(" "),
                matchType = PayeeRule.MatchType.Exact,
            )
        else
            PayeeRememberChoice(
                pattern = PayeeNormalizer.normalizePattern(selectedText),
                matchType = PayeeRule.MatchType.Contains,
            )

    companion object {

        internal fun words(normalizedPayee: String): List<String> =
            normalizedPayee
                .split(' ')
                .filter(String::isNotEmpty)

        /**
         * @return selection of the leading words, null for an empty payee;
         * [leadingWordCount] is clamped to 1..words count.
         */
        fun leading(
            normalizedPayee: String,
            leadingWordCount: Int,
        ): PayeeWordSelection? {
            val words = words(normalizedPayee)
            if (words.isEmpty()) {
                return null
            }
            return PayeeWordSelection(
                words = words,
                first = 0,
                last = leadingWordCount.coerceIn(1, words.size) - 1,
            )
        }

        /**
         * @return selection of all the words, null for an empty payee.
         */
        fun whole(normalizedPayee: String): PayeeWordSelection? =
            leading(normalizedPayee, Int.MAX_VALUE)
    }
}
