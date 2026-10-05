package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.sources.logic.BuiltInPresets
import java.math.BigDecimal
import java.text.Normalizer

class SebLatviaNotificationParserTest {

    private val parser = SebLatviaNotificationParser()
    private val title = "Jauna rezervācija"
    private val sample = "Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA ."

    private fun parsePayment(text: String) =
        parser.parse(title, text) as ParsedBankNotification.Payment

    @Test
    fun sampledTemplate() {
        assertEquals(
            ParsedBankNotification.Payment(
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
        assertEquals(listOf("se.seb.latvia"), BuiltInPresets.all.map { it.packageName })
    }

    @Test
    fun outgoingAccountPayment() {
        assertEquals(
            ParsedBankNotification.Payment(
                amount = BigDecimal("30.00"),
                currencyCode = "EUR",
                cardLast4 = null,
                payee = "EXAMPLE SIA",
                isIncoming = false,
                hasTimestamp = false,
            ),
            parser.parse(
                "Jauns darījums",
                "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance:",
            ),
        )
    }

    @Test
    fun incomingAccountPayment() {
        assertEquals(
            ParsedBankNotification.Payment(
                amount = BigDecimal("1234.56"),
                currencyCode = "EUR",
                cardLast4 = null,
                payee = "EXAMPLE EMPLOYER (PUBL) FILIALE",
                isIncoming = true,
                hasTimestamp = false,
            ),
            parser.parse(
                "Jauns darījums",
                "EXAMPLE EMPLOYER (PUBL) FILIALE samaksāja 1234,56 EUR " +
                        "par Darba alga par 2026.g.septembri. Konta bilance:",
            ),
        )
    }

    @Test
    fun accountPaymentWithBalanceShown() {
        val payment = parsePayment("Jūs samaksājāt 5 EUR SHOP SIA par goods. Konta bilance: 100,00 EUR")
        assertEquals(BigDecimal("5"), payment.amount)
        assertEquals("SHOP SIA", payment.payee)
    }

    @Test
    fun cardPaymentIsNotAnAccountPayment() {
        val payment = parsePayment("Jūs samaksājāt 7,02 EUR par 02/10/2026 11:55 karte...0000 BISTRO EXAMPLE .")
        assertEquals("BISTRO EXAMPLE", payment.payee)
        assertEquals("0000", payment.cardLast4)
        assertEquals(false, payment.isIncoming)
        assertEquals(true, payment.hasTimestamp)
    }

    @Test
    fun accountPaymentWithoutBalanceLabelIsUnrecognized() {
        assertSame(
            ParsedBankNotification.Unrecognized,
            parser.parse("Jauns darījums", "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking."),
        )
    }
}
