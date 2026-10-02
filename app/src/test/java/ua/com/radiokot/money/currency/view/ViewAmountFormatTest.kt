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

package ua.com.radiokot.money.currency.view


import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class ViewAmountFormatTest {
    private val usd = ViewCurrency(
        symbol = "$",
        precision = 2,
    )
    private val btc = ViewCurrency(
        symbol = "B",
        precision = 8,
    )

    @Test
    fun formatForInput() {
        val format = ViewAmountFormat(
            locale = Locale.ENGLISH,
        )
        val formatFr = ViewAmountFormat(
            locale = Locale.FRENCH,
        )

        Assert.assertEquals(
            "-0.0000005",
            format.formatInput(
                value = BigInteger("-50"),
                currency = btc,
            )
        )
        Assert.assertEquals(
            "10 000 000,05",
            formatFr.formatInput(
                value = BigInteger("1000000005"),
                currency = usd,
            )
        )
        Assert.assertEquals(
            "10,000,000.05",
            format.formatInput(
                value = BigInteger("1000000005"),
                currency = usd,
            )
        )
        Assert.assertEquals(
            "1,05",
            formatFr.formatInput(
                value = BigInteger("105"),
                currency = usd,
            )
        )
        Assert.assertEquals(
            "1.05",
            format.formatInput(
                value = BigInteger("105"),
                currency = usd,
            )
        )
    }

    @Test
    fun parseInput_IfCorrect() {
        val format = ViewAmountFormat(
            locale = Locale.ENGLISH,
        )
        val formatFr = ViewAmountFormat(
            locale = Locale.FRENCH,
        )

        Assert.assertEquals(
            BigInteger("0"),
            format.parseInput(
                input = "-",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("0"),
            format.parseInput(
                input = "-0.",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("0"), format.parseInput(
                input = "-.",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("-50"),
            format.parseInput(
                input = "-.5",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("-50"),
            format.parseInput(
                input = "-0.5",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("-104"),
            format.parseInput(
                input = "-1.04",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("505000"),
            format.parseInput(
                input = "0.005050",
                currency = btc,
            )
        )
        Assert.assertEquals(
            BigInteger("101"),
            format.parseInput(
                input = "1.01",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("1"),
            format.parseInput(
                input = "0.01",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("1"),
            format.parseInput(
                input = ".01",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("10"),
            format.parseInput(
                input = ".1",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("5200"),
            format.parseInput(
                input = "52.",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("5234"),
            format.parseInput(
                input = "52.34",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("0"),
            format.parseInput(
                input = "0",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("0"),
            format.parseInput(
                input = "000000.00",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("0"),
            format.parseInput(
                input = "000000.0000000",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("0"),
            format.parseInput(
                input = "",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("38050202415100"),
            format.parseInput(
                input = "380,502,024,151",
                currency = usd,
            )
        )
        Assert.assertEquals(
            BigInteger("38050202415121"),
            formatFr.parseInput(
                input = "380 502 024 151,21",
                currency = usd,
            )
        )
    }

    @Test
    fun notParseInput_IfIncorrect() {
        val format = ViewAmountFormat(
            locale = Locale.ENGLISH,
        )

        Assert.assertNull(
            format.parseInput(
                input = "2.43$", // Input must not contain the currency symbol.
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "1 25",
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "1. 25",
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "1 .25",
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "O", // letter O
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "9.99.99",
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = ".999999",
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "--1.04",
                currency = usd,
            )
        )
        Assert.assertNull(
            format.parseInput(
                input = "1.-04",
                currency = usd,
            )
        )
    }

    @Test
    fun invoke_UsesConfiguredSignColors() {
        val positive = androidx.compose.ui.graphics.Color(0xFF00FF00)
        val negative = androidx.compose.ui.graphics.Color(0xFFFF0000)
        val zero = androidx.compose.ui.graphics.Color(0xFF0000FF)
        val format = ViewAmountFormat(
            locale = Locale.ENGLISH,
            positiveColor = positive,
            negativeColor = negative,
            zeroColor = zero,
        )

        Assert.assertEquals(
            positive,
            format(value = BigInteger("150"), currency = usd).spanStyles.first().item.color,
        )
        Assert.assertEquals(
            negative,
            format(value = BigInteger("-150"), currency = usd).spanStyles.first().item.color,
        )
        Assert.assertEquals(
            zero,
            format(value = BigInteger.ZERO, currency = usd).spanStyles.first().item.color,
        )
    }

    @Test
    fun invoke_CustomColorWins() {
        val custom = androidx.compose.ui.graphics.Color(0xFF123456)
        val format = ViewAmountFormat(locale = Locale.ENGLISH)

        Assert.assertEquals(
            custom,
            format(value = BigInteger("150"), currency = usd, customColor = custom)
                .spanStyles.first().item.color,
        )
    }

    @Test
    fun privateTextHasNoCurrencyAndKeepsColorRule() {
        val red = androidx.compose.ui.graphics.Color.Red
        val blue = androidx.compose.ui.graphics.Color.Blue
        val format = ViewAmountFormat(
            locale = Locale.US,
            positiveColor = androidx.compose.ui.graphics.Color.Green,
            negativeColor = red,
            zeroColor = androidx.compose.ui.graphics.Color.Gray,
        )

        val text = format.privateText("•••", BigInteger.valueOf(-5))
        Assert.assertEquals("•••", text.text)
        Assert.assertEquals(red, text.spanStyles.single().item.color)

        val custom = format.privateText("12%", BigInteger.ONE, customColor = blue)
        Assert.assertEquals("12%", custom.text)
        Assert.assertEquals(blue, custom.spanStyles.single().item.color)
    }

    private val ru = Locale.forLanguageTag("ru")
    private val eur = ViewCurrency(symbol = "€", precision = 2)

    @Test
    fun invoke_English() {
        val format = ViewAmountFormat(Locale.ENGLISH)

        Assert.assertEquals(
            "1,234,567.89 €",
            format(BigInteger.valueOf(123456789), eur).text,
        )
    }

    @Test
    fun invoke_Russian() {
        val format = ViewAmountFormat(ru)
        val grouping = format.groupingSeparator

        Assert.assertTrue(Character.isSpaceChar(grouping))
        Assert.assertEquals(',', format.decimalSeparator)
        Assert.assertEquals(
            "1${grouping}234${grouping}567,89 €",
            format(BigInteger.valueOf(123456789), eur).text,
        )
    }

    @Test
    fun invoke_RussianNegativeStartsWithMinusSign() {
        val format = ViewAmountFormat(ru)

        val text = format(BigInteger.valueOf(-150), eur).text

        Assert.assertTrue(text.startsWith(format.minusSign))
        Assert.assertEquals("${format.minusSign}1,50 €", text)
    }

    @Test
    fun formatInputAndParseInput_RussianRoundTrip() {
        val format = ViewAmountFormat(ru)

        Assert.assertEquals(
            BigInteger.valueOf(123450),
            format.parseInput("1234,5", eur),
        )
        Assert.assertEquals(
            "1${format.groupingSeparator}234,5",
            format.formatInput("1234,5"),
        )
        Assert.assertEquals(
            BigInteger.valueOf(123450),
            format.parseInput(format.formatInput(BigInteger.valueOf(123450), eur), eur),
        )
    }

    @Test
    fun privateText_RussianUnchanged() {
        val format = ViewAmountFormat(ru)

        Assert.assertEquals("12%", format.privateText("12%", BigInteger.ONE).text)
        Assert.assertEquals("<1%", format.privateText("<1%", BigInteger.ONE).text)
    }
}
