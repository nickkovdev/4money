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

import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * There is no valid user session to upload with.
 * An [IOException] so the uploader always retries it and never discards a transaction.
 */
class NoUserSessionException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Makes sure an upload goes out with a valid user access token.
 *
 * Without a user session supabase-kt sends the anon key as the bearer token,
 * and PostgREST rejects the RPC calls with 42501 as `anon`.
 *
 * @param currentSessionExpiresAt expiration of the current user session, null if there is none
 * @param refreshSession refreshes the current user session, throws on failure
 */
@OptIn(ExperimentalTime::class)
class UploadSessionGuard(
    private val currentSessionExpiresAt: () -> Instant?,
    private val refreshSession: suspend () -> Unit,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * Refreshes the session if it expires within [MIN_VALIDITY].
     *
     * @throws NoUserSessionException if there is no valid user session after that
     */
    suspend fun ensureValidSession() {
        val expiresAt = currentSessionExpiresAt()
            ?: throw NoUserSessionException("No user session")

        if (!needsRefresh(expiresAt, now())) {
            return
        }

        try {
            refreshSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NoUserSessionException("Session refresh failed", e)
        }

        val refreshedExpiresAt = currentSessionExpiresAt()
            ?: throw NoUserSessionException("No user session after refresh")

        if (needsRefresh(refreshedExpiresAt, now())) {
            throw NoUserSessionException("Session still expires at $refreshedExpiresAt after refresh")
        }
    }

    companion object {
        val MIN_VALIDITY: Duration = 60.seconds

        fun needsRefresh(
            expiresAt: Instant,
            now: Instant,
        ): Boolean =
            expiresAt - now < MIN_VALIDITY
    }
}
