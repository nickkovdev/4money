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

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.TemplateFields
import java.math.BigDecimal
import java.text.Normalizer

private val NBSP = Char(0x00A0)
private val NNBSP = Char(0x202F)

class TemplateMatcherTest {

    private val sebTitle = "Jauna rezervācija"
    private val sebText = "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 COFFEE POINT ."
    private val sebCard = TemplateBuilder.build(
        SampleTokenizer.tokenize(SampleTokenizer.composeInput(sebTitle, sebText)),
        mapOf(
            4 to TokenRole.Amount,
            5 to TokenRole.Currency,
            11 to TokenRole.Card,
            12 to TokenRole.Payee,
            13 to TokenRole.Payee,
        ),
    ) as TemplateBuilder.Result.Built

    private fun matchSebCard(text: String, title: String? = sebTitle, isIncoming: Boolean = false) =
        TemplateMatcher.match(sebCard.pattern, sebCard.fields, isIncoming, title, text)

    @Test
    fun sebCardMatchesItsSample() {
        assertEquals(
            ParsedBankNotification.Payment(
                amount = BigDecimal("3.40"),
                currencyCode = "EUR",
                cardLast4 = "1234",
                payee = "COFFEE POINT",
                isIncoming = false,
                hasTimestamp = true,
            ),
            matchSebCard(sebText),
        )
    }

    @Test
    fun sebCardMatchesOtherPayments() {
        val longer = matchSebCard(
            "Jūs samaksājāt 1 024,15 EUR par 05/10/2026 18:40 karte...1234 SOME LONGER SHOP NAME RIGA ."
        )!!
        assertEquals(BigDecimal("1024.15"), longer.amount)
        assertEquals("SOME LONGER SHOP NAME RIGA", longer.payee)

        val usd = matchSebCard("Jūs samaksājāt 7,80 USD par 2/10/2026 22:05 karte...0000 TAXI EXAMPLE .")!!
        assertEquals(BigDecimal("7.80"), usd.amount)
        assertEquals("USD", usd.currencyCode)
        assertEquals("0000", usd.cardLast4)
        assertEquals("TAXI EXAMPLE", usd.payee)

        val integer = matchSebCard("Jūs samaksājāt 15 EUR par 03/10/2026 18:40 karte...1111 CAFE EXAMPLE .")!!
        assertEquals(BigDecimal("15"), integer.amount)
        assertEquals("CAFE EXAMPLE", integer.payee)
    }

    @Test
    fun sebCardDoesNotMatchAnotherKind() {
        assertNull(matchSebCard("EXAMPLE SIA samaksāja 1000,00 EUR par Darba alga."))
        assertNull(matchSebCard("Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance:"))
        assertNull(matchSebCard(sebText, title = "Jauns darījums"))
        assertNull(matchSebCard(sebText, title = null))
    }

    @Test
    fun caseUnicodeAndSpaceVariants() {
        assertEquals(
            "COFFEE POINT",
            matchSebCard("JŪS SAMAKSĀJĀT 3,40 EUR PAR 04/10/2026 09:12 KARTE...1234 COFFEE POINT .")?.payee,
        )
        assertEquals(
            BigDecimal("1024.15"),
            matchSebCard("Jūs samaksājāt 1${NBSP}024,15${NNBSP}EUR par 05/10/2026 18:40 karte...1234 SHOP .")?.amount,
        )
        assertEquals(
            "COFFEE POINT",
            matchSebCard(Normalizer.normalize(sebText, Normalizer.Form.NFD))?.payee,
        )
        assertEquals(
            "COFFEE POINT",
            matchSebCard("  Jūs  samaksājāt\n3,40 EUR par 04/10/2026 09:12 karte...1234 COFFEE POINT . ")?.payee,
        )
    }

    @Test
    fun lowerCaseCurrencyIsUpperCased() {
        assertEquals(
            "EUR",
            matchSebCard("Jūs samaksājāt 3,40 eur par 04/10/2026 09:12 karte...1234 SHOP .")?.currencyCode,
        )
    }

    @Test
    fun invalidAmountOrCurrencyIsNull() {
        assertNull(matchSebCard("Jūs samaksājāt 0,00 EUR par 04/10/2026 09:12 karte...1234 SHOP ."))
        assertNull(matchSebCard("Jūs samaksājāt 3,40 ABC par 04/10/2026 09:12 karte...1234 SHOP ."))
    }

