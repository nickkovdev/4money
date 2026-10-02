package ua.com.radiokot.money.powersync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.powersync.PowerSyncConnection.Holder
import java.util.Collections
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PowerSyncConnectionTest {

    private class FakeControl : PowerSyncConnection.Control {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var isOpen = false

        @Volatile
        var uploadsIdle = CompletableDeferred(Unit)

        @Volatile
        var disconnectGate: CompletableDeferred<Unit>? = null

        @Volatile
        var failNextConnect = false

        val lastSyncedAt = MutableStateFlow<Instant?>(null)

        override suspend fun connect() {
            calls += "connect"
            if (failNextConnect) {
                failNextConnect = false
                error("Connect failed")
            }
            isOpen = true
        }

        override suspend fun disconnect() {
            calls += "disconnect"
            disconnectGate?.await()
            isOpen = false
        }

        @Volatile
        var cancelledUploadsIdleWaits = 0

        override suspend fun awaitUploadsIdle() {
            try {
                uploadsIdle.await()
            } catch (e: CancellationException) {
                cancelledUploadsIdleWaits++
                throw e
            }
        }

        override suspend fun awaitSyncedAndUploaded(since: Instant) {
            lastSyncedAt.first { it != null && it >= since }
        }
    }

    private class FakeClock(
        @Volatile
        var now: Instant,
    ) : Clock {
        override fun now(): Instant = now
    }

    private val t0 = Instant.parse("2026-10-02T10:00:00Z")

    private fun connection(
        control: FakeControl,
        idleDisconnectTimeout: Duration = 30.seconds,
        clock: Clock = Clock.System,
    ) = PowerSyncConnection(
        control = control,
        idleDisconnectTimeout = idleDisconnectTimeout,
        clock = clock,
    )

    private suspend fun eventually(condition: () -> Boolean) =
        withTimeout(2.seconds) {
            while (!condition()) {
                delay(5)
            }
        }

    /**
     * Gives the connection time to (wrongly) act, for negative checks.
     */
    private suspend fun settle() =
        delay(100)

    @Test
    fun connectsWhenVisibleAndDisconnectsAfterGoingToBackground() = runBlocking {
        val control = FakeControl()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen && connection.isConnected }

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        eventually { !control.isOpen && !connection.isConnected }

        assertEquals(listOf("connect", "disconnect"), control.calls.toList())
        connection.close()
    }

    @Test
    fun repeatedConnectOpensTheStreamOnce() = runBlocking {
        val control = FakeControl()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        connection.connect(Holder.VISIBLE_APP)
        connection.connect(Holder.BACKGROUND_WORKER)
        eventually { control.isOpen }
        settle()

        assertEquals(listOf("connect"), control.calls.toList())
        connection.close()
    }

    @Test
    fun workerFinishingWhileAppIsVisibleKeepsTheStream() = runBlocking {
        val control = FakeControl()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        connection.connect(Holder.BACKGROUND_WORKER)
        eventually { control.isOpen }

        connection.disconnectWhenIdle(Holder.BACKGROUND_WORKER)
        settle()

        assertTrue(control.isOpen)
        assertEquals(listOf("connect"), control.calls.toList())
        connection.close()
    }

    @Test
    fun appGoingToBackgroundWhileWorkerRunsKeepsTheStreamUntilWorkerDone() = runBlocking {
        val control = FakeControl()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        connection.connect(Holder.BACKGROUND_WORKER)
        eventually { control.isOpen }

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        settle()
        assertTrue(control.isOpen)

        connection.disconnectWhenIdle(Holder.BACKGROUND_WORKER)
        eventually { !control.isOpen }
        connection.close()
    }

    @Test
    fun waitsForUploadsBeforeDisconnecting() = runBlocking {
        val control = FakeControl()
        control.uploadsIdle = CompletableDeferred()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen }

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        settle()
        assertTrue(control.isOpen)

        control.uploadsIdle.complete(Unit)
        eventually { !control.isOpen }
        connection.close()
    }

    @Test
    fun disconnectsAfterTimeoutIfUploadsNeverGetIdle() = runBlocking {
        val control = FakeControl()
        control.uploadsIdle = CompletableDeferred()
        val connection = connection(control, idleDisconnectTimeout = 50.milliseconds)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen }

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        eventually { !control.isOpen }
        connection.close()
    }

    @Test
    fun reopeningWhileWaitingForUploadsDoesNotDisconnect() = runBlocking {
        val control = FakeControl()
        control.uploadsIdle = CompletableDeferred()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen }

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        settle()
        connection.connect(Holder.VISIBLE_APP)
        // Completing uploads before the reconnect is observed
        // would race the converge coroutine into a disconnect.
        eventually { control.cancelledUploadsIdleWaits == 1 }
        control.uploadsIdle.complete(Unit)
        settle()

        assertTrue(control.isOpen)
        assertEquals(listOf("connect"), control.calls.toList())
        connection.close()
    }

    @Test
    fun reopeningDuringDisconnectEndsConnected() = runBlocking {
        val control = FakeControl()
        val disconnectGate = CompletableDeferred<Unit>()
        control.disconnectGate = disconnectGate
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen }

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        eventually { control.calls.size == 2 }
        // The disconnect is in flight now.
        connection.connect(Holder.VISIBLE_APP)
        settle()
        disconnectGate.complete(Unit)

        eventually { control.calls.size == 3 }
        settle()
        assertTrue(control.isOpen)
        assertTrue(connection.isConnected)
        assertEquals(listOf("connect", "disconnect", "connect"), control.calls.toList())
        connection.close()
    }

    @Test
    fun failedConnectIsRetriedOnNextDemand() = runBlocking {
        val control = FakeControl()
        control.failNextConnect = true
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.calls.size == 1 }
        assertFalse(connection.isConnected)

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        settle()
        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen && connection.isConnected }
        connection.close()
    }

    @Test
    fun closeDisconnectsAndIgnoresFurtherRequests() = runBlocking {
        val control = FakeControl()
        val connection = connection(control)

        connection.connect(Holder.VISIBLE_APP)
        eventually { control.isOpen }

        connection.close()
        eventually { !control.isOpen }

        connection.connect(Holder.VISIBLE_APP)
        settle()
        assertFalse(control.isOpen)
        assertEquals(listOf("connect", "disconnect"), control.calls.toList())
    }

    @Test
    fun connectedAtIsSetOnConnectAndClearedOnDisconnect() = runBlocking {
        val control = FakeControl()
        val clock = FakeClock(t0)
        val connection = connection(control, clock = clock)

        connection.connect(Holder.VISIBLE_APP)
        eventually { connection.isConnected }
        assertEquals(t0, connection.connectedAt.value)

        connection.disconnectWhenIdle(Holder.VISIBLE_APP)
        eventually { !connection.isConnected }
        assertNull(connection.connectedAt.value)
        connection.close()
    }

    @Test
    fun workerReturnsAtOnceIfTheOpenStreamHasSyncedSinceOpening() = runBlocking {
        val control = FakeControl()
        val clock = FakeClock(t0)
        val connection = connection(control, clock = clock)

        connection.connect(Holder.VISIBLE_APP)
        eventually { connection.isConnected }
        control.lastSyncedAt.value = t0 + 1.seconds

        // The idle stream gets no new checkpoints.
        clock.now = t0 + 10.minutes
        connection.connect(Holder.BACKGROUND_WORKER)
        withTimeout(1.seconds) {
            connection.awaitSyncedAndUploaded(since = clock.now - 2.seconds)
        }
        connection.disconnectWhenIdle(Holder.BACKGROUND_WORKER)

        assertEquals(listOf("connect"), control.calls.toList())
        connection.close()
    }

    @Test
    fun workerReturnsAtOnceIfTheOpenStreamSyncedWithinTheTruncationMargin() = runBlocking {
        val control = FakeControl()
        val clock = FakeClock(t0 + 700.milliseconds)
        val connection = connection(control, clock = clock)

        connection.connect(Holder.VISIBLE_APP)
        eventually { connection.isConnected }
        // Synced right after opening, but truncated to seconds.
        control.lastSyncedAt.value = t0

        clock.now = t0 + 10.minutes
        connection.connect(Holder.BACKGROUND_WORKER)
        withTimeout(1.seconds) {
            connection.awaitSyncedAndUploaded(since = clock.now - 2.seconds)
        }
        connection.close()
    }

    @Test
    fun workerWaitsForTheFirstSyncOfAFreshlyOpenedStream() = runBlocking {
        val control = FakeControl()
        val clock = FakeClock(t0 + 10.minutes)
        val connection = connection(control, clock = clock)
        // Synced in an earlier run.
        control.lastSyncedAt.value = t0

        connection.connect(Holder.BACKGROUND_WORKER)
        val wait = async {
            connection.awaitSyncedAndUploaded(since = clock.now - 2.seconds)
        }
        eventually { connection.isConnected }
        settle()
        assertFalse(wait.isCompleted)

        control.lastSyncedAt.value = t0 + 10.minutes + 1.seconds
        withTimeout(1.seconds) {
            wait.await()
        }
        connection.close()
    }
}
