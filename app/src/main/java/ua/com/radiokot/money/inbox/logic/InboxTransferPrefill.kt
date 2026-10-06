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

import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.inbox.data.InboxItem
import java.text.NumberFormat
import java.util.Locale
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferSheetRoute

/**
 * @return the amount as written by the bank, with the decimal comma and the currency code,
 * e.g. "2,12 USD". Empty if neither is known.
 */
fun InboxItem.originalAmountText(): String =
    "${amount?.toPlainString()?.replace('.', ',').orEmpty()} ${currencyCode.orEmpty()}"
        .trim()

/**
 * @return the amount in the number format of the [locale], with the currency code,
 * e.g. "1,234.50 USD" or "1 234,50 USD". Empty if neither is known.
 */
fun InboxItem.displayAmountText(locale: Locale): String =
    listOfNotNull(
        amount?.let { amount ->
            NumberFormat.getNumberInstance(locale)
                .apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = maxOf(2, amount.stripTrailingZeros().scale())
                }
                .format(amount)
        },
        currencyCode?.takeIf(String::isNotEmpty),
    ).joinToString(" ")

object InboxTransferPrefill {

    /**
     * @return the regular transfer sheet route for [item] paid from [account] to [category],
     * or, for an incoming item, received from [category] to [account].
     * Amounts are prefilled only when the item is in the account currency;
     * a foreign amount is shown in the memo for the user to convert.
     */
    fun buildRoute(
        item: InboxItem,
        account: Account,
        category: Category,
        subcategory: Subcategory? = null,
    ): TransferSheetRoute {
        val isInAccountCurrency = item.currencyCode != null
                && item.currencyCode.equals(account.currency.code, ignoreCase = true)

        val accountAmount = item.amount
            ?.takeIf { isInAccountCurrency }
            ?.let { AutoExpenseResolver.toMinorUnits(it, account.currency.precision) }

        val categoryAmount = accountAmount
            ?.takeIf { category.currency == account.currency }

        val payee = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?.takeIf(String::isNotEmpty)

        val memo =
            if (isInAccountCurrency || item.amount == null || item.currencyCode == null)
                payee
            else
                listOfNotNull(
                    payee,
                    item.originalAmountText(),
                ).joinToString(" · ")

        val accountId = TransferCounterpartyId.Account(account.id)
        val categoryId = TransferCounterparty.Category(category, subcategory).id
        val isIncoming = item.direction == InboxItem.Direction.Incoming

        return TransferSheetRoute(
            sourceId = if (isIncoming) categoryId else accountId,
            destinationId = if (isIncoming) accountId else categoryId,
            sourceAmount = if (isIncoming) categoryAmount else accountAmount,
            destinationAmount = if (isIncoming) accountAmount else categoryAmount,
            memo = memo,
            dateTime = item.receivedAt,
            inboxItemId = item.id,
            rememberPayee = item.payee
                ?.let(PayeeNormalizer::normalize)
                ?.takeIf(String::isNotEmpty),
        )
    }
}
