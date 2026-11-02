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

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.powersync.UploadErrorAction.DISCARD
import ua.com.radiokot.money.powersync.UploadErrorAction.REFRESH_SESSION_AND_RETRY
import ua.com.radiokot.money.powersync.UploadErrorAction.RETRY
import java.io.IOException
import java.net.SocketTimeoutException

class UploadErrorClassifierTest {

    private fun httpResponse(status: Int): HttpResponse = runBlocking {
        HttpClient(MockEngine { respond(content = "{}", status = HttpStatusCode.fromValue(status)) })
            .use { client -> client.post("https://example.invalid/rest/v1/rpc/atomic_crud") }
    }

    private fun postgrestException(status: Int, code: String?) =
        PostgrestRestException(
            message = "error",
            hint = null,
            details = null,
            code = code,
            response = httpResponse(status),
        )

    private fun classify(t: Throwable) = UploadErrorClassifier.classify(t)

    // --- the decision on the extracted value ---

    @Test
    fun insufficientPrivilegeUnderAnonIsRefreshAndRetry() {
        assertEquals(
            REFRESH_SESSION_AND_RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 401, postgresCode = "42501", isNetwork = false)),
        )
    }

    @Test
    fun insufficientPrivilegeAsAuthenticatedIsDiscard() {
        assertEquals(
            DISCARD,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 403, postgresCode = "42501", isNetwork = false)),
        )
    }

    @Test
    fun uniqueViolationIsDiscard() {
        assertEquals(
            DISCARD,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 409, postgresCode = "23505", isNetwork = false)),
        )
        assertEquals(
            DISCARD,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 400, postgresCode = "23505", isNetwork = false)),
        )
    }

    @Test
    fun invalidTextRepresentationIsDiscard() {
        assertEquals(
            DISCARD,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 400, postgresCode = "22P02", isNetwork = false)),
        )
    }

    @Test
    fun jwtErrorsAreRefreshAndRetry() {
        listOf("PGRST301", "PGRST303", null).forEach { code ->
            assertEquals(
                code.toString(),
                REFRESH_SESSION_AND_RETRY,
                UploadErrorClassifier.classify(UploadFailure(httpStatus = 401, postgresCode = code, isNetwork = false)),
            )
        }
    }

    @Test
    fun dataErrorCodeUnder401IsStillRefreshAndRetry() {
        assertEquals(
            REFRESH_SESSION_AND_RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 401, postgresCode = "23505", isNetwork = false)),
        )
    }

    @Test
    fun serverErrorsAreRetry() {
        assertEquals(
            RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 500, postgresCode = "XX000", isNetwork = false)),
        )
        assertEquals(
            RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 503, postgresCode = "23505", isNetwork = false)),
        )
    }

    @Test
    fun otherClientErrorsAreRetry() {
        assertEquals(
            RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 404, postgresCode = "PGRST202", isNetwork = false)),
        )
        assertEquals(
            RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = 403, postgresCode = "PGRST000", isNetwork = false)),
        )
    }

    @Test
    fun networkAndUnknownAreRetry() {
        assertEquals(
            RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = null, postgresCode = null, isNetwork = true)),
        )
        assertEquals(
            RETRY,
            UploadErrorClassifier.classify(UploadFailure(httpStatus = null, postgresCode = null, isNetwork = false)),
        )
    }

    // --- the adapter from real exceptions ---

    @Test
    fun adapterReadsStatusAndCodeFromPostgrestException() {
        val failure = UploadFailure.of(postgrestException(401, "42501"))
        assertEquals(401, failure.httpStatus)
        assertEquals("42501", failure.postgresCode)
        assertFalse(failure.isNetwork)
    }

    @Test
    fun adapterMarksSupabaseHttpRequestExceptionAsNetwork() {
        val failure = UploadFailure.of(HttpRequestException("Unable to resolve host", HttpRequestBuilder()))
        assertTrue(failure.isNetwork)
        assertNull(failure.httpStatus)
        assertNull(failure.postgresCode)
    }

    @Test
    fun postgrestAnon42501IsRefreshAndRetry() {
        assertEquals(REFRESH_SESSION_AND_RETRY, classify(postgrestException(401, "42501")))
    }

    @Test
    fun postgrestAuthenticated42501IsDiscard() {
        assertEquals(DISCARD, classify(postgrestException(403, "42501")))
    }

    @Test
    fun postgrestUniqueViolationIsDiscard() {
        assertEquals(DISCARD, classify(postgrestException(400, "23505")))
    }

    @Test
    fun postgrestInvalidInputIsDiscard() {
        assertEquals(DISCARD, classify(postgrestException(400, "22P02")))
    }

    @Test
    fun postgrestJwtExpiredIsRefreshAndRetry() {
        assertEquals(REFRESH_SESSION_AND_RETRY, classify(postgrestException(401, "PGRST303")))
    }

    @Test
    fun postgrestServerErrorIsRetry() {
        assertEquals(RETRY, classify(postgrestException(500, "XX000")))
    }

    @Test
    fun unauthorizedRestExceptionIsRefreshAndRetry() {
        assertEquals(
            REFRESH_SESSION_AND_RETRY,
            classify(UnauthorizedRestException("unauthorized", httpResponse(401), null)),
        )
    }

    @Test
    fun supabaseHttpRequestExceptionIsRetry() {
        assertEquals(RETRY, classify(HttpRequestException("timeout", HttpRequestBuilder())))
    }

    @Test
    fun ioExceptionsAndTimeoutsAreRetry() {
        assertEquals(RETRY, classify(IOException("connection reset")))
        assertEquals(RETRY, classify(SocketTimeoutException("read timed out")))
        assertEquals(RETRY, classify(HttpRequestTimeoutException("https://example.invalid", 10_000L)))
    }

    @Test
    fun unknownExceptionsAreRetry() {
        assertEquals(RETRY, classify(IllegalStateException("something odd")))
        assertEquals(RETRY, classify(RuntimeException()))
    }

    @Test
    fun anon42501FollowedByNetworkErrorIsRetriedBothTimes() {
        // Regression: a 42501 used to leave a stale error code behind,
        // and the following network error was then classified as fatal.
        val first = classify(postgrestException(401, "42501"))
        val second = classify(HttpRequestException("Unable to resolve host", HttpRequestBuilder()))
        val third = classify(IOException("Software caused connection abort"))

        assertEquals(REFRESH_SESSION_AND_RETRY, first)
        assertEquals(RETRY, second)
        assertEquals(RETRY, third)
    }

    @Test
    fun discardFollowedByNetworkErrorIsRetry() {
        assertEquals(DISCARD, classify(postgrestException(403, "42501")))
        assertEquals(RETRY, classify(HttpRequestException("Unable to resolve host", HttpRequestBuilder())))
    }
}
