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

package ua.com.radiokot.money.powersync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RefreshThrottleTest {

    private var elapsed: Duration = 100.seconds
    private var refreshes = 0
    private val throttle = RefreshThrottle(
        minInterval = 30.seconds,
        elapsed = { elapsed },
    )

    private fun tryRefresh(): Boolean = runBlocking {
        throttle.tryRefresh { refreshes++ }
    }

    @Test
    fun firstRefreshRuns() {
        assertTrue(tryRefresh())
        assertEquals(1, refreshes)
    }

    @Test
    fun secondRefreshWithin30SecondsIsSkipped() {
        tryRefresh()
        elapsed += 29.seconds
        assertFalse(tryRefresh())
        assertEquals(1, refreshes)
    }

    @Test
    fun refreshAfter30SecondsRunsAgain() {
        tryRefresh()
        elapsed += 30.seconds
        assertTrue(tryRefresh())
        assertEquals(2, refreshes)
    }

    @Test
    fun failedRefreshAlsoStartsTheInterval() {
        try {
            runBlocking { throttle.tryRefresh { error("network is down") } }
        } catch (_: IllegalStateException) {
        }
        elapsed += 5.seconds
        assertFalse(tryRefresh())
        assertEquals(0, refreshes)
    }

    @Test
    fun guardDoesNotRefreshTwiceWithin30Seconds() = runBlocking {
        val guard = UploadSessionGuard(
            // Always expired, so every call wants a refresh.
            currentSessionExpiresAt = { Instant.fromEpochSeconds(0) },
            refreshSession = { if (!throttle.tryRefresh { refreshes++ }) throw NoUserSessionException("throttled") },
            now = { Instant.fromEpochSeconds(1_000_000) },
        )
        repeat(3) {
            try {
                guard.ensureValidSession()
            } catch (_: NoUserSessionException) {
            }
        }
        assertEquals(1, refreshes)
    }
}
