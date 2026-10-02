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

package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Banks re-post and update notifications; the same payment must not be recorded twice.
 *
 * Recognized payments carry their own minute, amount, card and payee in the text,
 * so (package, title, text) identifies them across re-posts with a new post time or ID.
 * Unrecognized texts carry no timestamp, so the post time is added to keep
 * repeated identical texts on different occasions apart.
 */
object BankNotificationDedupHash {

    fun compute(
        notification: IncomingBankNotification,
        includePostTime: Boolean,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")

        listOf(
            notification.packageName,
            notification.title ?: "",
            notification.text,
            if (includePostTime) notification.postTimeMillis.toString() else "",
        ).forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            // Length prefix keeps ("ab", "c") and ("a", "bc") apart.
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }

        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
