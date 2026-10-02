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
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import java.math.BigInteger

/**
 * What happens when an inbox card is accepted with a category:
 * a transfer is recorded right away when the amounts are unambiguous,
 * otherwise the prefilled transfer sheet opens for the user to check.
 */
object InboxCardAcceptance {

    sealed interface Decision {

        /**
         * Record the transfer without the sheet.
         */
        data class Record(
            val sourceId: TransferCounterpartyId,
            val sourceAmount: BigInteger,
            val destinationId: TransferCounterpartyId,
            val destinationAmount: BigInteger,
            val memo: String?,
        ) : Decision

        /**
         * Let the user check or convert the amount in the transfer sheet.
         */
        data class OpenSheet(
            val route: TransferSheetRoute,
        ) : Decision
    }

    /**
     * Records directly only when the bank amount is in the account currency,
     * the category has the same currency and the amount fits its precision.
     */
    fun decide(
        item: InboxItem,
        account: Account,
        category: Category,
        subcategory: Subcategory?,
    ): Decision {
        val amount = item.amount
        val isSameCurrency = item.currencyCode != null
                && item.currencyCode.equals(account.currency.code, ignoreCase = true)
                && category.currency == account.currency
        val minorUnits = amount
            ?.takeIf { isSameCurrency && it.signum() > 0 }
            ?.let { AutoExpenseResolver.toMinorUnits(it, account.currency.precision) }

        if (minorUnits == null) {
            return Decision.OpenSheet(
                route = InboxTransferPrefill.buildRoute(
                    item = item,
                    account = account,
                    category = category,
                    subcategory = subcategory,
                )
            )
        }

        val accountId = TransferCounterpartyId.Account(account.id)
        val categoryId = TransferCounterparty.Category(category, subcategory).id
        val isIncoming = item.direction == InboxItem.Direction.Incoming
        val memo = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?.takeIf(String::isNotEmpty)

        return Decision.Record(
            sourceId = if (isIncoming) categoryId else accountId,
            sourceAmount = minorUnits,
            destinationId = if (isIncoming) accountId else categoryId,
            destinationAmount = minorUnits,
            memo = memo,
        )
    }
}
