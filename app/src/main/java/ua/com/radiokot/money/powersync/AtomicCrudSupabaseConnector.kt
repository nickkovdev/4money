/* Copyright 2025 Oleg Koretsky
   Copyright 2026 Nikita Kovalovs

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

import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.crud.UpdateType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ua.com.radiokot.money.lazyLogger

/**
 * A Supabase connector applying CRUD transactions atomically,
 * using [`atomic_crud`](https://gist.github.com/Radiokot/fbc8d1a7cf283d1f476938ca573ced82)
 * function which must be manually added to the DB functions.
 *
 * ⚠️ Empty updates (update with no data) are not supported,
 * ensure that all the tables in the schema have `ignoreEmptyUpdates = true` param.
 *
 * Uploading and the failure handling are done by [CrudTransactionUploader]:
 * a transaction is only discarded for Postgres class 22/23 errors (see [UploadErrorClassifier]),
 * and then it is written to [discardedUploadsLog] first. Auth, network, permission
 * and server errors keep it in the queue.
 */
@OptIn(SupabaseInternal::class)
class AtomicCrudSupabaseConnector(
    private val supabaseClient: SupabaseClient,
    private val powerSyncEndpoint: String,
    private val discardedUploadsLog: DiscardedUploadsLog,
) : PowerSyncBackendConnector() {

    private val log by lazyLogger("AtomicCrud")

    // At most one refresh per 30 s from here, otherwise supabase-kt auto-refresh does the job.
    private val refreshThrottle = RefreshThrottle()

    private val uploader = CrudTransactionUploader(
        ensureValidSession = UploadSessionGuard(
            currentSessionExpiresAt = { supabaseClient.auth.currentSessionOrNull()?.expiresAt },
            refreshSession = {
                if (!refreshThrottle.tryRefresh { supabaseClient.auth.refreshCurrentSession() }) {
                    throw NoUserSessionException("Session refresh throttled")
                }
            },
        )::ensureValidSession,
        send = { transaction ->
            if (!tryToUploadSpecialTransaction(transaction)) {
                supabaseClient.postgrest.rpc(
                    function = "atomic_crud",
                    parameters = AtomicCrudInput(transaction),
                )
            }
        },
        refreshSession = ::refreshSessionQuietly,
        discardedUploadsLog = discardedUploadsLog,
    )

    @Serializable
    @Suppress("unused")
    private class AtomicCrudInput(
        val operations: List<Operation>,
    ) {
        constructor(transaction: CrudTransaction) : this(
            operations = transaction.crud.map(::Operation)
        )

        @Serializable
        @Suppress("unused")
        class Operation(
            val t: String,
            val id: String,
            val o: String,
            val d: List<List<String?>>?,
        ) {
            constructor(crudEntry: CrudEntry) : this(
                t = crudEntry.table,
                id = crudEntry.id,
                o = when (crudEntry.op) {
                    UpdateType.PUT -> "I"
                    UpdateType.PATCH -> "U"
                    UpdateType.DELETE -> "D"
                },
                d = crudEntry.opData?.map { (columnName, columnValue) ->
                    listOf(columnName, columnValue)
                },
            )
        }
    }

    @Serializable
    @Suppress("unused")
    internal class TransferInput(
        @SerialName("id")
        val id: String,
        @SerialName("source_id")
        val sourceId: String,
        @SerialName("source_amount")
        val sourceAmount: String,
        @SerialName("destination_id")
        val destinationId: String,
        @SerialName("destination_amount")
        val destinationAmount: String,
        @SerialName("memo")
        val memo: String?,
        @SerialName("time")
        val time: String,
    ) {
        constructor(crudEntry: CrudEntry) : this(
            id = crudEntry.id,
            sourceId = crudEntry.requireValue("source_id"),
            sourceAmount = crudEntry.requireValue("source_amount"),
            destinationId = crudEntry.requireValue("destination_id"),
            destinationAmount = crudEntry.requireValue("destination_amount"),
            memo = crudEntry.opData?.get("memo"),
            time = crudEntry.requireValue("time"),
        )

        private companion object {
            fun CrudEntry.requireValue(column: String): String =
                opData?.get(column)
                    ?: throw MalformedCrudEntryException(
                        "$table entry $id has no $column"
                    )
        }
    }

    init {
        require(supabaseClient.pluginManager.getPluginOrNull(Auth) != null) {
            "The Auth plugin must be installed on the Supabase client"
        }
        require(supabaseClient.pluginManager.getPluginOrNull(Postgrest) != null) {
            "The Postgrest plugin must be installed on the Supabase client"
        }
    }

    override suspend fun fetchCredentials(
    ): PowerSyncCredentials = runWrapped {

        check(supabaseClient.auth.sessionStatus.value is SessionStatus.Authenticated) {
            "Supabase client is not authenticated"
        }

        // Use Supabase token for PowerSync
        val session = supabaseClient.auth.currentSessionOrNull()
            ?: error("Could not fetch Supabase credentials")

        check(session.user != null) {
            "No user data"
        }

        // userId is for debugging purposes only
        PowerSyncCredentials(
            endpoint = powerSyncEndpoint,
            token = session.accessToken, // Use the access token to authenticate against PowerSync
            userId = session.user!!.id,
        )
    }

    override suspend fun uploadData(
        database: PowerSyncDatabase,
    ) = runWrapped {

        val transaction = database.getNextCrudTransaction()
            ?: return@runWrapped

        uploader.upload(transaction)
    }

    private suspend fun refreshSessionQuietly() {
        try {
            val refreshed = refreshThrottle.tryRefresh {
                supabaseClient.auth.refreshCurrentSession()
            }
            if (!refreshed) {
                log.debug {
                    "refreshSessionQuietly(): refreshed less than 30 s ago, skipping"
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }

            log.warn(e) {
                "refreshSessionQuietly(): failed refreshing the session, will retry later"
            }
        }
    }

    /**
     * @return **true** if the transaction was uploaded in a special way,
     * **false** if it is not special and must be uploaded with the default strategy.
     */
    private suspend fun tryToUploadSpecialTransaction(
        transaction: CrudTransaction,
    ): Boolean {

        val transfersCrudEntry: CrudEntry? = transaction.crud
            .find { it.table == "transfers" }

        // If the default strategy is used to sync multiple transfers from/to the same account
        // done offline, then the last synced transfer overwrites other's balance changes.
        // To prevent this, account balance changes must occur on the server at the moment of sync,
        // which is done inside the following RPC functions.
        when {
            transfersCrudEntry != null
                    && transfersCrudEntry.op == UpdateType.PUT
                    && transfersCrudEntry.metadata == SPECIAL_TRANSACTION_TRANSFER
                -> {
                supabaseClient.postgrest.rpc(
                    function = "transfer",
                    parameters = TransferInput(transfersCrudEntry),
                )
                return true
            }

            transfersCrudEntry != null
                    && transfersCrudEntry.op == UpdateType.PUT
                    && transfersCrudEntry.metadata == SPECIAL_TRANSACTION_TRANSFER_EDIT
                -> {
                supabaseClient.postgrest.rpc(
                    function = "transfer_edit",
                    parameters = TransferInput(transfersCrudEntry),
                )
                return true
            }

            transfersCrudEntry != null
                    && transfersCrudEntry.op == UpdateType.DELETE
                -> {
                supabaseClient.postgrest.rpc(
                    function = "transfer_revert",
                    parameters = JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(transfersCrudEntry.id),
                        )
                    ),
                )
                return true
            }
        }

        return false
    }

    private inline fun <R> runWrapped(block: () -> R): R =
        try {
            block()
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }

            if (t is PowerSyncException) {
                log.error(t) { "PowerSync exception" }
                throw t
            } else {
                log.error(t) { "other exception" }
                throw PowerSyncException(t.message ?: "Unknown internal exception", t)
            }
        }

    companion object {
        const val SPECIAL_TRANSACTION_TRANSFER = "transfer"
        const val SPECIAL_TRANSACTION_TRANSFER_EDIT = "transfer_edit"
    }
}
