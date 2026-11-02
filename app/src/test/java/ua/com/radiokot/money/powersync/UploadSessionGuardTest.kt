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
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class UploadSessionGuardTest {

    private val now = Instant.parse("2026-11-02T10:00:00Z")

    // --- freshness ---

    @Test
    fun expiredSessionNeedsRefresh() {
        assertTrue(UploadSessionGuard.needsRefresh(expiresAt = now - 1.hours, now = now))
    }

    @Test
    fun sessionExpiringWithinAMinuteNeedsRefresh() {
        assertTrue(UploadSessionGuard.needsRefresh(expiresAt = now + 30.seconds, now = now))
        assertTrue(UploadSessionGuard.needsRefresh(expiresAt = now + 59.seconds, now = now))
    }

    @Test
    fun sessionValidForLongDoesNotNeedRefresh() {
        assertFalse(UploadSessionGuard.needsRefresh(expiresAt = now + 30.minutes, now = now))
        assertFalse(UploadSessionGuard.needsRefresh(expiresAt = now + 2.minutes, now = now))
    }

    // --- the guard ---

    private class FakeAuth(
        var expiresAt: Instant?,
        val refreshedExpiresAt: Instant?,
        val refreshError: Exception? = null,
    ) {
        var refreshCalls = 0

        suspend fun refresh() {
            refreshCalls++
            refreshError?.let { throw it }
            expiresAt = refreshedExpiresAt
        }
    }

    private fun guard(auth: FakeAuth) = UploadSessionGuard(
        currentSessionExpiresAt = { auth.expiresAt },
        refreshSession = auth::refresh,
        now = { now },
    )

    private fun assertNoSession(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("Expected NoUserSessionException")
        } catch (e: NoUserSessionException) {
            // Must be retryable for the uploader: an IOException is never discarded.
            assertTrue(UploadFailure.of(e).isNetwork)
            assertEquals(UploadErrorAction.RETRY, UploadErrorClassifier.classify(e))
        }
    }

    @Test
    fun freshSessionIsUsedWithoutRefresh() = runBlocking {
        val auth = FakeAuth(expiresAt = now + 30.minutes, refreshedExpiresAt = null)
        guard(auth).ensureValidSession()
        assertEquals(0, auth.refreshCalls)
    }

    @Test
    fun expiredSessionIsRefreshedBeforeUpload() = runBlocking {
        val auth = FakeAuth(expiresAt = now - 1.hours, refreshedExpiresAt = now + 1.hours)
        guard(auth).ensureValidSession()
        assertEquals(1, auth.refreshCalls)
    }

    @Test
    fun almostExpiredSessionIsRefreshedBeforeUpload() = runBlocking {
        val auth = FakeAuth(expiresAt = now + 10.seconds, refreshedExpiresAt = now + 1.hours)
        guard(auth).ensureValidSession()
        assertEquals(1, auth.refreshCalls)
    }

    @Test
    fun missingSessionFailsRetryably() {
        val auth = FakeAuth(expiresAt = null, refreshedExpiresAt = now + 1.hours)
        assertNoSession { guard(auth).ensureValidSession() }
    }

    @Test
    fun failedRefreshFailsRetryably() {
        val auth = FakeAuth(
            expiresAt = now - 1.hours,
            refreshedExpiresAt = null,
            refreshError = IllegalStateException("network is down"),
        )
        assertNoSession { guard(auth).ensureValidSession() }
        assertEquals(1, auth.refreshCalls)
    }

    @Test
    fun refreshThatLeavesNoSessionFailsRetryably() {
        val auth = FakeAuth(expiresAt = now - 1.hours, refreshedExpiresAt = null)
        assertNoSession { guard(auth).ensureValidSession() }
    }

    @Test
    fun refreshThatLeavesExpiredSessionFailsRetryably() {
        val auth = FakeAuth(expiresAt = now - 1.hours, refreshedExpiresAt = now - 1.hours)
        assertNoSession { guard(auth).ensureValidSession() }
    }
}
