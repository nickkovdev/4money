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

class InboxCardSuggesterTest {

    private val food = CategoryKey("food", null)
    private val car = CategoryKey("car", null)
    private val moto = CategoryKey("moto", null)
    private val home = CategoryKey("home", null)
    private val snacks = CategoryKey("food", "snacks")

    private fun rule(pattern: String, category: CategoryKey) = PayeeRule(
        payeePattern = pattern,
        matchType = PayeeRule.MatchType.Exact,
        categoryId = category.categoryId,
        subcategoryId = category.subcategoryId,
        accountId = null,
        hits = 1,
        lastUsedAt = null,
        id = "rule-$pattern",
    )

    @Test
    fun rule_WinsOverHistory() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = listOf(rule("fuelstop", car)),
            history = listOf(
                HistoryEntry("fuelstop", food),
                HistoryEntry("fuelstop", food),
            ),
        )

        Assert.assertEquals(car, result.suggestion?.category)
        Assert.assertTrue(result.suggestion?.reason is InboxCardSuggester.Reason.Rule)
        Assert.assertEquals(listOf(food), result.alternatives)
    }

    @Test
    fun noRule_PayeeHistoryMostUsedWins() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = emptyList(),
            history = listOf(
                HistoryEntry("fuelstop", moto),
                HistoryEntry("fuelstop", car),
                HistoryEntry("fuelstop", car),
                HistoryEntry("rimi", home),
            ),
        )

        Assert.assertEquals(car, result.suggestion?.category)
        Assert.assertEquals(
            InboxCardSuggester.Reason.PayeeHistory(2),
            result.suggestion?.reason,
        )
        // Payee alternatives first, then overall.
        Assert.assertEquals(listOf(moto, home), result.alternatives)
    }

    @Test
    fun unknownPayee_MostUsedOverall() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "new shop",
            rules = emptyList(),
            history = listOf(
                HistoryEntry("a", home),
                HistoryEntry("b", food),
                HistoryEntry("c", food),
            ),
        )

        Assert.assertEquals(food, result.suggestion?.category)
        Assert.assertEquals(InboxCardSuggester.Reason.MostUsed, result.suggestion?.reason)
        Assert.assertEquals(listOf(home), result.alternatives)
    }

    @Test
    fun ties_BrokenByRecency() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "",
            rules = emptyList(),
            // Newest first: moto was used more recently than car.
            history = listOf(
                HistoryEntry("x", moto),
                HistoryEntry("y", car),
            ),
        )

        Assert.assertEquals(moto, result.suggestion?.category)
        Assert.assertEquals(listOf(car), result.alternatives)
    }

    @Test
    fun unusableCategories_AreSkipped() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = listOf(rule("fuelstop", car)),
            history = listOf(
                HistoryEntry("fuelstop", snacks),
                HistoryEntry("fuelstop", moto),
            ),
            isUsable = { it != car && it != moto },
        )

        Assert.assertEquals(snacks, result.suggestion?.category)
        Assert.assertTrue(result.alternatives.isEmpty())
    }

    @Test
    fun alternatives_AreLimited() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "",
            rules = emptyList(),
            history = listOf(food, car, moto, home, snacks).map { HistoryEntry("", it) },
            maxAlternatives = 2,
        )

        Assert.assertEquals(2, result.alternatives.size)
    }

    @Test
    fun nothingKnown_NoSuggestion() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "x",
            rules = emptyList(),
            history = emptyList(),
        )

        Assert.assertNull(result.suggestion)
        Assert.assertTrue(result.alternatives.isEmpty())
    }

    @Test
    fun history_IsIgnoredWhenNotUsed() {
        val history = listOf(HistoryEntry("fuelstop", car), HistoryEntry("other", food))

        val withoutRule = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = emptyList(),
            history = history,
            useHistory = false,
        )
        Assert.assertNull(withoutRule.suggestion)
        Assert.assertTrue(withoutRule.alternatives.isEmpty())
        Assert.assertNull(withoutRule.rememberDefault)

        val withRule = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = listOf(rule("fuelstop", moto)),
            history = history,
            useHistory = false,
        )
        Assert.assertEquals(moto, withRule.suggestion?.category)
        Assert.assertTrue(withRule.suggestion?.reason is InboxCardSuggester.Reason.Rule)
        Assert.assertTrue(withRule.alternatives.isEmpty())
    }

    @Test
    fun archivedSubcategoryFallsBackToTheCategory() {
        val bakery = CategoryKey("food", "bakery")
        val dropBakery = { key: CategoryKey ->
            if (key.subcategoryId == "bakery") key.copy(subcategoryId = null) else key
        }

        val withRule = InboxCardSuggester.suggest(
            normalizedPayee = "example fuel",
            rules = listOf(rule("example fuel", bakery)),
            history = emptyList(),
            mapKey = dropBakery,
        )
        Assert.assertEquals(food, withRule.suggestion?.category)
        Assert.assertTrue(withRule.suggestion?.reason is InboxCardSuggester.Reason.Rule)

        val fromHistory = InboxCardSuggester.suggest(
            normalizedPayee = "example fuel",
            rules = emptyList(),
            history = listOf(
                HistoryEntry("example fuel", bakery),
                HistoryEntry("example fuel", food),
                HistoryEntry("example fuel", car),
            ),
            mapKey = dropBakery,
        )
        Assert.assertEquals(food, fromHistory.suggestion?.category)
        Assert.assertEquals(
            InboxCardSuggester.Reason.PayeeHistory(2),
            fromHistory.suggestion?.reason,
        )
        Assert.assertEquals(listOf(car), fromHistory.alternatives)
    }
}
