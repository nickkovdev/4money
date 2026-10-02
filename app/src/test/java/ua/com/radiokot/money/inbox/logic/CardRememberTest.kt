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

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.FakePayeeRuleRepository
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester.CategoryKey
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester.HistoryEntry

class CardRememberTest {

    private val home = CategoryKey("home", null)
    private val food = CategoryKey("food", null)
    private val car = CategoryKey("car", null)
    private val moto = CategoryKey("moto", null)

    @Test
    fun singleCategoryHistory_RememberOnByDefault() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "example sia",
            rules = emptyList(),
            history = listOf(
                HistoryEntry("example sia", home),
                HistoryEntry("example sia", home),
                HistoryEntry("rimi", food),
            ),
        )

        Assert.assertEquals(true, result.rememberDefault)
        Assert.assertFalse(result.isPayeeHistoryMixed)
    }

    @Test
    fun mixedHistory_RememberOffByDefault_AmountRulesHinted() {
        val result = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = emptyList(),
            history = listOf(
                HistoryEntry("fuelstop", food),
                HistoryEntry("fuelstop", car),
                HistoryEntry("fuelstop", moto),
            ),
        )

        Assert.assertEquals(false, result.rememberDefault)
        Assert.assertTrue(result.isPayeeHistoryMixed)
    }

    @Test
    fun ruleSuggestionOrNoPayeeHistory_NoToggle() {
        val rule = PayeeRule(
            payeePattern = "fuelstop",
            matchType = PayeeRule.MatchType.Exact,
            categoryId = "car",
            subcategoryId = null,
            accountId = null,
            hits = 0,
            lastUsedAt = null,
        )
        val fromRule = InboxCardSuggester.suggest(
            normalizedPayee = "fuelstop",
            rules = listOf(rule),
            history = listOf(HistoryEntry("fuelstop", car)),
        )
        val unknownPayee = InboxCardSuggester.suggest(
            normalizedPayee = "new shop",
            rules = emptyList(),
            history = listOf(HistoryEntry("rimi", food)),
        )

        Assert.assertNull(fromRule.rememberDefault)
        Assert.assertNull(unknownPayee.rememberDefault)
    }

    @Test
    fun undo_RemovesOnlyTheRuleCreatedByTheAccept() = runBlocking {
        val repository = FakePayeeRuleRepository()
        val before = repository.getRules()

        repository.saveRuleForPayee(
            payeePattern = "example sia",
            matchType = PayeeRule.MatchType.Exact,
            categoryId = "home",
            subcategoryId = null,
            accountId = null,
        )

        val createdId = LearnedRule.createdRuleId(before, repository.getRules(), "example sia")
        Assert.assertNotNull(createdId)
        Assert.assertEquals("home", repository.getRules().single { it.id == createdId }.categoryId)
    }

    @Test
    fun undo_KeepsARuleThatExistedBefore() = runBlocking {
        val existing = PayeeRule(
            payeePattern = "example sia",
            matchType = PayeeRule.MatchType.Exact,
            categoryId = "food",
            subcategoryId = null,
            accountId = null,
            hits = 3,
            lastUsedAt = null,
            id = "existing",
        )
        val repository = FakePayeeRuleRepository(listOf(existing))
        val before = repository.getRules()

        // Remembering points the existing rule to the new category instead of adding one.
        repository.saveRuleForPayee(
            payeePattern = "example sia",
            matchType = PayeeRule.MatchType.Exact,
            categoryId = "home",
            subcategoryId = null,
            accountId = null,
        )

        Assert.assertNull(LearnedRule.createdRuleId(before, repository.getRules(), "example sia"))
    }
}
