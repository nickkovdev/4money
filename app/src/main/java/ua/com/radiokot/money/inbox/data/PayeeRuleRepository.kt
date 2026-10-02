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

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface PayeeRuleRepository {

    /**
     * @return all rules from the in-memory cache, refreshed on every rule change.
     */
    suspend fun getRules(): List<PayeeRule>

    fun getRulesFlow(): Flow<List<PayeeRule>>

    /**
     * Creates a plain rule (no amount range) or, if one with the same pattern
     * and match type exists, points it to the new category/account.
     * Range rules of the payee are left as is.
     */
    suspend fun saveRuleForPayee(
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        categoryId: String,
        subcategoryId: String?,
        accountId: String?,
    )

    suspend fun updateRule(
        ruleId: String,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
    )

    /**
     * Creates (null [ruleId]) or replaces a rule for the payee amounts in [amountRange].
     *
     * @param categoryId required for [PayeeRule.Action.Record], ignored for [PayeeRule.Action.Ask]
     */
    suspend fun saveRangeRule(
        ruleId: String?,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        amountRange: AmountRange,
        action: PayeeRule.Action,
        categoryId: String?,
        subcategoryId: String?,
    )

    suspend fun deleteRule(ruleId: String)

    suspend fun recordHit(
        ruleId: String,
        at: LocalDateTime,
    )
}
