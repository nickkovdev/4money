/* Copyright 2026 Nikita Kovalovs

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

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.FakeInboxRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.transfers.logic.RevertTransferUseCase

class UndoInboxItemUseCaseTest {

    private class RecordingRevertTransferUseCase(
        private val failWith: Throwable? = null,
    ) : RevertTransferUseCase {
        val reverted = mutableListOf<String>()

        override suspend fun invoke(transferId: String): Result<Unit> {
            reverted += transferId
            return failWith?.let(Result.Companion::failure) ?: Result.success(Unit)
        }
    }

    private fun doneItem(transferId: String?) = InboxItem(
        id = "item",
        receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
        sourcePackage = "se.seb.latvia",
        rawText = "x",
        amount = null,
        currencyCode = null,
        payee = null,
        cardLast4 = null,
        accountId = null,
        status = InboxItem.Status.Done,
        transferId = transferId,
        dedupHash = "h",
    )

    @Test
    fun revertsTransferAndReturnsItemToPending() = runBlocking {
        val inbox = FakeInboxRepository()
        val revert = RecordingRevertTransferUseCase()
        val useCase = UndoInboxItemUseCase(
            inboxRepository = inbox,
            revertTransferUseCase = revert,
            transferExists = { true },
        )
        val item = doneItem(transferId = "tr")
        inbox.addItem(item)

        useCase(item).getOrThrow()

        assertEquals(listOf("tr"), revert.reverted)
        val updated = inbox.items.value.single()
        assertEquals(InboxItem.Status.Pending, updated.status)
        assertNull(updated.transferId)
    }

    @Test
    fun keepsItemDoneIfRevertFails() = runBlocking {
        val inbox = FakeInboxRepository()
        val useCase = UndoInboxItemUseCase(
            inboxRepository = inbox,
            revertTransferUseCase = RecordingRevertTransferUseCase(failWith = IllegalStateException("boom")),
            transferExists = { true },
        )
        val item = doneItem(transferId = "tr")
        inbox.addItem(item)

        assertTrue(useCase(item).isFailure)

        val unchanged = inbox.items.value.single()
        assertEquals(InboxItem.Status.Done, unchanged.status)
        assertEquals("tr", unchanged.transferId)
    }

    @Test
    fun returnsItemWithoutTransferToPendingWithoutReverting() = runBlocking {
        val inbox = FakeInboxRepository()
        val revert = RecordingRevertTransferUseCase()
        val useCase = UndoInboxItemUseCase(
            inboxRepository = inbox,
            revertTransferUseCase = revert,
            transferExists = { true },
        )
        val item = doneItem(transferId = null)
        inbox.addItem(item)

        useCase(item).getOrThrow()

        assertTrue(revert.reverted.isEmpty())
        assertEquals(InboxItem.Status.Pending, inbox.items.value.single().status)
    }

    @Test
    fun skipsRevertAndReturnsItemToPendingIfTransferIsGone() = runBlocking {
        val inbox = FakeInboxRepository()
        val revert = RecordingRevertTransferUseCase()
        val useCase = UndoInboxItemUseCase(
            inboxRepository = inbox,
            revertTransferUseCase = revert,
            transferExists = { false },
        )
        val item = doneItem(transferId = "tr")
        inbox.addItem(item)

        useCase(item).getOrThrow()

        assertTrue(revert.reverted.isEmpty())
        val updated = inbox.items.value.single()
        assertEquals(InboxItem.Status.Pending, updated.status)
        assertNull(updated.transferId)
    }

    @Test
    fun retryAfterMarkPendingFailureSucceeds() = runBlocking {
        val inbox = FakeInboxRepository()
        var transferDeleted = false
        var failMarkPending = true
        val flakyInbox = object : InboxRepository by inbox {
            override suspend fun markPending(itemId: String) {
                if (failMarkPending) {
                    failMarkPending = false
                    throw IllegalStateException("boom")
                }
                inbox.markPending(itemId)
            }
        }
        val useCase = UndoInboxItemUseCase(
            inboxRepository = flakyInbox,
            revertTransferUseCase = object : RevertTransferUseCase {
                override suspend fun invoke(transferId: String): Result<Unit> {
                    check(!transferDeleted) { "Reverting a deleted transfer" }
                    transferDeleted = true
                    return Result.success(Unit)
                }
            },
            transferExists = { !transferDeleted },
        )
        val item = doneItem(transferId = "tr")
        inbox.addItem(item)

        assertTrue(useCase(item).isFailure)
        assertEquals(InboxItem.Status.Done, inbox.items.value.single().status)

        useCase(item).getOrThrow()
        assertEquals(InboxItem.Status.Pending, inbox.items.value.single().status)
    }
}
