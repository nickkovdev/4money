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

import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.logic.displayAmountText
import ua.com.radiokot.money.uikit.ViewText
import java.util.Locale

@Immutable
class ViewInboxItem(
    val title: String,
    val amountText: ViewText?,
    val receivedAt: LocalDateTime,
    val isForeignCurrency: Boolean,
    val key: String,
    val source: InboxItem? = null,
    /**
     * The amount in the app format: negative for outgoing, null if not parsed.
     */
    val amount: ViewAmount? = null,
    val isIncoming: Boolean = false,
    /**
     * The [title] is the last line of the raw bank text, which usually contains the amount.
     */
    val isTitleRawText: Boolean = false,
) {
    /**
     * @param accountCurrencyCode currency of the item's account, if known
     */
    constructor(
        item: InboxItem,
        accountCurrencyCode: String?,
    ) : this(
        title = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?: item.rawText.lineSequence().last().take(120),
        amountText = item.displayAmountText(Locale.ROOT)
            .takeIf(String::isNotEmpty)
            ?.let {
                ViewText.Dynamic { context ->
                    val amountText = item.displayAmountText(context.resources.configuration.locales[0])
                    if (item.direction == InboxItem.Direction.Incoming)
                        "+$amountText"
                    else
                        amountText
                }
            },
        receivedAt = item.receivedAt,
        isForeignCurrency = accountCurrencyCode != null
                && item.currencyCode != null
                && !item.currencyCode.equals(accountCurrencyCode, ignoreCase = true),
        key = item.id,
        source = item,
        amount = viewAmountOf(item),
        isIncoming = item.direction == InboxItem.Direction.Incoming,
        isTitleRawText = item.payee?.let(PayeeNormalizer::displayName) == null,
    )
}

/**
 * The bank amount as an app amount, e.g. 18.90 EUR → −18.90 €,
 * with the currency symbol and precision of [java.util.Currency].
 */
fun viewAmountOf(item: InboxItem): ViewAmount? {
    val amount = item.amount
        ?: return null
    val currencyCode = item.currencyCode
        ?: return null
    val javaCurrency = runCatching {
        java.util.Currency.getInstance(currencyCode.uppercase())
    }.getOrNull()
    val precision = javaCurrency
        ?.defaultFractionDigits
        ?.takeIf { it >= 0 }
        ?: 2
    val minorUnits = amount
        .movePointRight(precision)
        .setScale(0, java.math.RoundingMode.HALF_UP)
        .toBigIntegerExact()

    return ViewAmount(
        value =
            if (item.direction == InboxItem.Direction.Incoming)
                minorUnits.abs()
            else
                minorUnits.abs().negate(),
        currency = ViewCurrency(
            symbol = javaCurrency?.symbol ?: currencyCode,
            precision = precision,
        ),
    )
}
