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

package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/**
 * SEB Latvia push notifications.
 *
 * Sampled card payment (title / text):
 * ```
 * Jauna rezervācija
 * Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .
 * ```
 * The date/time in the text is not local time and is ignored.
 */
class SebLatviaNotificationParser : BankNotificationParser {

    override val packageName: String = PACKAGE_NAME

    override fun parse(title: String?, text: String): ParsedBankNotification {
        val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace(' ', ' ')
            .replace(' ', ' ')
            .trim()

        val match = CARD_PAYMENT_REGEX.matchEntire(normalizedText)
            ?: return ParsedBankNotification.Unrecognized

        val (amountString, currencyCode, cardLast4, payee) = match.destructured

        val amount = parseAmount(amountString)
            ?.takeIf { it.signum() > 0 }
            ?: return ParsedBankNotification.Unrecognized

        return ParsedBankNotification.CardPayment(
            amount = amount,
            currencyCode = currencyCode.uppercase(Locale.ROOT),
            cardLast4 = cardLast4,
            payee = payee.trim(),
        )
    }

    private fun parseAmount(amountString: String): BigDecimal? =
        amountString
            .replace(" ", "")
            .replace(',', '.')
            .toBigDecimalOrNull()

    companion object {
        const val PACKAGE_NAME = "se.seb.latvia"

        // 1: amount with decimal comma and optional space thousands separators,
        // 2: ISO currency, 3: card last 4, 4: payee (lazy, without the trailing " .").
        private val CARD_PAYMENT_REGEX = Regex(
            "^Jūs samaksājāt\\s+(\\d{1,3}(?: \\d{3})+(?:,\\d+)?|\\d+(?:,\\d+)?)\\s+([A-Za-z]{3})" +
                    "\\s+par\\s+\\S+\\s+\\S+\\s+karte\\s*(?:\\.{2,}|…)\\s*(\\d{4})\\s+(.+?)\\s*\\.?$",
            RegexOption.IGNORE_CASE,
        )
    }
}