    @Test
    fun emptyPayeeIsNull() {
        val tokens = SampleTokenizer.tokenize("Paid 3,40 EUR SHOP")
        val built = TemplateBuilder.build(
            tokens,
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee),
        ) as TemplateBuilder.Result.Built
        assertEquals("SHOP", TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR SHOP.")?.payee)
        assertNull(TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR ."))
    }

    @Test
    fun payeeInTitle() {
        val title = "Coffee Point"
        val text = "Paid €3.40 with card ·1234"
        val built = TemplateBuilder.build(
            SampleTokenizer.tokenize(SampleTokenizer.composeInput(title, text)),
            mapOf(
                0 to TokenRole.Payee, 1 to TokenRole.Payee,
                3 to TokenRole.Currency, 4 to TokenRole.Amount, 8 to TokenRole.Card,
            ),
        ) as TemplateBuilder.Result.Built

        assertEquals(
            ParsedBankNotification.Payment(
                amount = BigDecimal("18.40"),
                currencyCode = "EUR",
                cardLast4 = "1234",
                payee = "Fuelstop Riga",
                isIncoming = false,
                hasTimestamp = false,
            ),
            TemplateMatcher.match(
                built.pattern, built.fields, false,
                "Fuelstop Riga", "Paid €18.40 with card ·1234",
            ),
        )
        assertEquals(
            "USD",
            TemplateMatcher.match(
                built.pattern, built.fields, false,
                "Fuelstop", "Paid $1,250.00 with card ·0000",
            )?.currencyCode,
        )
        assertNull(TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid €18.40 with card ·1234"))

        // 2-6 digits match; the last 4 are the card, fewer than 4 are no card.
        val sixDigits = TemplateMatcher.match(
            built.pattern, built.fields, false,
            "Fuelstop", "Paid €18.40 with card ·001234",
        )
        assertEquals("1234", sixDigits?.cardLast4)
        val twoDigits = TemplateMatcher.match(
            built.pattern, built.fields, false,
            "Fuelstop", "Paid €18.40 with card ·12",
        )
        assertEquals("Fuelstop", twoDigits?.payee)
        assertNull(twoDigits?.cardLast4)
    }

    @Test
    fun variesAndBalanceWildcard() {
        val sample = "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance: 120,00 EUR"
        val tokens = SampleTokenizer.tokenize(sample)
        // Jūs0 samaksājāt1 30,00(2) EUR3 EXAMPLE4 SIA5 par6 parking7 .8 Konta9 bilance10 :11 120,00(12) EUR13
        val built = TemplateBuilder.build(
            tokens,
            mapOf(
                2 to TokenRole.Amount, 3 to TokenRole.Currency,
                4 to TokenRole.Payee, 5 to TokenRole.Payee,
                7 to TokenRole.Varies, 8 to TokenRole.Varies,
            ),
        ) as TemplateBuilder.Result.Built
        assertEquals(TemplateFields(amount = 1, currency = 2, payee = 3), built.fields)

        val payment = TemplateMatcher.match(
            built.pattern, built.fields, false, null,
            "Jūs samaksājāt 12,50 EUR OTHER COMPANY SIA par rēķins 42 oktobris. Konta bilance: 1 020,00 EUR",
        )!!
        assertEquals(BigDecimal("12.50"), payment.amount)
        assertEquals("OTHER COMPANY SIA", payment.payee)
        assertEquals(false, payment.hasTimestamp)

        // Integer balance and a single-word purpose.
        assertEquals(
            "SHOP",
            TemplateMatcher.match(
                built.pattern, built.fields, false, null,
                "Jūs samaksājāt 5 EUR SHOP par x. Konta bilance: 120 EUR",
            )?.payee,
        )
    }

    @Test
    fun incomingDirection() {
        val sample = "EXAMPLE SIA samaksāja 1000,00 EUR par Darba alga. Konta bilance:"
        val built = TemplateBuilder.build(
            SampleTokenizer.tokenize(sample),
            mapOf(
                0 to TokenRole.Payee, 1 to TokenRole.Payee,
                3 to TokenRole.Amount, 4 to TokenRole.Currency,
                6 to TokenRole.Varies, 7 to TokenRole.Varies, 8 to TokenRole.Varies,
            ),
        ) as TemplateBuilder.Result.Built

        val template = NotificationTemplate(
            id = "t1",
            sourcePackage = "com.example.bank",
            name = "Incoming",
            direction = NotificationTemplate.Direction.Incoming,
            pattern = built.pattern,
            fields = built.fields,
            sampleText = sample,
            isEnabled = true,
            createdAt = LocalDateTime(2026, 10, 5, 12, 0),
        )
        assertEquals(
            ParsedBankNotification.Payment(
                amount = BigDecimal("250.00"),
                currencyCode = "EUR",
                cardLast4 = null,
                payee = "OTHER PERSON",
                isIncoming = true,
                hasTimestamp = false,
            ),
            TemplateMatcher.match(template, null, "OTHER PERSON samaksāja 250,00 EUR par Return. Konta bilance:"),
        )
    }

    @Test
    fun signedAmount() {
        val sample = "−18,40 € COFFEE POINT"
        val built = TemplateBuilder.build(
            SampleTokenizer.tokenize(sample),
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee, 4 to TokenRole.Payee),
        ) as TemplateBuilder.Result.Built
        val payment = TemplateMatcher.match(built.pattern, built.fields, false, null, "−1 018,40 € FUELSTOP")!!
        assertEquals(BigDecimal("1018.40"), payment.amount)
        assertEquals("FUELSTOP", payment.payee)
    }

    @Test
    fun shortCardDigitsAreNotACard() {
        val fields = TemplateFields(amount = 1, currency = 2, payee = 4, card = 3)
        val payment = TemplateMatcher.match(
            sebCard.pattern, fields, false, sebTitle,
            "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...12 SHOP .",
        )!!
        assertNull(payment.cardLast4)
        assertEquals(
            "3456",
            TemplateMatcher.match(
                sebCard.pattern, fields, false, sebTitle,
                "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...123456 SHOP .",
            )?.cardLast4,
        )
    }

    @Test
    fun brokenPatternOrFieldsNeverThrow() {
        assertNull(TemplateMatcher.match("(", sebCard.fields, false, sebTitle, sebText))
        assertNull(TemplateMatcher.match("[", sebCard.fields, false, sebTitle, sebText))
        assertNull(
            TemplateMatcher.match(
                sebCard.pattern, sebCard.fields.copy(payee = 9), false, sebTitle, sebText,
            )
        )
        assertNull(
            TemplateMatcher.match(
                sebCard.pattern, sebCard.fields.copy(amount = 0), false, sebTitle, sebText,
            )
        )
        // A pattern that is valid but not ours.
        assertNull(TemplateMatcher.match(".*", sebCard.fields, false, sebTitle, sebText))
    }

    @Test
    fun longTextDoesNotHang() {
        val text = "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 " + "A ".repeat(1_900) + "."
        assertEquals(BigDecimal("3.40"), matchSebCard(text)?.amount)
    }

    @Test
    fun inputLongerThanTheCapIsNotMatched() {
        val prefix = "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 "
        val fitting = prefix + "A".repeat(4096 - sebTitle.length - 1 - prefix.length - 2) + " ."
        assertEquals(4096, SampleTokenizer.composeInput(sebTitle, fitting).length)
        assertEquals(BigDecimal("3.40"), matchSebCard(fitting)?.amount)

        val tooLong = prefix + "A".repeat(4096) + " ."
        assertNull(matchSebCard(tooLong))
    }

    @Test
    fun balanceSignMayFlip() {
        val positive = "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance: 120,00 EUR"
        val negative = "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance: -5,00 EUR"
        val marks = mapOf(
            2 to TokenRole.Amount, 3 to TokenRole.Currency,
            4 to TokenRole.Payee, 5 to TokenRole.Payee,
            7 to TokenRole.Varies, 8 to TokenRole.Varies,
        )
        fun template(sample: String) =
            TemplateBuilder.build(SampleTokenizer.tokenize(sample), marks) as TemplateBuilder.Result.Built

        val fromPositive = template(positive)
        val fromNegative = template(negative)
        listOf(fromPositive, fromNegative).forEach { built ->
            listOf(positive, negative, negative.replace("-5,00", "−5,00")).forEach { text ->
                assertEquals(
                    BigDecimal("30.00"),
                    TemplateMatcher.match(built.pattern, built.fields, false, null, text)?.amount,
                )
            }
        }
    }

    @Test
    fun signOfTheMarkedAmountStaysLiteral() {
        // −0 18,40(1) €2 COFFEE3 POINT4
        val built = TemplateBuilder.build(
            SampleTokenizer.tokenize("−18,40 € COFFEE POINT"),
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee, 4 to TokenRole.Payee),
        ) as TemplateBuilder.Result.Built
        assertNull(TemplateMatcher.match(built.pattern, built.fields, false, null, "+18,40 € COFFEE POINT"))
    }

