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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

private val NBSP = Char(0x00A0)

class TemplateAmountsTest {

    @Test
    fun thousandsAndDecimalSeparators() {
        assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1 500,00"))
        assertEquals(
            BigDecimal("1500.00"),
            TemplateAmounts.parseAmount("1${NBSP}500,00".let(SampleTokenizer::normalize)),
        )
        assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1.500,00"))
        assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1,500.00"))
        assertEquals(BigDecimal("1234567.89"), TemplateAmounts.parseAmount("1 234 567.89"))
        assertEquals(BigDecimal("1234567.89"), TemplateAmounts.parseAmount("1.234.567,89"))
        assertEquals(BigDecimal("1500"), TemplateAmounts.parseAmount("1 500"))
    }

    @Test
    fun decimalPart() {
        assertEquals(BigDecimal("3.4"), TemplateAmounts.parseAmount("3,4"))
        assertEquals(BigDecimal("3.40"), TemplateAmounts.parseAmount("3.40"))
        assertEquals(BigDecimal("1500.00"), TemplateAmounts.parseAmount("1500,00"))
    }

    @Test
    fun threeDigitsAfterTheOnlySeparatorAreThousands() {
        assertEquals(BigDecimal("1234"), TemplateAmounts.parseAmount("1.234"))
        assertEquals(BigDecimal("1234"), TemplateAmounts.parseAmount("1,234"))
    }

    @Test
    fun integer() {
        assertEquals(BigDecimal("15"), TemplateAmounts.parseAmount("15"))
        assertEquals(BigDecimal("2026"), TemplateAmounts.parseAmount("2026"))
    }

    @Test
    fun signIsIgnored() {
        assertEquals(BigDecimal("3.40"), TemplateAmounts.parseAmount("-3,40"))
        assertEquals(BigDecimal("18.40"), TemplateAmounts.parseAmount("−18,40"))
        assertEquals(BigDecimal("18.40"), TemplateAmounts.parseAmount("+18.40"))
    }

    @Test
    fun invalid() {
        assertNull(TemplateAmounts.parseAmount("0,00"))
        assertNull(TemplateAmounts.parseAmount("0"))
        assertNull(TemplateAmounts.parseAmount("1.23.4"))
        assertNull(TemplateAmounts.parseAmount("1.2345"))
        assertNull(TemplateAmounts.parseAmount("12 34,00"))
        assertNull(TemplateAmounts.parseAmount("1234.567,00"))
        assertNull(TemplateAmounts.parseAmount(""))
        assertNull(TemplateAmounts.parseAmount("-"))
        assertNull(TemplateAmounts.parseAmount("abc"))
        assertNull(TemplateAmounts.parseAmount("3,40 EUR"))
        assertNull(TemplateAmounts.parseAmount(",50"))
        assertNull(TemplateAmounts.parseAmount("50,"))
    }

    @Test
    fun currency() {
        assertEquals("EUR", TemplateAmounts.parseCurrency("€"))
        assertEquals("USD", TemplateAmounts.parseCurrency("$"))
        assertEquals("GBP", TemplateAmounts.parseCurrency("£"))
        assertEquals("EUR", TemplateAmounts.parseCurrency("eur"))
        assertEquals("EUR", TemplateAmounts.parseCurrency("EUR"))
        assertEquals("UAH", TemplateAmounts.parseCurrency("₴"))
        assertNull(TemplateAmounts.parseCurrency("par"))
        assertNull(TemplateAmounts.parseCurrency("EURO"))
        assertNull(TemplateAmounts.parseCurrency(""))
        assertNull(TemplateAmounts.parseCurrency("3,40"))
    }
}
