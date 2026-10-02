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
import ua.com.radiokot.money.inbox.data.InboxItem
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

object InboxTransferPrefill {

    /**
     * @return the regular transfer sheet route for [item] paid from [account] to [category].
     * Amounts are prefilled only when the item is in the account currency;
     * a foreign amount is shown in the memo for the user to convert.
     */
    fun buildRoute(
        item: InboxItem,
        account: Account,
        category: Category,
    ): TransferSheetRoute {
        val isInAccountCurrency = item.currencyCode != null
                && item.currencyCode.equals(account.currency.code, ignoreCase = true)

        val sourceAmount = item.amount
            ?.takeIf { isInAccountCurrency }
            ?.let { AutoExpenseResolver.toMinorUnits(it, account.currency.precision) }

        val destinationAmount = sourceAmount
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

        return TransferSheetRoute(
            sourceId = TransferCounterpartyId.Account(account.id),
            destinationId = TransferCounterparty.Category(category).id,
            sourceAmount = sourceAmount,
            destinationAmount = destinationAmount,
            memo = memo,
            dateTime = item.receivedAt,
            inboxItemId = item.id,
            rememberPayee = item.payee
                ?.let(PayeeNormalizer::normalize)
                ?.takeIf(String::isNotEmpty),
        )
    }
}
