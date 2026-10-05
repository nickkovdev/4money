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

package ua.com.radiokot.money.inbox.sources.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileRecentNotificationBufferTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val day = 24L * 3600 * 1000
    private var now = 100L * day
    private val file: File by lazy { File(folder.root, "recent_money_notifications.json") }

    private fun buffer() = FileRecentNotificationBuffer(file = file, now = { now })

    private fun notification(
        n: Int,
        postTimeMillis: Long = now - 1000L * (100 - n),
        packageName: String = "com.example.bank",
    ) = RecentNotification(
        packageName = packageName,
        postTimeMillis = postTimeMillis,
        title = "Payment",
        text = "Paid $n,00 EUR at Fuelstop",
    )

    @Test
    fun newestFirstAndPersisted() {
        val buffer = buffer()
        buffer.add(notification(1))
        buffer.add(notification(3))
        buffer.add(notification(2))

        assertEquals(listOf(3, 2, 1).map(::notification), buffer.getAll())
        assertEquals(listOf(3, 2, 1).map(::notification), buffer().getAll())
    }

    @Test
    fun keepsNewest50() {
        val buffer = buffer()
        (1..51).forEach { buffer.add(notification(it)) }

        val all = buffer().getAll()
        assertEquals(50, all.size)
        assertEquals(notification(51), all.first())
        assertEquals(notification(2), all.last())
    }

    @Test
    fun entriesOlderThan7DaysAreDropped() {
        val buffer = buffer()
        buffer.add(notification(1, postTimeMillis = now - 8 * day))
        buffer.add(notification(2, postTimeMillis = now - 6 * day))

        assertEquals(listOf(notification(2, postTimeMillis = now - 6 * day)), buffer.getAll())

        now += 2 * day
        assertTrue(buffer.getAll().isEmpty())
    }

    @Test
    fun duplicateIsNotAddedTwice() {
        val buffer = buffer()
        buffer.add(notification(1))
        buffer.add(notification(1).copy(postTimeMillis = now))

        assertEquals(listOf(notification(1)), buffer.getAll())

        // Same text of another app is another notification.
        buffer.add(notification(1, packageName = "com.example.other"))
        assertEquals(2, buffer.getAll().size)
    }

    @Test
    fun clearEmptiesAndSurvivesNewInstance() {
        val buffer = buffer()
        buffer.add(notification(1))

        buffer.clear()

        assertTrue(buffer.getAll().isEmpty())
        assertTrue(buffer().getAll().isEmpty())
    }

    @Test
    fun corruptFileReadsEmptyAndIsOverwritten() {
        file.writeText("{not json")

        val buffer = buffer()
        assertTrue(buffer.getAll().isEmpty())

        buffer.add(notification(1))
        assertEquals(listOf(notification(1)), buffer().getAll())
    }

    @Test
    fun nullTitleIsKept() {
        val buffer = buffer()
        buffer.add(notification(1).copy(title = null))

        assertEquals(listOf(notification(1).copy(title = null)), buffer().getAll())
    }
}
