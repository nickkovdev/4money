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
 * Tells which rule an accepted inbox card created, so its Undo can remove it.
 */
object LearnedRule {

    /**
     * @param rulesBefore the rules right before accepting
     * @param rulesAfter the rules after the item was completed with "Remember"
     * @param payeePattern the normalized payee that was remembered
     *
     * @return the ID of the plain exact rule for [payeePattern] that did not exist before,
     * null if the accept only re-pointed an existing rule (it must survive the Undo).
     */
    fun createdRuleId(
        rulesBefore: List<PayeeRule>,
        rulesAfter: List<PayeeRule>,
        payeePattern: String,
    ): String? {
        val idsBefore = rulesBefore.mapTo(mutableSetOf(), PayeeRule::id)
        return rulesAfter
            .firstOrNull { rule ->
                rule.id !in idsBefore
                        && rule.payeePattern == payeePattern
                        && rule.matchType == PayeeRule.MatchType.Exact
                        && rule.amountRange == null
            }
            ?.id
    }
}
