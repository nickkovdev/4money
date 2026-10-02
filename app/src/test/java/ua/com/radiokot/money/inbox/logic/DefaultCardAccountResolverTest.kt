package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource

class DefaultCardAccountResolverTest {

    private val preferences = object : CardAccountPreferences {
        val map = MutableStateFlow(mapOf("0000" to "acc-card"))
        override fun getAccountIdForCard(cardLast4: String) = map.value[cardLast4]
        override fun setAccountIdForCard(cardLast4: String, accountId: String) {
            map.value += cardLast4 to accountId
        }

        override fun getCardAccountsFlow(): Flow<Map<String, String>> = map
    }
    private val resolver = DefaultCardAccountResolver(
        cardAccountPreferences = preferences,
        mostUsedAccountSource = object : MostUsedAccountSource {
            override suspend fun getMostUsedAccountId() = "acc-most-used"
        },
    )

    @Test
    fun precedence() = runBlocking {
        assertEquals("acc-card", resolver.resolve(cardLast4 = "0000", ruleAccountId = "acc-rule"))
        assertEquals("acc-rule", resolver.resolve(cardLast4 = "1111", ruleAccountId = "acc-rule"))
        assertEquals("acc-most-used", resolver.resolve(cardLast4 = "1111", ruleAccountId = null))
        assertEquals("acc-most-used", resolver.resolve(cardLast4 = null, ruleAccountId = null))
    }
}
