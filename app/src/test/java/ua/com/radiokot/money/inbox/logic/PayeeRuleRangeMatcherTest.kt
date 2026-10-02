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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.data.AmountRange
import ua.com.radiokot.money.inbox.data.PayeeRule
import java.math.BigDecimal

class PayeeRuleRangeMatcherTest {

    private fun rule(
        id: String,
        range: AmountRange? = null,
        action: PayeeRule.Action = PayeeRule.Action.Record,
        pattern: String = "fuelstop",
        matchType: PayeeRule.MatchType = PayeeRule.MatchType.Exact,
        hits: Long = 0,
    ) = PayeeRule(
        payeePattern = pattern,
        matchType = matchType,
        categoryId = if (action == PayeeRule.Action.Ask) null else "cat-$id",
        subcategoryId = null,
        accountId = null,
        hits = hits,
        lastUsedAt = null,
        id = id,
        amountRange = range,
        action = action,
    )

    private fun d(value: String) = BigDecimal(value)

    // Fuelstop: < 10 € snack, 10–35 € ask, > 35 € car.
    private val snack = rule("snack", AmountRange(min = null, max = d("10")))
    private val ask = rule("ask", AmountRange(min = d("10"), max = d("35"), isMaxInclusive = true), PayeeRule.Action.Ask)
    private val car = rule("car", AmountRange(min = d("35"), isMinInclusive = false, max = null))
    private val fuelstop = listOf(snack, ask, car)

    @Test
    fun rangeContainingTheAmountWins() {
        assertEquals(snack, PayeeRuleMatcher.match("fuelstop", fuelstop, d("7.02")))
        assertEquals(ask, PayeeRuleMatcher.match("fuelstop", fuelstop, d("18.40")))
        assertEquals(car, PayeeRuleMatcher.match("fuelstop", fuelstop, d("48.30")))
    }

    @Test
    fun edges_RespectInclusivity() {
        // [10, 35] ask; (35, ∞) car; (-∞, 10) snack.
        assertEquals(ask, PayeeRuleMatcher.match("fuelstop", fuelstop, d("10")))
        assertEquals(ask, PayeeRuleMatcher.match("fuelstop", fuelstop, d("35")))
        assertEquals(car, PayeeRuleMatcher.match("fuelstop", fuelstop, d("35.01")))
        assertEquals(snack, PayeeRuleMatcher.match("fuelstop", fuelstop, d("9.99")))
    }

    @Test
    fun noRangeContains_FallsBackToPlainRule() {
        val plain = rule("plain")
        val onlyCheap = rule("cheap", AmountRange(min = null, max = d("10")))
        assertEquals(plain, PayeeRuleMatcher.match("fuelstop", listOf(onlyCheap, plain), d("50")))
    }

    @Test
    fun noRangeContainsAndNoPlainRule_NoMatch() {
        val onlyCheap = rule("cheap", AmountRange(min = null, max = d("10")))
        assertNull(PayeeRuleMatcher.match("fuelstop", listOf(onlyCheap), d("50")))
    }

    @Test
    fun unknownAmount_OnlyPlainRules() {
        val plain = rule("plain")
        assertEquals(plain, PayeeRuleMatcher.match("fuelstop", fuelstop + plain, amount = null))
        assertNull(PayeeRuleMatcher.match("fuelstop", fuelstop, amount = null))
    }

    @Test
    fun overlappingRanges_NarrowestWins() {
        val wide = rule("wide", AmountRange(min = d("0"), max = d("100")))
        val narrow = rule("narrow", AmountRange(min = d("10"), max = d("20")), hits = 0)
        val unbounded = rule("unbounded", AmountRange(min = d("5"), max = null), hits = 99)
        assertEquals(narrow, PayeeRuleMatcher.match("fuelstop", listOf(wide, unbounded, narrow), d("15")))
    }

    @Test
    fun moreSpecificPatternWithoutApplicableRule_FallsToNextPattern() {
        val exactCheap = rule("exact-cheap", AmountRange(min = null, max = d("10")))
        val containsPlain = rule("contains", pattern = "fuel", matchType = PayeeRule.MatchType.Contains)
        assertEquals(
            containsPlain,
            PayeeRuleMatcher.match("fuelstop", listOf(exactCheap, containsPlain), d("50")),
        )
        assertEquals(
            exactCheap,
            PayeeRuleMatcher.match("fuelstop", listOf(exactCheap, containsPlain), d("5")),
        )
    }

    @Test
    fun range_Contains() {
        val range = AmountRange(min = d("10"), max = d("35"))
        assertTrue(d("10") in range)
        assertFalse(d("35") in range)
        assertEquals("[)", range.boundsSlug)
        assertEquals(range, AmountRange.fromColumns(d("10"), d("35"), "[)"))
        assertNull(AmountRange.fromColumns(null, null, "[]"))
        assertEquals(
            AmountRange(min = d("1"), isMinInclusive = false, max = d("2"), isMaxInclusive = true),
            AmountRange.fromColumns(d("1"), d("2"), "(]"),
        )
    }
}
