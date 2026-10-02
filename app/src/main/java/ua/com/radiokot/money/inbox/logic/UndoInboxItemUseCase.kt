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

import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.transfers.logic.RevertTransferUseCase

/**
 * Undoes an auto-created (or inbox-created) expense: the transfer is reverted
 * with its balance effect, the item goes back to pending.
 * If the transfer no longer exists, the item just goes back to pending.
 */
class UndoInboxItemUseCase(
    private val inboxRepository: InboxRepository,
    private val revertTransferUseCase: RevertTransferUseCase,
    private val transferExists: suspend (transferId: String) -> Boolean,
) {

    suspend operator fun invoke(item: InboxItem): Result<Unit> = runCatching {
        val transferId = item.transferId
        // The transfer may be already deleted (e.g. in the history):
        // there is nothing to revert then, but the item must still be released.
        if (transferId != null && transferExists(transferId)) {
            revertTransferUseCase(transferId).getOrThrow()
        }

        inboxRepository.markPending(item.id)
    }
}
