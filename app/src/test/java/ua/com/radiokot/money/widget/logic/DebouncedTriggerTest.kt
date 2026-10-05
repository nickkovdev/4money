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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DebouncedTriggerTest {

    @Test
    fun burstCollapsesToOneRun() = runBlocking {
        val runs = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val trigger = DebouncedTrigger(scope, delayMs = 100) { runs.incrementAndGet() }
        repeat(20) { trigger.trigger() }
        delay(400)
        assertEquals(1, runs.get())
        scope.cancel()
    }

    @Test
    fun separateBurstsRunSeparately() = runBlocking {
        val runs = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val trigger = DebouncedTrigger(scope, delayMs = 50) { runs.incrementAndGet() }
        trigger.trigger(); delay(300)
        trigger.trigger(); delay(300)
        assertEquals(2, runs.get())
        scope.cancel()
    }

    @Test
    fun failingActionDoesNotStopLaterRuns() = runBlocking {
        val runs = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val trigger = DebouncedTrigger(scope, delayMs = 50) {
            if (runs.incrementAndGet() == 1) error("boom")
        }
        trigger.trigger(); delay(300)
        trigger.trigger(); delay(300)
        assertEquals(2, runs.get())
        scope.cancel()
    }
}
