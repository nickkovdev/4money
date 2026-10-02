package ua.com.radiokot.money.powersync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.powersync.PowerSyncConnection.Holder
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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

        override suspend fun awaitUploadsIdle() {
            uploadsIdle.await()
        }

        override suspend fun awaitSyncedAndUploaded(since: Instant) =
            awaitCancellation()
    }

    private fun connection(
        control: FakeControl,
        idleDisconnectTimeout: Duration = 30.seconds,
    ) = PowerSyncConnection(
        control = control,
        idleDisconnectTimeout = idleDisconnectTimeout,
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
}
