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
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.templates.data.TemplateFields
import ua.com.radiokot.money.inbox.templates.logic.TemplateBuilder.Problem

class TemplateBuilderTest {

    private val sebTitle = "Jauna rezervācija"
    private val sebText = "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 COFFEE POINT ."
    private val sebTokens = SampleTokenizer.tokenize(SampleTokenizer.composeInput(sebTitle, sebText))

    // Jauna0 rezervācija1 Jūs2 samaksājāt3 3,40(4) EUR5 par6 04/10/2026(7) 09:12(8)
    // karte9 ...10 1234(11) COFFEE12 POINT13 .14
    private val sebMarks = mapOf(
        4 to TokenRole.Amount,
        5 to TokenRole.Currency,
        11 to TokenRole.Card,
        12 to TokenRole.Payee,
        13 to TokenRole.Payee,
    )

    private fun built(tokens: List<SampleToken>, marks: Map<Int, TokenRole>) =
        TemplateBuilder.build(tokens, marks) as TemplateBuilder.Result.Built

    @Test
    fun detectSebSample() {
        assertEquals(
            mapOf(4 to TokenRole.Amount, 5 to TokenRole.Currency),
            TemplateBuilder.detect(sebTokens),
        )
    }

    @Test
    fun detectSymbolBeforeAmount() {
        val tokens = SampleTokenizer.tokenize(
            SampleTokenizer.composeInput("Coffee Point", "Paid €3.40 with card ·1234")
        )
        assertEquals(
            mapOf(3 to TokenRole.Currency, 4 to TokenRole.Amount),
            TemplateBuilder.detect(tokens),
        )
    }

