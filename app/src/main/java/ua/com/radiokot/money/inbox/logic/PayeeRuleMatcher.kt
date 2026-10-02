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

object PayeeRuleMatcher {

    /**
     * @param normalizedPayee output of [PayeeNormalizer.normalize]
     *
     * @return the best rule: an exact match (most hits first),
     * otherwise the longest "contains" pattern (then most hits), otherwise null.
     */
    fun match(
        normalizedPayee: String,
        rules: List<PayeeRule>,
    ): PayeeRule? {
        if (normalizedPayee.isEmpty()) {
            return null
        }

        val exactMatch = rules
            .filter { rule ->
                rule.matchType == PayeeRule.MatchType.Exact
                        && rule.payeePattern == normalizedPayee
            }
            .maxByOrNull(PayeeRule::hits)

        if (exactMatch != null) {
            return exactMatch
        }

        return rules
            .filter { rule ->
                rule.matchType == PayeeRule.MatchType.Contains
                        && rule.payeePattern.isNotEmpty()
                        && normalizedPayee.contains(rule.payeePattern)
            }
            .maxWithOrNull(
                compareBy<PayeeRule> { it.payeePattern.length }
                    .thenBy(PayeeRule::hits)
            )
    }
}
