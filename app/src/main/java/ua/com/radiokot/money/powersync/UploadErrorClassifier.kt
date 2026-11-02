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

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException

/**
 * What to do with a CRUD transaction whose upload failed.
 */
enum class UploadErrorAction {
    /**
     * Keep the transaction in the queue, PowerSync retries it later.
     */
    RETRY,

    /**
     * The request was not made as the signed-in user (expired or missing token).
     * Refresh the session and keep the transaction in the queue.
     */
    REFRESH_SESSION_AND_RETRY,

    /**
     * The server could not even convert the data of an authenticated request (Postgres class 22/23).
     * Retrying would block the queue forever. The transaction is written to [DiscardedUploadsLog] first.
     */
    DISCARD,
}

/**
 * What is known about a failed upload request,
 * extracted from the thrown exception by [of].
 *
 * @param httpStatus status of the response, null if there was no response
 * @param postgresCode `code` from the PostgREST error body (a Postgres SQLSTATE or PGRSTxxx)
 * @param isNetwork whether the request failed without a response (I/O error, timeout)
 */
data class UploadFailure(
    val httpStatus: Int?,
    val postgresCode: String?,
    val isNetwork: Boolean,
) {
    companion object {
        fun of(throwable: Throwable): UploadFailure = when (throwable) {
            is PostgrestRestException ->
                UploadFailure(
                    httpStatus = throwable.statusCode,
                    postgresCode = throwable.code,
                    isNetwork = false,
                )

            is RestException ->
                UploadFailure(
                    httpStatus = throwable.statusCode,
                    postgresCode = null,
                    isNetwork = false,
                )

            // Includes supabase-kt HttpRequestException and Ktor timeouts.
            is IOException ->
                UploadFailure(
                    httpStatus = null,
                    postgresCode = null,
                    isNetwork = true,
                )

            else ->
                UploadFailure(
                    httpStatus = null,
                    postgresCode = null,
                    isNetwork = false,
                )
        }
    }
}

/**
 * Decides whether a failed upload may be discarded.
 * Stateless: the decision depends only on the given failure.
 *
 * The only discard: Postgres class 22 (data exception) or 23 (integrity constraint violation)
 * with a 4xx status other than 401. Errors raised inside the server functions are dead-lettered
 * by the server itself (`money.sync_errors`) and acknowledged, so a 22/23 reaching the client
 * comes from converting the arguments before the function body runs: retrying can never succeed.
 *
 * Everything else is retried: 401 of any kind (after a session refresh),
 * 42501 with any status (a missing grant is a server configuration problem, discarding would wipe the queue),
 * network errors, timeouts, 5xx and unknown failures.
 */
object UploadErrorClassifier {
    private val DATA_ERROR_CODE = "^2[23][0-9A-Z]{3}$".toRegex()

    fun classify(failure: UploadFailure): UploadErrorAction {
        val status = failure.httpStatus
        val code = failure.postgresCode

        return when {
            status == 401 ->
                UploadErrorAction.REFRESH_SESSION_AND_RETRY

            failure.isNetwork || status == null || status !in 400..499 || code == null ->
                UploadErrorAction.RETRY

            DATA_ERROR_CODE.matches(code) ->
                UploadErrorAction.DISCARD

            else ->
                UploadErrorAction.RETRY
        }
    }

    fun classify(throwable: Throwable): UploadErrorAction =
        classify(UploadFailure.of(throwable))
}
