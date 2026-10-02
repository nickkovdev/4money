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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.data.AmountRange
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Format strings of amount ranges, each but [any] taking the amount (and [between] two of them).
 */
class RangeTexts(
    val between: String,
    val upTo: String,
    val under: String,
    val from: String,
    val over: String,
    val any: String,
)

fun rangeTextsOf(context: Context): RangeTexts =
    RangeTexts(
        between = context.getString(R.string.inbox_range_between),
        upTo = context.getString(R.string.inbox_range_up_to),
        under = context.getString(R.string.inbox_range_under),
        from = context.getString(R.string.inbox_range_from),
        over = context.getString(R.string.inbox_range_over),
        any = context.getString(R.string.inbox_range_any),
    )

@Composable
fun rememberRangeTexts(): RangeTexts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { rangeTextsOf(context) }
}

/**
 * A short human range: "Under 10 €", "Up to 10 €", "10–35 €", "Over 35 €", "From 35 €".
 *
 * @param currencyCode to show the symbol, nothing if null or unknown
 * @param locale to format the numbers
 * @param texts format strings in the app language
 */
fun describeRange(
    range: AmountRange,
    currencyCode: String?,
    locale: Locale,
    texts: RangeTexts,
): String {
    val symbol = currencyCode
        ?.let { code ->
            runCatching { java.util.Currency.getInstance(code.uppercase()).symbol }
                .getOrDefault(code)
        }
        ?.let { " $it" }
        .orEmpty()

    val numberFormat = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
    }

    fun format(value: BigDecimal): String =
        numberFormat.format(value)

    val min = range.min
    val max = range.max

    return when {
        min != null && max != null ->
            texts.between.format(format(min), format(max)) + symbol

        max != null ->
            (if (range.isMaxInclusive) texts.upTo else texts.under).format(format(max)) + symbol

        min != null ->
            (if (range.isMinInclusive) texts.from else texts.over).format(format(min)) + symbol

        else ->
            texts.any
    }
}

/**
 * [describeRange] resolved with the app language when shown.
 */
fun describeRangeText(
    range: AmountRange,
    currencyCode: String?,
): (Context) -> String = { context ->
    describeRange(
        range = range,
        currencyCode = currencyCode,
        locale = context.resources.configuration.locales[0],
        texts = rangeTextsOf(context),
    )
}
