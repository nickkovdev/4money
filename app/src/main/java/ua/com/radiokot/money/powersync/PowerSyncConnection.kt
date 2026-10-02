/* Copyright 2025 Oleg Koretsky

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

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.sync.SyncOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.radiokot.money.BuildConfig
import ua.com.radiokot.money.lazyLogger
import java.io.Closeable
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Owns the PowerSync stream lifecycle, so it is open only while
 * at least one [Holder] needs it: the app is visible or the background worker runs.
 *
 * Requests are converged by a single coroutine: only the latest demand matters,
 * and a connect or disconnect that has started always completes,
 * so [isConnected] reflects the real stream state.
 */
@OptIn(ExperimentalTime::class)
class PowerSyncConnection(
    private val control: Control,
    private val idleDisconnectTimeout: Duration = 30.seconds,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
    private val clock: Clock = Clock.System,
) : Closeable {

    enum class Holder {
        VISIBLE_APP,
        BACKGROUND_WORKER,
    }

    /**
     * The stream operations, abstracted from [PowerSyncDatabase] for testing.
     */
    interface Control {
        suspend fun connect()
        suspend fun disconnect()

        /**
         * Suspends until no local changes are waiting for upload.
         */
        suspend fun awaitUploadsIdle()

        /**
         * Suspends until a full sync completed at or after [since]
         * and the local upload queue is empty.
         */
        suspend fun awaitSyncedAndUploaded(since: Instant)
    }

    private val log by lazyLogger("PowerSyncConnection")
    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val holders = MutableStateFlow(emptySet<Holder>())
    private val convergeJob: Job

    @Volatile
    private var isClosed = false

    private val connectedAtFlow = MutableStateFlow<Instant?>(null)

    /**
     * When the stream was last opened, null if it is closed.
     */
    val connectedAt: StateFlow<Instant?> = connectedAtFlow.asStateFlow()

    val isConnected: Boolean
        get() = connectedAtFlow.value != null

    init {
        convergeJob = coroutineScope.launch {
            holders
                .map(Set<Holder>::isNotEmpty)
                .distinctUntilChanged()
                .collectLatest { isNeeded ->
                    if (isNeeded) {
                        withContext(NonCancellable) {
                            connectIfNeeded()
                        }
                    } else if (isConnected) {
                        // Cancelled if the stream is needed again meanwhile.
                        withTimeoutOrNull(idleDisconnectTimeout) {
                            control.awaitUploadsIdle()
                        }
                        withContext(NonCancellable) {
                            disconnectIfNeeded()
                        }
                    }
                }
        }
    }

    /**
     * Opens the stream, if not yet, and keeps it open until
     * the [holder] calls [disconnectWhenIdle].
     */
    fun connect(holder: Holder) {
        if (isClosed) {
            log.debug {
                "connect(): ignoring, closed:" +
                        "\nholder=$holder"
            }
            return
        }

        log.debug {
            "connect(): requested:" +
                    "\nholder=$holder"
        }

        holders.update { it + holder }
    }

    /**
     * Releases the [holder]. If no other holder needs the stream,
     * disconnects once pending local changes are uploaded,
     * or after the idle disconnect timeout.
     */
    fun disconnectWhenIdle(holder: Holder) {
        log.debug {
            "disconnectWhenIdle(): requested:" +
                    "\nholder=$holder"
        }

        holders.update { it - holder }
    }

    /**
     * Suspends until the stream is open, a full sync completed
     * at or after [since] or since the stream was opened, whichever is earlier,
     * and the local upload queue is empty.
     *
     * A stream that was already open and synced returns at once,
     * as an idle stream gets no new checkpoints.
     * A freshly opened stream waits for its first sync.
     */
    suspend fun awaitSyncedAndUploaded(since: Instant) {
        val connectedAt = connectedAtFlow.filterNotNull().first()
        // lastSyncedAt may be truncated to seconds.
        val effectiveSince = minOf(since, connectedAt - SYNC_TIME_MARGIN)

        log.debug {
            "awaitSyncedAndUploaded(): waiting:" +
                    "\nsince=$since," +
                    "\nconnectedAt=$connectedAt," +
                    "\neffectiveSince=$effectiveSince"
        }

        control.awaitSyncedAndUploaded(effectiveSince)
    }

    /**
     * Stops serving requests and disconnects the stream if it is open.
     */
    override fun close() {
        isClosed = true
        holders.value = emptySet()
        coroutineScope.cancel()

        // Detached, as the scope is cancelled;
        // waits for an in-flight connect or disconnect to complete.
        CoroutineScope(dispatcher).launch {
            convergeJob.join()
            disconnectIfNeeded()
        }
    }

    private companion object {
        val SYNC_TIME_MARGIN = 2.seconds
    }

    private suspend fun connectIfNeeded() {
        if (isConnected) {
            return
        }

        log.debug {
            "connectIfNeeded(): connecting"
        }

        try {
            control.connect()
            connectedAtFlow.value = clock.now()
        } catch (e: Exception) {
            log.error(e) {
                "connectIfNeeded(): failed"
            }
        }
    }

    private suspend fun disconnectIfNeeded() {
        if (!isConnected) {
            return
        }

        log.debug {
            "disconnectIfNeeded(): disconnecting"
        }

        // Cleared first: even after a failed disconnect,
        // the next connect re-creates the stream.
        connectedAtFlow.value = null
        try {
            control.disconnect()
        } catch (e: Exception) {
            log.error(e) {
                "disconnectIfNeeded(): failed"
            }
        }
    }
}

/**
 * [PowerSyncConnection.Control] of a real [PowerSyncDatabase].
 */
@OptIn(ExperimentalPowerSyncAPI::class, ExperimentalTime::class)
class PowerSyncDatabaseControl(
    private val database: PowerSyncDatabase,
    private val connector: PowerSyncBackendConnector,
) : PowerSyncConnection.Control {

    override suspend fun connect() =
        database.connect(
            connector = connector,
            options = SyncOptions(
                userAgent = "4Money/${BuildConfig.VERSION_NAME}",
            ),
            appMetadata = mapOf(
                "v" to BuildConfig.VERSION_NAME,
                "debug" to BuildConfig.DEBUG.toString(),
            ),
        )

    override suspend fun disconnect() =
        database.disconnect()

    override suspend fun awaitUploadsIdle() {
        database.currentStatus.asFlow().first { status ->
            !status.uploading && database.getNextCrudTransaction() == null
        }
    }

    override suspend fun awaitSyncedAndUploaded(since: Instant) {
        database.currentStatus.asFlow().first { status ->
            val lastSyncedAt = status.lastSyncedAt
                ?: return@first false

            status.connected
                    && !status.uploading
                    && lastSyncedAt >= since
                    && database.getNextCrudTransaction() == null
        }
    }
}
