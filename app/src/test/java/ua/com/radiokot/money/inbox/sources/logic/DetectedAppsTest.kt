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

package ua.com.radiokot.money.inbox.sources.logic

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.sources.data.RecentNotification

class DetectedAppsTest {

    private val zone = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 5, 12, 0).toInstant(zone).toEpochMilliseconds()
    private val today = LocalDateTime(2026, 10, 5, 9, 12).toInstant(zone).toEpochMilliseconds()
    private val earlier = LocalDateTime(2026, 10, 3, 9, 12).toInstant(zone).toEpochMilliseconds()

    private fun notification(
        packageName: String,
        postTime: Long,
        text: String = "Paid 3.40 EUR",
    ) = RecentNotification(
        packageName = packageName,
        postTimeMillis = postTime,
        title = null,
        text = text,
    )

    private fun detect(
        notifications: List<RecentNotification>,
        exclude: Set<String> = emptySet(),
    ) = detectApps(notifications, now, exclude, zone)

    @Test
    fun empty_noApps() {
        assertEquals(emptyList<DetectedApp>(), detect(emptyList()))
    }

    @Test
    fun counts_totalAndToday() {
        val apps = detect(
            listOf(
                notification("com.example.bank", today, "a"),
                notification("com.example.bank", earlier, "b"),
                notification("com.example.bank", earlier, "c"),
            )
        )

        assertEquals(
            listOf(DetectedApp("com.example.bank", count = 3, todayCount = 1, isDuplicateWallet = false)),
            apps,
        )
    }

    @Test
    fun sortedByCountDescending() {
        val apps = detect(
            listOf(
                notification("com.example.few", today, "a"),
                notification("com.example.many", today, "b"),
                notification("com.example.many", earlier, "c"),
                notification("com.example.many", earlier, "d"),
            )
        )

        assertEquals(listOf("com.example.many", "com.example.few"), apps.map { it.packageName })
    }

    @Test
    fun equalCounts_sortedByPackage() {
        val apps = detect(
            listOf(
                notification("com.example.b", today, "a"),
                notification("com.example.a", today, "b"),
            )
        )

        assertEquals(listOf("com.example.a", "com.example.b"), apps.map { it.packageName })
    }

    @Test
    fun excludedPackages_dropped() {
        val apps = detect(
            notifications = listOf(
                notification("com.example.bank", today),
                notification("com.example.source", today),
            ),
            exclude = setOf("com.example.source"),
        )

        assertEquals(listOf("com.example.bank"), apps.map { it.packageName })
    }

    @Test
    fun googleWallet_markedAsDuplicate() {
        val apps = detect(
            listOf(
                notification(GOOGLE_WALLET_PACKAGE, today),
                notification("com.example.bank", today),
            )
        )

        assertTrue(apps.single { it.packageName == GOOGLE_WALLET_PACKAGE }.isDuplicateWallet)
        assertFalse(apps.single { it.packageName == "com.example.bank" }.isDuplicateWallet)
    }

    @Test
    fun todayBoundary_localMidnight() {
        val justBefore = LocalDateTime(2026, 10, 4, 23, 59).toInstant(zone).toEpochMilliseconds()
        val justAfter = LocalDateTime(2026, 10, 5, 0, 0).toInstant(zone).toEpochMilliseconds()

        val app = detect(
            listOf(
                notification("com.example.bank", justBefore, "a"),
                notification("com.example.bank", justAfter, "b"),
            )
        ).single()

        assertEquals(1, app.todayCount)
    }

    @Test
    fun distinct_dropsSamePackageTitleAndText_keepsFirst() {
        val first = notification("com.example.bank", today, "same")
        val duplicate = notification("com.example.bank", earlier, "same")
        val otherApp = notification("com.example.other", today, "same")
        val otherTitle = first.copy(title = "Title")

        assertEquals(
            listOf(first, otherApp, otherTitle),
            listOf(first, duplicate, otherApp, otherTitle).distinctNotifications(),
        )
    }

    @Test
    fun mergeSamples_forPackage_newestFirstDistinct() {
        val old = notification("com.example.bank", earlier, "old")
        val fresh = notification("com.example.bank", today, "fresh")
        val otherApp = notification("com.example.other", today, "other")

        val merged = mergeSamples(
            buffered = listOf(old),
            active = listOf(fresh, otherApp, old.copy(postTimeMillis = earlier + 1)),
            packageName = "com.example.bank",
        )

        // The same text seen twice is one sample, the later sighting is kept.
        assertEquals(listOf(fresh, old.copy(postTimeMillis = earlier + 1)), merged)
    }
}
