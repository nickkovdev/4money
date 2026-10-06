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

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester.CategoryKey
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester.HistoryEntry
import ua.com.radiokot.money.inbox.logic.PayeeRulePatternSuggester.KnownPayee

class PayeeRulePatternSuggesterTest {

    private val known = listOf(
        KnownPayee("mcdonalds alfa", "food"),
        KnownPayee("mcdonalds giftcard shop", "gifts"),
        KnownPayee("sia example cafe", "food"),
        KnownPayee("example cafe old town", "cafe"),
    )

    @Test
    fun sharedLeadingWordInTheSameCategory() =
        Assert.assertEquals(1, PayeeRulePatternSuggester.suggestWordCount("mcdonalds akropole", "food", known))

    @Test
    fun otherCategoryDoesNotCount() =
        Assert.assertEquals(2, PayeeRulePatternSuggester.suggestWordCount("mcdonalds akropole", "transport", known))

    @Test
    fun longestSharedRunWins() = Assert.assertEquals(
        2, PayeeRulePatternSuggester.suggestWordCount(
            "example cafe new town", "cafe", known + KnownPayee("example bar", "cafe")
        )
    )

    @Test
    fun genericLeadingTokenAloneIsIgnored() = Assert.assertEquals(
        3, PayeeRulePatternSuggester.suggestWordCount(
            "sia other shop", "food", known
        )
    ) // shares only "sia"

    @Test
    fun genericTokenFollowedBySignificantOneIsKept() = Assert.assertEquals(
        2,
        PayeeRulePatternSuggester.suggestWordCount("sia example bakery", "food", known)
    ) // "sia example"

    @Test
    fun shortAndDigitTokensAreTrimmedFromTheEnd() = Assert.assertEquals(
        1,
        PayeeRulePatternSuggester.suggestWordCount(
            "bolt ab 22 x", "transport",
            listOf(KnownPayee("bolt ab 22 y", "transport"))
        )
    ) // "bolt ab 22" → "bolt"

    @Test
    fun samePayeeIsNotEvidence() = Assert.assertEquals(
        2,
        PayeeRulePatternSuggester.suggestWordCount("mcdonalds alfa", "food", known)
    )

    @Test
    fun singleWordPayeeIsWhole() = Assert.assertEquals(
        1,
        PayeeRulePatternSuggester.suggestWordCount("mcdonalds", "food", known)
    )

    @Test
    fun emptyPayee() =
        Assert.assertEquals(0, PayeeRulePatternSuggester.suggestWordCount("", "food", known))

    @Test
    fun containsRulePatternIsEvidence() = Assert.assertEquals(
        1,
        PayeeRulePatternSuggester.suggestWordCount(
            "taxi example 12", "transport",
            listOf(KnownPayee("taxi", "transport"))
        )
    )

    @Test
    fun knownPayeesFromRulesAndHistory() {
        val rules = listOf(
            PayeeRule(
                payeePattern = "mcdonalds alfa",
                matchType = PayeeRule.MatchType.Exact,
                categoryId = "food",
                subcategoryId = null,
                accountId = null,
                hits = 3,
                lastUsedAt = null,
            ),
            PayeeRule(
                payeePattern = "example cafe",
                matchType = PayeeRule.MatchType.Contains,
                categoryId = "cafe",
                subcategoryId = "coffee",
                accountId = null,
                hits = 1,
                lastUsedAt = null,
            ),
            PayeeRule(
                payeePattern = "example market",
                matchType = PayeeRule.MatchType.Exact,
                categoryId = null,
                subcategoryId = null,
                accountId = null,
                hits = 0,
                lastUsedAt = null,
                action = PayeeRule.Action.Ask,
            ),
        )
        val history = listOf(
            HistoryEntry("mcdonalds alfa", CategoryKey("food", null)),
            HistoryEntry("mcdonalds akropole", CategoryKey("food", "burgers")),
            HistoryEntry("mcdonalds akropole", CategoryKey("food", null)),
            HistoryEntry("", CategoryKey("transport", null)),
            HistoryEntry("example cafe old town", CategoryKey("cafe", null)),
        )

        Assert.assertEquals(
            listOf(
                KnownPayee("mcdonalds alfa", "food"),
                KnownPayee("example cafe", "cafe"),
                KnownPayee("mcdonalds akropole", "food"),
                KnownPayee("example cafe old town", "cafe"),
            ),
            PayeeRulePatternSuggester.knownPayees(rules, history)
        )
    }

    @Test
    fun defaultSelectionIsTheSuggestedLeadingRun() {
        Assert.assertEquals(
            PayeeWordSelection(listOf("mcdonalds", "akropole"), 0, 0),
            PayeeRulePatternSuggester.defaultSelection("mcdonalds akropole", "food", known)
        )
        Assert.assertEquals(
            PayeeWordSelection(listOf("mcdonalds", "akropole"), 0, 1),
            PayeeRulePatternSuggester.defaultSelection("mcdonalds akropole", "transport", known)
        )
        Assert.assertNull(PayeeRulePatternSuggester.defaultSelection("", "food", known))
    }
}
