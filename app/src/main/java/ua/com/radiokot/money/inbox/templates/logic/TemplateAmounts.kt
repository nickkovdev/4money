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

import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * Amounts and currencies as banks write them in notifications.
 */
object TemplateAmounts {

    /**
     * Group-free amount regex fragment, unsigned: an integer, a number with a 1-2 digit
     * decimal part, or digits with space/dot/comma thousands separators and an optional
     * decimal part ("1 500,00", "1.500,00", "1,500.00", "1.234").
     * Never starts or ends in the middle of a digit run.
     */
    const val AMOUNT_PATTERN: String =
        "(?<!\\d)(?:\\d{1,3}(?:[ .,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)(?!\\d)"

    /**
     * Single-char currency symbols, each with one unambiguous ISO code.
     */
    const val CURRENCY_SYMBOLS: String = "€\$£₴₽₸₾₺₹₪₩"

    /**
     * Group-free currency regex fragment: 3 letters not inside a word (validated as an ISO
     * code by [parseCurrency]) or one of the [CURRENCY_SYMBOLS] (€ $ £ and a few more).
     */
    const val CURRENCY_PATTERN: String =
        "(?:(?<!\\p{L})[A-Za-z]{3}(?!\\p{L})|[$CURRENCY_SYMBOLS])"

    private val symbolCodes: Map<Char, String> = mapOf(
        '€' to "EUR",
        '$' to "USD",
        '£' to "GBP",
        '₴' to "UAH",
        '₽' to "RUB",
        '₸' to "KZT",
        '₾' to "GEL",
        '₺' to "TRY",
        '₹' to "INR",
        '₪' to "ILS",
        '₩' to "KRW",
    )

    private val isoCodes: Set<String> by lazy {
        Currency.getAvailableCurrencies().mapTo(HashSet(), Currency::getCurrencyCode)
    }

    private val signChars = charArrayOf('-', '−', '+')

    /**
     * Parses an amount: "1 500,00" → 1500.00, "1.234" → 1234, "3,4" → 3.4.
     * The last dot or comma followed by 1-2 digits is the decimal separator, all the other
     * dots, commas and spaces are thousands separators, each followed by exactly 3 digits.
     * A leading sign (- − +) is ignored.
     *
     * @return a positive amount, or null when the text is not one
     */
    fun parseAmount(text: String): BigDecimal? {
        val unsigned = text
            .replace(' ', ' ')
            .replace(' ', ' ')
            .trim()
            .trimStart(*signChars)

        if (unsigned.isEmpty()
            || !unsigned.first().isAsciiDigit()
            || !unsigned.last().isAsciiDigit()
            || unsigned.any { !it.isAsciiDigit() && it != ' ' && it != '.' && it != ',' }
        ) {
            return null
        }

        val lastSeparatorIndex = unsigned.indexOfLast { it == '.' || it == ',' }
        val digitsAfterLastSeparator = unsigned.length - lastSeparatorIndex - 1
        val integerPart: String
        val fractionPart: String?
        if (lastSeparatorIndex >= 0 && digitsAfterLastSeparator in 1..2) {
            integerPart = unsigned.substring(0, lastSeparatorIndex)
            fractionPart = unsigned.substring(lastSeparatorIndex + 1)
        } else {
            integerPart = unsigned
            fractionPart = null
        }

        if (fractionPart != null && !fractionPart.all { it.isAsciiDigit() }) {
            return null
        }

        val groups = integerPart.split(' ', '.', ',')
        if (groups.any { group -> group.isEmpty() || !group.all { it.isAsciiDigit() } }) {
            return null
        }
        if (groups.size > 1
            && (groups.first().length > 3 || groups.drop(1).any { it.length != 3 })
        ) {
            return null
        }

        val plain = groups.joinToString("") +
                if (fractionPart != null) ".$fractionPart" else ""

        return plain
            .toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 }
    }

    /**
     * Parses a currency: "€" → "EUR", "$" → "USD", "£" → "GBP", "eur" → "EUR".
     *
     * @return an upper-case ISO 4217 code, or null when the text is not a currency
     */
    fun parseCurrency(text: String): String? {
        val trimmed = text.trim()

        if (trimmed.length == 1) {
            return symbolCodes[trimmed[0]]
        }

        if (trimmed.length != 3 || !trimmed.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            return null
        }

        return trimmed
            .uppercase(Locale.ROOT)
            .takeIf(::isIsoCode)
    }

    /**
     * @return whether the [code] is a known upper-case ISO 4217 currency code
     */
    fun isIsoCode(code: String): Boolean =
        code in isoCodes

    private fun Char.isAsciiDigit(): Boolean =
        this in '0'..'9'
}
