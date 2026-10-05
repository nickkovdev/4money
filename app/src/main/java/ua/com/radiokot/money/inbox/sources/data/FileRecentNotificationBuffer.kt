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

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * [RecentNotificationBuffer] in a JSON [file]. A corrupt file reads as empty
 * and is overwritten by the next [add].
 *
 * @param now epoch millis
 */
class FileRecentNotificationBuffer(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) : RecentNotificationBuffer {

    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun add(notification: RecentNotification) = synchronized(lock) {
        val kept = read()

        if (kept.any { it.isSameAs(notification) }) {
            return@synchronized
        }

        write(
            (kept + notification)
                .sortedByDescending(RecentNotification::postTimeMillis)
                .take(MAX_COUNT)
        )
    }

    override fun getAll(): List<RecentNotification> = synchronized(lock) {
        read()
    }

    override fun clear() = synchronized(lock) {
        file.delete()
        Unit
    }

    /**
     * @return kept entries not older than [MAX_AGE_MILLIS], newest first
     */
    private fun read(): List<RecentNotification> {
        val minPostTime = now() - MAX_AGE_MILLIS

        val entries =
            try {
                if (file.exists())
                    json.decodeFromString<List<RecentNotification>>(file.readText())
                else
                    emptyList()
            } catch (e: Exception) {
                emptyList()
            }

        return entries
            .filter { it.postTimeMillis >= minPostTime }
            .sortedByDescending(RecentNotification::postTimeMillis)
    }

    private fun write(entries: List<RecentNotification>) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.path + ".tmp")
        tempFile.writeText(json.encodeToString(entries))
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun RecentNotification.isSameAs(other: RecentNotification) =
        packageName == other.packageName
                && title == other.title
                && text == other.text

    private companion object {
        const val MAX_COUNT = 50
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