    @Test
    fun detectIntegerAmountAndSignedAmount() {
        assertEquals(
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency),
            TemplateBuilder.detect(SampleTokenizer.tokenize("Paid 15 EUR at SHOP")),
        )
        assertEquals(
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency),
            TemplateBuilder.detect(SampleTokenizer.tokenize("−18,40 € SHOP")),
        )
    }

    @Test
    fun detectPrefersTheFirstAmountWithCurrency() {
        // Card 1234 is a number, not next to a currency; the balance comes after the payment.
        assertEquals(
            mapOf(4 to TokenRole.Amount, 5 to TokenRole.Currency),
            TemplateBuilder.detect(
                SampleTokenizer.tokenize("Card 1234 paid : 12,50 EUR SHOP . Balance 1 020,00 EUR")
            ),
        )
    }

    @Test
    fun detectNothing() {
        assertTrue(TemplateBuilder.detect(SampleTokenizer.tokenize("Your code is 123456")).isEmpty())
        assertTrue(TemplateBuilder.detect(emptyList()).isEmpty())
    }

    @Test
    fun buildSebCard() {
        val result = built(sebTokens, sebMarks)
        assertEquals(
            TemplateFields(amount = 1, currency = 2, payee = 4, card = 3, hasTimestamp = true),
            result.fields,
        )
        assertTrue(result.pattern.startsWith("^\\s*Jauna\\s+rezervācija\\s+Jūs"))
        assertTrue(result.pattern.endsWith("\\s*$"))
    }

    @Test
    fun noTimeNoTimestamp() {
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP")
        val result = built(
            tokens,
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee),
        )
        assertEquals(
            TemplateFields(amount = 1, currency = 2, payee = 3, card = null, hasTimestamp = false),
            result.fields,
        )
    }

    @Test
    fun groupNumbersFollowTheOrderOfAppearance() {
        val tokens = SampleTokenizer.tokenize(
            SampleTokenizer.composeInput("Coffee Point", "Paid €3.40 with card ·1234")
        )
        val result = built(
            tokens,
            mapOf(
                0 to TokenRole.Payee, 1 to TokenRole.Payee,
                3 to TokenRole.Currency, 4 to TokenRole.Amount, 8 to TokenRole.Card,
            ),
        )
        assertEquals(TemplateFields(amount = 3, currency = 2, payee = 1, card = 4), result.fields)
    }

    @Test
    fun invalid() {
        fun problem(marks: Map<Int, TokenRole>) =
            (TemplateBuilder.build(sebTokens, marks) as TemplateBuilder.Result.Invalid).problem

        assertEquals(Problem.MissingAmount, problem(sebMarks - 4))
        assertEquals(Problem.MissingCurrency, problem(sebMarks - 5))
        assertEquals(Problem.MissingPayee, problem(sebMarks - 12 - 13))
        assertEquals(Problem.PayeeNotContiguous, problem(sebMarks - 13 + (14 to TokenRole.Payee)))
        assertEquals(Problem.PayeeNotContiguous, problem(sebMarks + (2 to TokenRole.Payee)))
        // Checked in the order of the enum.
        assertEquals(Problem.MissingAmount, problem(sebMarks - 4 - 5))
        assertEquals(Problem.MissingPayee, problem(sebMarks - 12 - 13 + (7 to TokenRole.Amount)))
        assertEquals(Problem.SeveralAmounts, problem(sebMarks + (7 to TokenRole.Amount)))
        assertEquals(Problem.SeveralCurrencies, problem(sebMarks + (6 to TokenRole.Currency)))
        assertEquals(Problem.SeveralCards, problem(sebMarks + (8 to TokenRole.Card)))
    }

    @Test
    fun invalidPayeeOnTokensThreeAndFive() {
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP at MALL")
        val result = TemplateBuilder.build(
            tokens,
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee, 5 to TokenRole.Payee),
        )
        assertEquals(TemplateBuilder.Result.Invalid(Problem.PayeeNotContiguous), result)
    }

    @Test
    fun payeeNextToVaries() {
        // Paid0 3,40(1) EUR2 SHOP3 parking4 lot5 .6
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP parking lot.")
        val base = mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee)
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.PayeeNextToVaries),
            TemplateBuilder.build(tokens, base + (4 to TokenRole.Varies)),
        )
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.PayeeNextToVaries),
            TemplateBuilder.build(tokens, base - 3 + (4 to TokenRole.Payee) + (3 to TokenRole.Varies)),
        )
        // A literal in between is fine.
        assertTrue(TemplateBuilder.build(tokens, base + (5 to TokenRole.Varies)) is TemplateBuilder.Result.Built)
    }

    @Test
    fun sampleDoesNotMatch() {
        // Paid0 3,40(1) EUR2 SHOP3 card4 A5 ·6 12(7)
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP card A ·12")
        val base = mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee)
        // A card mark on a word.
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.SampleDoesNotMatch),
            TemplateBuilder.build(tokens, base + (5 to TokenRole.Card)),
        )
        // 2 digits match the card group but give no last 4.
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.SampleDoesNotMatch),
            TemplateBuilder.build(tokens, base + (7 to TokenRole.Card)),
        )
        // A payee of only a dot is empty after the cleanup.
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.SampleDoesNotMatch),
            TemplateBuilder.build(
                SampleTokenizer.tokenize("Paid 3,40 EUR ."),
                mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee),
            ),
        )
        assertTrue(TemplateBuilder.build(tokens, base) is TemplateBuilder.Result.Built)
    }

    @Test
    fun digitsInsideAWordBecomeWildcards() {
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP24 Nr.A12345")
        val built = built(
            tokens,
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee),
        )
        assertTrue(built.pattern, built.pattern.endsWith("Nr\\s*\\.\\s*A\\d+\\s*$"))
    }

    @Test
    fun amountMarkOnAWordIsMissingAmount() {
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP")
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.MissingAmount),
            TemplateBuilder.build(
                tokens,
                mapOf(0 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee),
            ),
        )
        assertEquals(
            TemplateBuilder.Result.Invalid(Problem.MissingCurrency),
            TemplateBuilder.build(
                tokens,
                mapOf(1 to TokenRole.Amount, 0 to TokenRole.Currency, 3 to TokenRole.Payee),
            ),
        )
    }

    @Test
    fun specialCharactersAreEscaped() {
        val sample = "Pirkums (POS) [A+B*] x? |y| \\z ^w {2} 3,40 EUR SHOP"
        val tokens = SampleTokenizer.tokenize(sample)
        val amountIndex = tokens.indexOfFirst { it.text == "3,40" }
        val result = built(
            tokens,
            mapOf(
                amountIndex to TokenRole.Amount,
                amountIndex + 1 to TokenRole.Currency,
                amountIndex + 2 to TokenRole.Payee,
            ),
        )
        assertTrue(result.pattern.contains("\\(\\s*POS\\s*\\)"))
        assertEquals(
            "SHOP",
            TemplateMatcher.match(result.pattern, result.fields, false, null, sample)?.payee,
        )
    }

    @Test
    fun defaultName() {
        assertEquals("Jauna rezervācija", TemplateBuilder.defaultName(sebTitle, sebTokens, sebMarks))

        val textTokens = SampleTokenizer.tokenize(sebText)
        // Jūs0 samaksājāt1 3,40(2) EUR3 par4 ...
        val textMarks = mapOf(2 to TokenRole.Amount, 3 to TokenRole.Currency)
        assertEquals("Jūs samaksājāt par", TemplateBuilder.defaultName(null, textTokens, textMarks))
        assertEquals("Jūs samaksājāt par", TemplateBuilder.defaultName("  ", textTokens, textMarks))
    }

    @Test
    fun defaultNameSkipsMarkedWordsAndIsLimited() {
        val tokens = SampleTokenizer.tokenize("SHOP Paid 3,40 EUR")
        assertEquals(
            "Paid",
            TemplateBuilder.defaultName(
                null,
                tokens,
                mapOf(0 to TokenRole.Payee, 2 to TokenRole.Amount, 3 to TokenRole.Currency),
            ),
        )
        assertEquals(32, TemplateBuilder.defaultName("A".repeat(40), tokens, emptyMap()).length)
    }
}
