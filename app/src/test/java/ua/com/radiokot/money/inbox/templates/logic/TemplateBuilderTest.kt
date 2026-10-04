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
