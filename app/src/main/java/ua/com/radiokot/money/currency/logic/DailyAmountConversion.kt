/*
 * Copyright 2025 Oleg Koretsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
