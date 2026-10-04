package ua.com.radiokot.money.inbox.templates.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.templates.logic.MoneyTextHeuristic.looksLikeMoney

class MoneyTextHeuristicTest {

    @Test
    fun money() {
        assertTrue(looksLikeMoney("Jūs samaksājāt 3,40 EUR par ..."))
        assertTrue(looksLikeMoney("Paid €3.40"))
        assertTrue(looksLikeMoney("−18,40 €"))
        assertTrue(looksLikeMoney("18,40 €"))
        assertTrue(looksLikeMoney("USD 12.00 spent"))
        assertTrue(looksLikeMoney("Balance: 1 020,00 EUR"))
        assertTrue(looksLikeMoney("Paid 15EUR"))
        assertTrue(looksLikeMoney("Paid $5"))
        assertTrue(looksLikeMoney("Списано 150,00 UAH"))
    }

    @Test
    fun notMoney() {
        assertFalse(looksLikeMoney("Your code is 123456"))
        assertFalse(looksLikeMoney("Meeting at 10:30"))
        assertFalse(looksLikeMoney("3 new messages from EUROPE"))
        assertFalse(looksLikeMoney("2 eur-like words"))
        assertFalse(looksLikeMoney("EUR"))
        assertFalse(looksLikeMoney(""))
        assertFalse(looksLikeMoney("Paid 3 EURO2026 tickets"))
    }
}
