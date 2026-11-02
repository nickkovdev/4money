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

import com.powersync.db.crud.CrudTransaction
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CrudTransactionUploaderTest {

    private class FakeDiscardLog(
        val failure: Exception? = null,
    ) : DiscardedUploadsLog {
        val records = mutableListOf<DiscardedUpload>()

        override fun append(upload: DiscardedUpload) {
            failure?.let { throw it }
            records += upload
        }
    }

    private val testTransaction = TestTransaction()
    private var sessionError: Exception? = null
    private var sendError: Exception? = null
    private var sendCalls = 0
    private var refreshCalls = 0
    private var discardLog = FakeDiscardLog()

    private fun uploader() = CrudTransactionUploader(
        ensureValidSession = { sessionError?.let { throw it } },
        send = { _: CrudTransaction ->
            sendCalls++
            sendError?.let { throw it }
        },
        refreshSession = { refreshCalls++ },
        discardedUploadsLog = discardLog,
        now = { Instant.parse("2026-11-02T10:00:00Z") },
    )

    /**
     * @return the exception the upload ended with, null if it finished normally
     */
    private fun upload(): Throwable? =
        try {
            runBlocking { uploader().upload(testTransaction.transaction) }
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun successCompletesTheTransaction() {
        assertEquals(null, upload())
        assertEquals(1, testTransaction.completeCalls)
        assertEquals(0, discardLog.records.size)
    }

    @Test
    fun unauthorizedIsNotCompletedAndRefreshesSession() {
        sendError = postgrestException(401, "42501")
        assertSame(sendError, upload())
        assertEquals(0, testTransaction.completeCalls)
        assertEquals(1, refreshCalls)
        assertEquals(0, discardLog.records.size)
    }

    @Test
    fun networkErrorIsNotCompleted() {
        sendError = HttpRequestException("Unable to resolve host", HttpRequestBuilder())
        assertSame(sendError, upload())
        assertEquals(0, testTransaction.completeCalls)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun missingSessionIsNotCompletedAndNothingIsSent() {
        sessionError = NoUserSessionException("No user session")
        assertSame(sessionError, upload())
        assertEquals(0, sendCalls)
        assertEquals(0, testTransaction.completeCalls)
    }

    @Test
    fun insufficientPrivilegeIsNotCompleted() {
        sendError = postgrestException(403, "42501")
        assertSame(sendError, upload())
        assertEquals(0, testTransaction.completeCalls)
        assertEquals(0, discardLog.records.size)
    }

    @Test
    fun malformedEntryIsNotCompleted() {
        sendError = MalformedCrudEntryException("transfers entry has no source_id")
        assertSame(sendError, upload())
        assertEquals(0, testTransaction.completeCalls)
    }

    @Test
    fun rejectedDataIsWrittenToTheDiscardLogAndCompleted() {
        sendError = postgrestException(400, "23505")

        assertEquals(null, upload())

        assertEquals(1, testTransaction.completeCalls)
        val record = discardLog.records.single()
        assertEquals(7, record.transactionId)
        assertEquals(400, record.httpStatus)
        assertEquals("23505", record.code)
        assertEquals("2026-11-02T10:00:00Z", record.discardedAt)
        val entry = record.entries.single()
        assertEquals("PUT", entry.op)
        assertEquals("categories", entry.table)
        assertEquals("c1", entry.id)
        assertEquals(mapOf("title" to "Food", "parent" to null), entry.opData)
    }

    @Test
    fun failingDiscardLogDoesNotCrashTheUpload() {
        sendError = postgrestException(400, "22P02")
        discardLog = FakeDiscardLog(failure = IOException("disk full"))

        assertEquals(null, upload())
        assertEquals(1, testTransaction.completeCalls)
    }

    @Test
    fun retryAfterRejectedDataLeavesNoStaleState() {
        val uploader = uploader()

        sendError = postgrestException(400, "23505")
        runBlocking { uploader.upload(testTransaction.transaction) }
        assertEquals(1, testTransaction.completeCalls)

        sendError = HttpRequestException("Unable to resolve host", HttpRequestBuilder())
        try {
            runBlocking { uploader.upload(testTransaction.transaction) }
            fail("Expected the network error to be rethrown")
        } catch (e: HttpRequestException) {
            assertTrue(e === sendError)
        }
        assertEquals(1, testTransaction.completeCalls)
    }
}