    @Test
    fun digitRunsInsideWordsAreWildcards() {
        val sample = "Paid 3,40 EUR at SHOP Ref TX1234567"
        val built = build(
            sample,
            marksByText = mapOf("3,40" to TokenRole.Amount, "EUR" to TokenRole.Currency, "SHOP" to TokenRole.Payee),
        )
        assertEquals(
            "SHOP",
            TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR at SHOP Ref TX7654321")?.payee,
        )
        assertEquals(
            "SHOP",
            TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR at SHOP Ref tx1")?.payee,
        )
        assertNull(TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR at SHOP Ref TY7654321"))
        assertNull(TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR at SHOP Ref TX"))
    }

    private fun build(sample: String, title: String? = null, marksByText: Map<String, TokenRole>): TemplateBuilder.Result.Built {
        val tokens = SampleTokenizer.tokenize(SampleTokenizer.composeInput(title, sample))
        val marks = tokens
            .filter { it.text in marksByText }
            .associate { it.index to marksByText.getValue(it.text) }
        return TemplateBuilder.build(tokens, marks) as TemplateBuilder.Result.Built
    }

    @Test
    fun payeeGluedToTheFinalDot() {
        val built = build(
            "Paid 3,40 EUR at SHOP.",
            marksByText = mapOf("3,40" to TokenRole.Amount, "EUR" to TokenRole.Currency, "SHOP" to TokenRole.Payee),
        )
        assertEquals(
            "A.B. OTHER SHOP LTD",
            TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 1,500.00 EUR at A.B. OTHER SHOP LTD.")?.payee,
        )
        assertEquals(
            BigDecimal("1500.00"),
            TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 1,500.00 EUR at SHOP.")?.amount,
        )
    }

    @Test
    fun payeeStopsAtTheFirstFollowingLiteral() {
        val built = build(
            "Paid 3,40 EUR at SHOP on card *1234",
            marksByText = mapOf(
                "3,40" to TokenRole.Amount, "EUR" to TokenRole.Currency,
                "SHOP" to TokenRole.Payee, "1234" to TokenRole.Card,
            ),
        )
        val payment = TemplateMatcher.match(
            built.pattern, built.fields, false, null, "Paid 7 EUR at MY SHOP on card *0000",
        )!!
        assertEquals("MY SHOP", payment.payee)
        assertEquals("0000", payment.cardLast4)
        assertEquals(BigDecimal("7"), payment.amount)
    }

    @Test
    fun cyrillicMultilineWithSymbolAndBalance() {
        val sample = "−150.00₴ Кавʼярня\nБаланс 1 234.56₴"
        // −0 150.00(1) ₴2 Кавʼярня3 Баланс4 1 234.56(5) ₴6
        val built = TemplateBuilder.build(
            SampleTokenizer.tokenize(sample),
            mapOf(1 to TokenRole.Amount, 2 to TokenRole.Currency, 3 to TokenRole.Payee),
        ) as TemplateBuilder.Result.Built
        val payment = TemplateMatcher.match(
            built.pattern, built.fields, false, null,
            "−42.10₴ Інша кавʼярня Київ\nБАЛАНС 12 000.00₴",
        )!!
        assertEquals(BigDecimal("42.10"), payment.amount)
        assertEquals("UAH", payment.currencyCode)
        assertEquals("Інша кавʼярня Київ", payment.payee)
    }

    @Test
    fun balanceInAnotherCurrencyStillMatches() {
        // Paid0 €1 3.40(2) at3 SHOP4 .5 Balance6 €7 120.00(8); the balance "€" stays unmarked.
        val built = TemplateBuilder.build(
            SampleTokenizer.tokenize("Paid €3.40 at SHOP. Balance €120.00"),
            mapOf(1 to TokenRole.Currency, 2 to TokenRole.Amount, 4 to TokenRole.Payee),
        ) as TemplateBuilder.Result.Built
        assertEquals(TemplateFields(amount = 2, currency = 1, payee = 3), built.fields)

        val payment = TemplateMatcher.match(
            built.pattern, built.fields, false, null, "Paid \$5.00 at SHOP. Balance USD 120",
        )!!
        assertEquals("USD", payment.currencyCode)
        assertEquals(BigDecimal("5.00"), payment.amount)
    }

    @Test
    fun manyTemplatesStayCorrectBeyondTheCache() {
        // Distinct literal words, as digits inside words are wildcards.
        repeat(40) { n ->
            val word = "x".repeat(n + 1)
            val built = build(
                "Paid 3,40 EUR at SHOP ref $word",
                marksByText = mapOf("3,40" to TokenRole.Amount, "EUR" to TokenRole.Currency, "SHOP" to TokenRole.Payee),
            )
            assertEquals(
                "SHOP",
                TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR at SHOP ref $word")?.payee,
            )
            assertNull(
                TemplateMatcher.match(built.pattern, built.fields, false, null, "Paid 3,40 EUR at SHOP ref ${word}x")
            )
        }
    }

    @Test(timeout = 5_000)
    fun longNonMatchingTextWithSeveralLazyRunsIsFast() {
        val built = build(
            "From SHOP for stuff note things. Paid 3,40 EUR",
            marksByText = mapOf(
                "SHOP" to TokenRole.Payee, "stuff" to TokenRole.Varies, "things" to TokenRole.Varies,
                "3,40" to TokenRole.Amount, "EUR" to TokenRole.Currency,
            ),
        )
        val text = "From " + "x for y note ".repeat(80) + "z. Paid 3,40 EU1"
        assertNull(TemplateMatcher.match(built.pattern, built.fields, false, null, text))
    }
}
