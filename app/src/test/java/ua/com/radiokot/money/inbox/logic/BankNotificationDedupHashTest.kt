package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.data.IncomingBankNotification

class BankNotificationDedupHashTest {

    private val notification = IncomingBankNotification(
        packageName = "se.seb.latvia",
        postTimeMillis = 1_790_000_000_000,
        title = "Jauna rezervācija",
        text = "Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .",
    )

    @Test
    fun isStableHex() {
        val hash = BankNotificationDedupHash.compute(notification, includePostTime = false)
        assertEquals(hash, BankNotificationDedupHash.compute(notification.copy(), includePostTime = false))
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun repostWithNewPostTimeIsSameWhenPostTimeExcluded() {
        assertEquals(
            BankNotificationDedupHash.compute(notification, includePostTime = false),
            BankNotificationDedupHash.compute(notification.copy(postTimeMillis = 1_790_000_005_000), includePostTime = false),
        )
    }

    @Test
    fun postTimeMattersWhenIncluded() {
        assertNotEquals(
            BankNotificationDedupHash.compute(notification, includePostTime = true),
            BankNotificationDedupHash.compute(notification.copy(postTimeMillis = 1_790_000_005_000), includePostTime = true),
        )
    }

    @Test
    fun anyFieldChangeChangesHash() {
        val base = BankNotificationDedupHash.compute(notification, includePostTime = false)
        listOf(
            notification.copy(packageName = "other.bank"),
            notification.copy(title = null),
            notification.copy(text = notification.text.replace("2,12", "2,13")),
        ).forEach { changed ->
            assertNotEquals(base, BankNotificationDedupHash.compute(changed, includePostTime = false))
        }
    }

    @Test
    fun fieldBoundariesAreUnambiguous() {
        assertNotEquals(
            BankNotificationDedupHash.compute(notification.copy(title = "ab", text = "c"), includePostTime = false),
            BankNotificationDedupHash.compute(notification.copy(title = "a", text = "bc"), includePostTime = false),
        )
    }
}
