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

package ua.com.radiokot.money.inbox.templates.data

import kotlinx.datetime.LocalDateTime

/**
 * A user-taught notification template: a regex matched against
 * the notification title + "\n" + text, with the fields located by capture group numbers.
 */
data class NotificationTemplate(
    val id: String,
    val sourcePackage: String,
    val name: String,
    val direction: Direction,
    val pattern: String,
    val fields: TemplateFields,
    val sampleText: String,
    val isEnabled: Boolean,
    val createdAt: LocalDateTime,
) {
    enum class Direction(val value: String) {
        Outgoing("outgoing"),
        Incoming("incoming"),
        ;

        companion object {
            fun fromValue(value: String): Direction =
                entries.first { it.value == value }
        }
    }
}
