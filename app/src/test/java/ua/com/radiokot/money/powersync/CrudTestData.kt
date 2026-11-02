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

import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.crud.CrudRow
import com.powersync.db.crud.UpdateType
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds an entry the way PowerSync does, from a `ps_crud` row.
 */
internal fun testCrudEntry(
    table: String = "categories",
    id: String = "c1",
    op: UpdateType = UpdateType.PUT,
    opData: Map<String, String?>? = mapOf("title" to "Food", "parent" to null),
    metadata: String? = null,
): CrudEntry {
    val data = buildJsonObject {
        put("op", op.name)
        put("id", id)
        put("type", table)
        if (opData != null) {
            put("data", JsonObject(opData.mapValues { JsonPrimitive(it.value) }))
        }
        if (metadata != null) {
            put("metadata", metadata)
        }
    }
    return CrudEntry.fromRow(CrudRow(id = "1", data = data.toString(), txId = 7))
}

internal fun postgrestException(status: Int, code: String?) =
    PostgrestRestException(
        message = "error",
        hint = null,
        details = null,
        code = code,
        response = runBlocking {
            HttpClient(MockEngine { respond(content = "{}", status = HttpStatusCode.fromValue(status)) })
                .use { client -> client.post("https://example.invalid/rest/v1/rpc/atomic_crud") }
        },
    )

/**
 * A transaction recording whether it was completed.
 */
internal class TestTransaction(
    entries: List<CrudEntry> = listOf(testCrudEntry()),
) {
    var completeCalls = 0
        private set

    val transaction = CrudTransaction(
        transactionId = 7,
        crud = entries,
        complete = { completeCalls++ },
    )
}
