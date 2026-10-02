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
import java.math.BigDecimal

object PayeeRuleMatcher {

    /**
     * @param normalizedPayee output of [PayeeNormalizer.normalize]
     * @param amount the payment amount in major units, null if unknown:
     * then only rules without an amount range apply
     *
     * @return the best rule. Patterns are tried from the most specific:
     * the exact payee, then "contains" patterns from the longest.
     * Within a pattern, a rule whose amount range contains [amount] wins
     * (the narrowest range if several do), otherwise the pattern's plain rule.
     * Ties are broken by hits. Null if nothing applies.
     */
    fun match(
        normalizedPayee: String,
        rules: List<PayeeRule>,
        amount: BigDecimal? = null,
    ): PayeeRule? {
        if (normalizedPayee.isEmpty()) {
            return null
        }

        val exactRules = rules.filter { rule ->
            rule.matchType == PayeeRule.MatchType.Exact
                    && rule.payeePattern == normalizedPayee
        }
        val containsGroups = rules
            .filter { rule ->
                rule.matchType == PayeeRule.MatchType.Contains
                        && rule.payeePattern.isNotEmpty()
                        && normalizedPayee.contains(rule.payeePattern)
            }
            .groupBy(PayeeRule::payeePattern)
            .entries
            .sortedByDescending { it.key.length }
            .map { it.value }

        return (listOf(exactRules) + containsGroups)
            .firstNotNullOfOrNull { group -> pickWithinPattern(group, amount) }
    }

    private fun pickWithinPattern(
        rules: List<PayeeRule>,
        amount: BigDecimal?,
    ): PayeeRule? {
        if (amount != null) {
            val rangeMatch = rules
                .filter { rule -> rule.amountRange?.contains(amount) == true }
                .minWithOrNull(
                    compareBy<PayeeRule, BigDecimal?>(nullsLast()) { it.amountRange?.width }
                        .thenByDescending(PayeeRule::hits)
                )
            if (rangeMatch != null) {
                return rangeMatch
            }
        }

        return rules
            .filter { it.amountRange == null }
            .maxByOrNull(PayeeRule::hits)
    }
}
