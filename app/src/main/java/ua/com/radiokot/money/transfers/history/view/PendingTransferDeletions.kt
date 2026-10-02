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

package ua.com.radiokot.money.transfers.history.view

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Deletions that can be undone until they are taken for committing.
 * Taken (committing or committed) ones stay hidden, unless restored after a failure.
 */
class PendingTransferDeletions {

    private data class State(
        val pending: Set<String> = emptySet(),
        val committing: Set<String> = emptySet(),
    ) {
        val hidden: Set<String>
            get() = pending + committing
    }

    private val state = MutableStateFlow(State())

    val hiddenIds: Set<String>
        get() = state.value.hidden

    val hiddenIdsFlow: Flow<Set<String>> =
        state
            .map { it.hidden }
            .distinctUntilChanged()

    /**
     * @return true if scheduled, false if it is already pending or taken.
     */
    fun schedule(transferId: String): Boolean {
        var isScheduled = false
        state.update { current ->
            isScheduled = transferId !in current.hidden
            if (isScheduled)
                current.copy(pending = current.pending + transferId)
            else
                current
        }
        return isScheduled
    }

    /**
     * @return true if the deletion was pending and is now cancelled.
     */
    fun undo(transferId: String): Boolean {
        var isUndone = false
        state.update { current ->
            isUndone = transferId in current.pending
            current.copy(pending = current.pending - transferId)
        }
        return isUndone
    }

    /**
     * Takes the deletion for committing.
     *
     * @return true if it was pending, so the caller must commit it exactly once.
     */
    fun take(transferId: String): Boolean {
        var isTaken = false
        state.update { current ->
            isTaken = transferId in current.pending
            if (isTaken)
                current.copy(
                    pending = current.pending - transferId,
                    committing = current.committing + transferId,
                )
            else
                current
        }
        return isTaken
    }

    /**
     * Takes all the pending deletions for committing.
     */
    fun takeAll(): Set<String> {
        var taken: Set<String> = emptySet()
        state.update { current ->
            taken = current.pending
            current.copy(
                pending = emptySet(),
                committing = current.committing + current.pending,
            )
        }
        return taken
    }

    /**
     * Shows a taken transfer again, e.g. when committing failed.
     */
    fun restore(transferId: String) {
        state.update { current ->
            current.copy(committing = current.committing - transferId)
        }
    }
}
