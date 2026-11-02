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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Lets a session refresh run at most once per [minInterval].
 * Between the attempts the uploader relies on supabase-kt auto-refresh (`alwaysAutoRefresh`).
 *
 * @param elapsed monotonic time, only differences matter
 */
class RefreshThrottle(
    private val minInterval: Duration = 30.seconds,
    private val elapsed: () -> Duration = monotonicElapsed(),
) {
    private var lastAttemptAt: Duration? = null

    /**
     * Runs [refresh] unless an attempt was started less than [minInterval] ago.
     * A failed attempt counts too.
     *
     * @return true if [refresh] ran, false if it was skipped
     */
    suspend fun tryRefresh(refresh: suspend () -> Unit): Boolean {
        val now = elapsed()
        synchronized(this) {
            val last = lastAttemptAt
            if (last != null && now - last < minInterval) {
                return false
            }
            lastAttemptAt = now
        }
        refresh()
        return true
    }

    private companion object {
        fun monotonicElapsed(): () -> Duration {
            val start = TimeSource.Monotonic.markNow()
            return start::elapsedNow
        }
    }
}
