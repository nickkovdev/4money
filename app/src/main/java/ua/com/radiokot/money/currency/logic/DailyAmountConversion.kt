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

package ua.com.radiokot.money.currency.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.currency.data.CurrencyPairMap
import java.math.BigInteger

/**
 * Converts the [amount] of [base] transferred on [dayString] (YYYY-MM-DD)
 * to [quote] using [dailyPrices] of that day. If there's no price for this day,
 * which could happen due to time zone differences, the previous day is used.
 *
 * @return the converted amount or null if there's no price.
 */
fun convertDailyAmount(
    dayString: String,
    amount: BigInteger,
    base: Currency,
    quote: Currency,
    dailyPrices: Map<String, CurrencyPairMap>,
): BigInteger? {
    if (base.code == quote.code) {
        return amount
    }

    val pricesForTheDay: CurrencyPairMap =
        dailyPrices[dayString]
            ?: dailyPrices[
                LocalDate
                    .parse(dayString, LocalDate.Formats.ISO)
                    .minus(1, DateTimeUnit.DAY)
                    .toString()
            ]
            ?: return null

    return pricesForTheDay
        .get(
            base = base,
            quote = quote,
        )
        ?.baseToQuote(amount)
}
