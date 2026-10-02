package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.PayeeRule

class PayeeRuleMatcherTest {

    private fun rule(
        pattern: String,
        matchType: PayeeRule.MatchType,
        hits: Long = 0,
        id: String = pattern + matchType,
    ) = PayeeRule(
        payeePattern = pattern,
        matchType = matchType,
        categoryId = "cat-$id",
        subcategoryId = null,
        accountId = null,
        hits = hits,
        lastUsedAt = null,
        id = id,
    )

    @Test
    fun exactBeatsContains() {
        val exact = rule("acme hyper", PayeeRule.MatchType.Exact)
        val contains = rule("acme", PayeeRule.MatchType.Contains, hits = 100)
        assertEquals(exact, PayeeRuleMatcher.match("acme hyper", listOf(contains, exact)))
    }

    @Test
    fun longestContainsWins() {
        val short = rule("foodo", PayeeRule.MatchType.Contains, hits = 50)
        val long = rule("foodo market", PayeeRule.MatchType.Contains)
        assertEquals(long, PayeeRuleMatcher.match("foodo market riga centrs", listOf(short, long)))
        assertEquals(short, PayeeRuleMatcher.match("foodo food", listOf(short, long)))
    }

    @Test
    fun exactTieBreaksByHits() {
        val a = rule("cafe", PayeeRule.MatchType.Exact, hits = 1, id = "a")
        val b = rule("cafe", PayeeRule.MatchType.Exact, hits = 5, id = "b")
        assertEquals(b, PayeeRuleMatcher.match("cafe", listOf(a, b)))
    }

    @Test
    fun noMatch() {
        val rules = listOf(
            rule("acme", PayeeRule.MatchType.Exact),
            rule("megamart", PayeeRule.MatchType.Contains),
        )
        assertNull(PayeeRuleMatcher.match("acme hyper", rules))
        assertNull(PayeeRuleMatcher.match("", rules))
        assertNull(PayeeRuleMatcher.match("anything", emptyList()))
    }
}
