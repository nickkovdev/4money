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

/**
 * Tells notifications that may be payments, so they are worth keeping as samples.
 */
object MoneyTextHeuristic {

    // Upper case only, so ordinary words ("eur-like", "Europe") don't count.
    private const val CODES =
        "EUR|USD|GBP|UAH|PLN|CHF|SEK|NOK|DKK|CZK|HUF|RON|BGN|RUB|BYN|MDL|GEL|KZT|TRY|" +
                "RSD|ISK|CAD|AUD|NZD|JPY|CNY|INR|ILS|AED|KRW|SGD|HKD"
    private const val CURRENCY =
        "(?:(?<!\\p{L})(?:$CODES)(?!\\p{L})|[${TemplateAmounts.CURRENCY_SYMBOLS}])"
    private const val SPACE = "[ \\u00A0\\u202F]?"

    private val moneyRegex = Regex(
        "$CURRENCY$SPACE[-\\u2212+]?\\d|\\d$SPACE$CURRENCY"
    )

    /**
     * Cheap: one regex search.
     *
     * @return whether the [text] has an amount next to an upper-case ISO code
     * (of a fixed set of common ones) or a currency symbol (€ $ £ …)
     */
    fun looksLikeMoney(text: String): Boolean =
        moneyRegex.containsMatchIn(text)
}
