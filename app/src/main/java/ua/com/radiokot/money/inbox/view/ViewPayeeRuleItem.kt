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

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.inbox.data.PayeeRule

@Immutable
class ViewPayeeRuleItem(
    val pattern: String,
    val matchTypeText: String,
    val categoryTitle: String,
    val hits: Long,
    val key: String,
    val source: PayeeRule? = null,
) {
    /**
     * Patterns are stored normalized (lower case), shown with capitalized words.
     */
    val displayPattern: String =
        pattern
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }

    constructor(
        rule: PayeeRule,
        categoryTitle: String,
    ) : this(
        pattern = rule.payeePattern,
        matchTypeText = when (rule.matchType) {
            PayeeRule.MatchType.Exact -> "is"
            PayeeRule.MatchType.Contains -> "contains"
        },
        categoryTitle = categoryTitle,
        hits = rule.hits,
        key = rule.id,
        source = rule,
    )
}
