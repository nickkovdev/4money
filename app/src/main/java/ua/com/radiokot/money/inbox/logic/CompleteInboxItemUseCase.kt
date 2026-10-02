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

import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId

/**
 * Called after the user saved the transfer opened from an inbox item.
 */
class CompleteInboxItemUseCase(
    private val inboxRepository: InboxRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
) {

    /**
     * @param rememberPayeePattern normalized payee to learn an exact rule for, null to not learn.
     * A rule is learned only for an account to category expense
     * or a category to account income.
     */
    suspend operator fun invoke(
        itemId: String,
        transferId: String,
        rememberPayeePattern: String?,
        sourceId: TransferCounterpartyId,
        destinationId: TransferCounterpartyId,
    ): Result<Unit> = runCatching {

        inboxRepository.markDone(
            itemId = itemId,
            transferId = transferId,
        )

        if (rememberPayeePattern.isNullOrEmpty()) {
            return@runCatching
        }

        val (accountId, categoryId) = when {
            sourceId is TransferCounterpartyId.Account
                    && destinationId is TransferCounterpartyId.Category ->
                sourceId to destinationId

            sourceId is TransferCounterpartyId.Category
                    && destinationId is TransferCounterpartyId.Account ->
                destinationId to sourceId

            else ->
                return@runCatching
        }

        payeeRuleRepository.saveRuleForPayee(
            payeePattern = rememberPayeePattern,
            matchType = PayeeRule.MatchType.Exact,
            categoryId = categoryId.categoryId,
            subcategoryId = categoryId.subcategoryId,
            accountId = accountId.accountId,
        )
    }
}
