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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.PayeeRule

class LearnedRuleTest {

    private val containsChoice =
        PayeeRememberChoice("mcdonalds", PayeeRule.MatchType.Contains)

    private fun rule(id: String, pattern: String, matchType: PayeeRule.MatchType) = PayeeRule(
        payeePattern = pattern,
        matchType = matchType,
        categoryId = "food",
        subcategoryId = null,
        accountId = null,
        hits = 0,
        lastUsedAt = null,
        id = id,
    )

    @Test
    fun createdContainsRule_IsFound() {
        val after = listOf(rule("new", "mcdonalds", PayeeRule.MatchType.Contains))

        assertEquals("new", LearnedRule.createdRuleId(emptyList(), after, containsChoice))
    }

    @Test
    fun exactRuleWithSamePattern_IsNotMistakenForContains() {
        val after = listOf(rule("exact", "mcdonalds", PayeeRule.MatchType.Exact))

        assertNull(LearnedRule.createdRuleId(emptyList(), after, containsChoice))
    }

    @Test
    fun repointedContainsRule_IsNotCreated() {
        val existing = rule("old", "mcdonalds", PayeeRule.MatchType.Contains)

        assertNull(
            LearnedRule.createdRuleId(
                rulesBefore = listOf(existing),
                rulesAfter = listOf(existing.copy(categoryId = "cafe")),
                choice = containsChoice,
            )
        )
    }
}
