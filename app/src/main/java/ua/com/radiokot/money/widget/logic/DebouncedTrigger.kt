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

package ua.com.radiokot.money.widget.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.com.radiokot.money.lazyLogger

/**
 * Runs [action] once [delayMs] after the last [trigger] call; a newer call restarts the wait.
 * An action failure is logged and does not stop later runs.
 */
class DebouncedTrigger(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val action: suspend () -> Unit,
) {
    private val log by lazyLogger("DebouncedTrigger")
    private val lock = Any()
    private var job: Job? = null

    fun trigger() = synchronized(lock) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "trigger(): action failed" }
            }
        }
    }
}
