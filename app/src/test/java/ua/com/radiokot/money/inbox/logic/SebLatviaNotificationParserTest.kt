package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import java.math.BigDecimal
import java.text.Normalizer

class SebLatviaNotificationParserTest {

    private val parser = SebLatviaNotificationParser()
    private val title = "Jauna rezervācija"
    private val sample = "Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA ."

    private fun parsePayment(text: String) =
        parser.parse(title, text) as ParsedBankNotification.CardPayment

    @Test
    fun sampledTemplate() {
        assertEquals(
            ParsedBankNotification.CardPayment(
                amount = BigDecimal("2.12"),
                currencyCode = "USD",
                cardLast4 = "0000",
                payee = "DEEPSEERWEA",
            ),
            parser.parse(title, sample),
        )
    }

    @Test
    fun euroIntegerAmount() {
        val payment = parsePayment("Jūs samaksājāt 15 EUR par 03/10/2026 18:40 karte...1111 CAFE EXAMPLE .")
        assertEquals(BigDecimal("15"), payment.amount)
        assertEquals("EUR", payment.currencyCode)
        assertEquals("1111", payment.cardLast4)
        assertEquals("CAFE EXAMPLE", payment.payee)
    }

    @Test
    fun thousandsSeparatorSpaceAndNbsp() {
        assertEquals(
            BigDecimal("1234.56"),
            parsePayment("Jūs samaksājāt 1 234,56 EUR par 03/10/2026 18:40 karte...0000 SHOP .").amount,
        )
        assertEquals(
            BigDecimal("1234.56"),
            parsePayment("Jūs samaksājāt 1\u00A0234,56\u00A0EUR par 03/10/2026 18:40 karte...0000 SHOP .").amount,
        )
        assertEquals(
            BigDecimal("1234.56"),
            parsePayment("Jūs samaksājāt 1\u202F234,56\u202FEUR par 03/10/2026 18:40 karte...0000 SHOP .").amount,
        )
    }

    @Test
    fun decomposedUnicodeIsParsed() {
        val decomposed = Normalizer.normalize(sample, Normalizer.Form.NFD)
        assertEquals("DEEPSEERWEA", parsePayment(decomposed).payee)
    }

    @Test
    fun ellipsisCharacterAndNoTrailingDot() {
        val payment = parsePayment("Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte\u20260000 DEEPSEERWEA")
        assertEquals("0000", payment.cardLast4)
        assertEquals("DEEPSEERWEA", payment.payee)
    }

    @Test
    fun lowerCaseCurrencyIsUpperCased() {
        assertEquals("USD", parsePayment(sample.replace("USD", "usd")).currencyCode)
    }

    @Test
    fun unknownTemplatesAreUnrecognized() {
        listOf(
            "Ienākošs maksājums 10,00 EUR no EXAMPLE SIA",
            "Jums ir jauns ziņojums internetbankā",
            "",
            "Jūs samaksājāt abc USD par karte...0000 X",
        ).forEach { text ->
            assertSame(text, ParsedBankNotification.Unrecognized, parser.parse(title, text))
        }
    }

    @Test
    fun sourcePackagesAreSebOnly() {
        assertEquals(setOf("se.seb.latvia"), BankNotificationSources.packageNames)
    }
}
