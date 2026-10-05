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

package ua.com.radiokot.money.inbox.templates.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.templates.logic.MoneyTextHeuristic.looksLikeMoney

private val NBSP = Char(0x00A0)

class MoneyTextHeuristicTest {

    @Test
    fun money() {
        assertTrue(looksLikeMoney("Jūs samaksājāt 3,40 EUR par ..."))
        assertTrue(looksLikeMoney("Paid €3.40"))
        assertTrue(looksLikeMoney("−18,40 €"))
        assertTrue(looksLikeMoney("18,40${NBSP}€"))
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
