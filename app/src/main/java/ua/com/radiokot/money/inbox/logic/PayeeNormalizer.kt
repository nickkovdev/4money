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

import java.text.Normalizer
import java.util.Locale

object PayeeNormalizer {

    private val whitespaceRegex = Regex("\\s+")
    private val trailingTokens = setOf("riga", "rīga", "lv", "lva", "latvia")

    /**
     * @return the payee for display and memo: composed Unicode, single spaces,
     * no trailing punctuation, case kept.
     */
    fun displayName(rawPayee: String): String =
        Normalizer.normalize(rawPayee, Normalizer.Form.NFC)
            .replace(' ', ' ')
            .replace(' ', ' ')
            .replace(whitespaceRegex, " ")
            .trim()
            .trimEnd('.', ',', ';', ' ')

    /**
     * @return the rule key: [displayName] in lower case without trailing
     * terminal IDs (tokens with digits) and city/country tokens.
     * The first token is always kept.
     */
    fun normalize(rawPayee: String): String {
        val tokens = displayName(rawPayee)
            .lowercase(Locale.ROOT)
            .split(' ')
            .filter(String::isNotEmpty)
            .toMutableList()

        while (tokens.size > 1) {
            val last = tokens.last()
            if (last.any(Char::isDigit) || last in trailingTokens) {
                tokens.removeAt(tokens.lastIndex)
            } else {
                break
            }
        }

        return tokens.joinToString(" ")
    }

    /**
     * @return a user-typed pattern in the same form as [normalize] output,
     * but without removing any tokens.
     */
    fun normalizePattern(input: String): String =
        displayName(input).lowercase(Locale.ROOT)
}
