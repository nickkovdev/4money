package ua.com.radiokot.money.inbox.templates.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.templates.logic.SampleToken.Kind
import java.text.Normalizer

class SampleTokenizerTest {

    private fun tokenize(text: String) =
        SampleTokenizer.tokenize(SampleTokenizer.normalize(text))

    @Test
    fun normalize() {
        assertEquals("1 500,00 EUR", SampleTokenizer.normalize("  1 500,00 EUR \n"))
        assertEquals(
            "Jūs",
            SampleTokenizer.normalize(Normalizer.normalize("Jūs", Normalizer.Form.NFD)),
        )
    }

    @Test
    fun composeInput() {
        assertEquals("Title\nText", SampleTokenizer.composeInput("Title", "Text"))
        assertEquals("Text", SampleTokenizer.composeInput(null, " Text "))
        assertEquals("Text", SampleTokenizer.composeInput("", "Text"))
    }

    @Test
    fun sebCardSample() {
        val s = SampleTokenizer.composeInput(
            "Jauna rezervācija",
            "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 COFFEE POINT .",
        )
        val t = SampleTokenizer.tokenize(s)

        assertEquals(
            listOf(
                "Jauna", "rezervācija", "Jūs", "samaksājāt", "3,40", "EUR", "par",
                "04/10/2026", "09:12", "karte", "...", "1234", "COFFEE", "POINT", ".",
            ),
            t.map(SampleToken::text),
        )
        assertEquals(
            listOf(
                Kind.Word, Kind.Word, Kind.Word, Kind.Word, Kind.Amount, Kind.Currency, Kind.Word,
                Kind.Date, Kind.Time, Kind.Word, Kind.Punctuation, Kind.Number, Kind.Word, Kind.Word,
                Kind.Punctuation,
            ),
            t.map(SampleToken::kind),
        )
        assertEquals(t.indices.toList(), t.map(SampleToken::index))
        assertFalse(t[10].spaceBefore)
        assertFalse(t[11].spaceBefore)
        assertTrue(t[2].spaceBefore) // "\n" counts.
        assertFalse(t[0].spaceBefore)
        t.forEach { token ->
            assertEquals(token.text, s.substring(token.start, token.end))
        }
    }

    @Test
    fun currencySymbolBeforeAndAfter() {
        val before = tokenize("€3.40")
        assertEquals(listOf("€", "3.40"), before.map(SampleToken::text))
        assertEquals(listOf(Kind.Currency, Kind.Amount), before.map(SampleToken::kind))
        assertFalse(before[1].spaceBefore)

        val after = tokenize("3,40€")
        assertEquals(listOf("3,40", "€"), after.map(SampleToken::text))
        assertEquals(listOf(Kind.Amount, Kind.Currency), after.map(SampleToken::kind))
    }

    @Test
    fun spaceThousandsAmountIsOneToken() {
        val t = tokenize("1 500,00 EUR")
        assertEquals(listOf("1 500,00", "EUR"), t.map(SampleToken::text))
        assertEquals(listOf(Kind.Amount, Kind.Currency), t.map(SampleToken::kind))

        assertEquals(listOf("1 234 567.89"), tokenize("1 234 567.89").map(SampleToken::text))
        assertEquals(listOf("1 500,00"), tokenize("1 500,00").map(SampleToken::text))
    }

    @Test
    fun spaceDoesNotJoinNonThousandsGroups() {
        val t = tokenize("Paid 2 12 times")
        assertEquals(listOf("Paid", "2", "12", "times"), t.map(SampleToken::text))
        assertEquals(listOf(Kind.Word, Kind.Number, Kind.Number, Kind.Word), t.map(SampleToken::kind))
    }

    @Test
    fun plainIntegerIsNumber() {
        assertEquals(listOf(Kind.Number), tokenize("2026").map(SampleToken::kind))
        assertEquals(listOf(Kind.Amount), tokenize("1.234").map(SampleToken::kind))
    }

    @Test
    fun sentenceDotAfterAmountIsPunctuation() {
        val t = tokenize("Paid 3.40. Balance 120,00.")
        assertEquals(listOf("Paid", "3.40", ".", "Balance", "120,00", "."), t.map(SampleToken::text))
    }

    @Test
    fun signIsPunctuation() {
        val t = tokenize("−18,40 €")
        assertEquals(listOf("−", "18,40", "€"), t.map(SampleToken::text))
        assertEquals(listOf(Kind.Punctuation, Kind.Amount, Kind.Currency), t.map(SampleToken::kind))
    }

    @Test
    fun dateAndTimeVariants() {
        assertEquals(
            listOf(Kind.Date, Kind.Date, Kind.Date, Kind.Time, Kind.Time),
            tokenize("2026-10-04 4.10.26 2/10/2026 9:05 18:40:12").map(SampleToken::kind),
        )
    }

    @Test
    fun words() {
        val t = tokenize("O’Brien's Co-op SHOP24 Pirkums (POS)")
        assertEquals(
            listOf("O’Brien's", "Co-op", "SHOP24", "Pirkums", "(", "POS", ")"),
            t.map(SampleToken::text),
        )
        assertEquals(Kind.Word, t[5].kind)
    }

    @Test
    fun onlyUpperCaseIsoCodesAreCurrency() {
        val t = tokenize("EUR eur SIA XYZ USD")
        assertEquals(
            listOf(Kind.Currency, Kind.Word, Kind.Word, Kind.Word, Kind.Currency),
            t.map(SampleToken::kind),
        )
    }

    @Test
    fun isoCodeGluedToAmountIsSplit() {
        val t = tokenize("Summa:EUR12,50")
        assertEquals(listOf("Summa", ":", "EUR", "12,50"), t.map(SampleToken::text))
        assertEquals(Kind.Currency, t[2].kind)
        assertEquals(Kind.Amount, t[3].kind)
    }

    @Test
    fun maskedCard() {
        val t = tokenize("card ·1234 ****0000")
        assertEquals(listOf("card", "·", "1234", "****", "0000"), t.map(SampleToken::text))
        assertEquals(Kind.Number, t[2].kind)
    }

    @Test
    fun emptyText() {
        assertTrue(SampleTokenizer.tokenize("").isEmpty())
    }
}
