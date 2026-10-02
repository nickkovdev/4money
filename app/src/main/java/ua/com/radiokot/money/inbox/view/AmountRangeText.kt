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

package ua.com.radiokot.money.inbox.view

import ua.com.radiokot.money.inbox.data.AmountRange
import java.math.BigDecimal

/**
 * A short human range: "Under 10 €", "Up to 10 €", "10–35 €", "Over 35 €", "From 35 €".
 *
 * @param currencyCode to show the symbol, nothing if null or unknown
 */
fun describeRange(
    range: AmountRange,
    currencyCode: String?,
): String {
    val symbol = currencyCode
        ?.let { code ->
            runCatching { java.util.Currency.getInstance(code.uppercase()).symbol }
                .getOrDefault(code)
        }
        ?.let { " $it" }
        .orEmpty()

    fun format(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    val min = range.min
    val max = range.max

    return when {
        min != null && max != null ->
            "${format(min)}–${format(max)}$symbol"

        max != null ->
            (if (range.isMaxInclusive) "Up to " else "Under ") + format(max) + symbol

        min != null ->
            (if (range.isMinInclusive) "From " else "Over ") + format(min) + symbol

        else ->
            "Any amount"
    }
}
