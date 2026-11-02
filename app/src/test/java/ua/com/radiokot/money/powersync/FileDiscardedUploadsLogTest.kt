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

import com.powersync.db.crud.UpdateType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FileDiscardedUploadsLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun record(transactionId: Int) =
        DiscardedUpload.of(
            transaction = TestTransaction(
                listOf(
                    testCrudEntry(op = UpdateType.PATCH, table = "accounts", id = "a1",
                        opData = mapOf("title" to "Cash"), metadata = "m"),
                    testCrudEntry(op = UpdateType.DELETE, table = "categories", id = "c2", opData = null),
                )
            ).transaction.copy(transactionId = transactionId),
            failure = UploadFailure(httpStatus = 400, postgresCode = "23502", isNetwork = false),
            error = IllegalArgumentException("null value"),
            discardedAt = Instant.parse("2026-11-02T10:00:00Z"),
        )

    @Test
    fun appendsOneJsonLinePerDiscardedTransaction() {
        val file = folder.root.resolve("discarded-uploads.jsonl")
        val log = FileDiscardedUploadsLog(file)

        log.append(record(1))
        log.append(record(2))

        val lines = file.readLines()
        assertEquals(2, lines.size)

        val first = Json.parseToJsonElement(lines[0]).jsonObject
        assertEquals("1", first["transactionId"]!!.jsonPrimitive.content)
        assertEquals("2026-11-02T10:00:00Z", first["discardedAt"]!!.jsonPrimitive.content)
        assertEquals("400", first["httpStatus"]!!.jsonPrimitive.content)
        assertEquals("23502", first["code"]!!.jsonPrimitive.content)

        val entries = first["entries"]!!.jsonArray
        assertEquals(2, entries.size)
        val patch = entries[0].jsonObject
        assertEquals("PATCH", patch["op"]!!.jsonPrimitive.content)
        assertEquals("accounts", patch["table"]!!.jsonPrimitive.content)
        assertEquals("a1", patch["id"]!!.jsonPrimitive.content)
        assertEquals("Cash", patch["opData"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("m", patch["metadata"]!!.jsonPrimitive.content)
        assertEquals("DELETE", entries[1].jsonObject["op"]!!.jsonPrimitive.content)

        assertEquals("2", Json.parseToJsonElement(lines[1]).jsonObject["transactionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun keepsExistingLines() {
        val file = folder.root.resolve("discarded-uploads.jsonl")
        FileDiscardedUploadsLog(file).append(record(1))
        FileDiscardedUploadsLog(file).append(record(2))

        assertEquals(2, file.readLines().size)
    }
}
