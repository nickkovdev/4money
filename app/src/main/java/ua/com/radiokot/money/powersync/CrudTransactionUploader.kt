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
import kotlinx.coroutines.CancellationException
import ua.com.radiokot.money.lazyLogger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A CRUD entry lacks data its upload needs. Retried (kept in the queue), never discarded.
 */
class MalformedCrudEntryException(message: String) : IllegalArgumentException(message)

/**
 * Uploads one CRUD transaction and decides its fate on failure.
 *
 * The transaction is completed (removed from the queue) only when it was uploaded,
 * or when [UploadErrorClassifier] returns [UploadErrorAction.DISCARD]; in the latter case
 * it is written to [discardedUploadsLog] and logged at ERROR first.
 * In every other case the exception is rethrown and PowerSync retries the same transaction later.
 *
 * @param ensureValidSession throws [NoUserSessionException] if there is no valid user session
 * @param send uploads the transaction
 * @param refreshSession refreshes the session after a 401, may be throttled, must not throw
 */
@OptIn(ExperimentalTime::class)
class CrudTransactionUploader(
    private val ensureValidSession: suspend () -> Unit,
    private val send: suspend (CrudTransaction) -> Unit,
    private val refreshSession: suspend () -> Unit,
    private val discardedUploadsLog: DiscardedUploadsLog,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val log by lazyLogger("CrudUploader")

    suspend fun upload(transaction: CrudTransaction) {
        // Without a user session supabase-kt sends the anon key as the bearer token.
        // Never upload like that: the exception keeps the transaction in the queue.
        try {
            ensureValidSession()
        } catch (e: NoUserSessionException) {
            log.warn(e) {
                "upload(): no valid user session, retrying later:" +
                        "\ntransactionId=${transaction.transactionId}"
            }
            throw e
        }

        try {
            send(transaction)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }

            handleFailure(transaction, e)
            return
        }

        transaction.complete(null)
    }

    private suspend fun handleFailure(
        transaction: CrudTransaction,
        e: Exception,
    ) {
        // Classified from this exception only, never from shared state.
        val failure = UploadFailure.of(e)

        when (UploadErrorClassifier.classify(failure)) {
            UploadErrorAction.DISCARD -> {
                log.error(e) {
                    "upload(): server rejected the data, discarding transaction:" +
                            "\nhttpStatus=${failure.httpStatus}" +
                            "\ncode=${failure.postgresCode}" +
                            "\ntransaction=$transaction" +
                            "\n"
                }

                try {
                    discardedUploadsLog.append(
                        DiscardedUpload.of(
                            transaction = transaction,
                            failure = failure,
                            error = e,
                            discardedAt = now(),
                        )
                    )
                } catch (writeError: Exception) {
                    log.error(writeError) {
                        "upload(): failed writing the discarded transaction to the file, " +
                                "it is only in the log line above:" +
                                "\ntransactionId=${transaction.transactionId}"
                    }
                }

                transaction.complete(null)
            }

            UploadErrorAction.REFRESH_SESSION_AND_RETRY -> {
                log.warn(e) {
                    "upload(): unauthorized, refreshing session and retrying transaction:" +
                            "\nhttpStatus=${failure.httpStatus}" +
                            "\ncode=${failure.postgresCode}" +
                            "\ntransaction=$transaction" +
                            "\n"
                }

                refreshSession()

                throw e
            }

            UploadErrorAction.RETRY -> {
                if (e is MalformedCrudEntryException) {
                    log.error(e) {
                        "upload(): malformed transfer entry, keeping in queue:" +
                                "\ntransaction=$transaction" +
                                "\n"
                    }
                } else {
                    log.warn(e) {
                        "upload(): error on upload, retrying transaction:" +
                                "\nhttpStatus=${failure.httpStatus}" +
                                "\ncode=${failure.postgresCode}" +
                                "\nisNetwork=${failure.isNetwork}" +
                                "\ntransaction=$transaction" +
                                "\n"
                    }
                }

                throw e
            }
        }
    }
}
