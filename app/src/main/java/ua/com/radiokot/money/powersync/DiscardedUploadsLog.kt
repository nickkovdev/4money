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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A CRUD transaction the uploader discarded, with everything needed to restore it by hand.
 */
@Serializable
data class DiscardedUpload(
    val transactionId: Int?,
    val discardedAt: String,
    val httpStatus: Int?,
    val code: String?,
    val error: String?,
    val entries: List<Entry>,
) {
    @Serializable
    data class Entry(
        val op: String,
        val table: String,
        val id: String,
        val opData: Map<String, String?>?,
        val metadata: String?,
    )

    companion object {
        @OptIn(ExperimentalTime::class)
        fun of(
            transaction: CrudTransaction,
            failure: UploadFailure,
            error: Throwable,
            discardedAt: Instant,
        ) = DiscardedUpload(
            transactionId = transaction.transactionId,
            discardedAt = discardedAt.toString(),
            httpStatus = failure.httpStatus,
            code = failure.postgresCode,
            error = error.message?.lineSequence()?.firstOrNull(),
            entries = transaction.crud.map { entry ->
                Entry(
                    op = entry.op.name,
                    table = entry.table,
                    id = entry.id,
                    opData = entry.opData?.toMap(),
                    metadata = entry.metadata,
                )
            },
        )
    }
}

/**
 * A durable record of discarded uploads.
 */
fun interface DiscardedUploadsLog {
    /**
     * @throws Exception if the record could not be written
     */
    fun append(upload: DiscardedUpload)
}

/**
 * Appends each discarded upload as one JSON line to [file].
 * The file is never rotated; in the app it lives in the app-private `filesDir`,
 * which survives app updates.
 */
class FileDiscardedUploadsLog(
    private val file: File,
) : DiscardedUploadsLog {

    override fun append(upload: DiscardedUpload) {
        val line = json.encodeToString(DiscardedUpload.serializer(), upload) + "\n"
        synchronized(LOCK) {
            file.parentFile?.mkdirs()
            file.appendText(line)
        }
    }

    private companion object {
        val LOCK = Any()
        val json = Json {
            encodeDefaults = true
        }
    }
}
