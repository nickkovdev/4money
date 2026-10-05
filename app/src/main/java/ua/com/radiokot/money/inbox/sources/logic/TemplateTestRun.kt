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

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate

/**
 * @param matched the notifications understood as payments, in the order given
 * @param unmatched the rest, in the order given
 */
data class TestRunResult(
    val matched: List<Pair<RecentNotification, ParsedBankNotification.Payment>>,
    val unmatched: List<RecentNotification>,
)

/**
 * Tries the [preset] and the enabled [templates] on every one of the [notifications],
 * exactly as the listener would. Pure, nothing is saved.
 */
fun runTest(
    preset: NotificationSourcePreset?,
    templates: List<NotificationTemplate>,
    notifications: List<RecentNotification>,
): TestRunResult {
    val matched =
        mutableListOf<Pair<RecentNotification, ParsedBankNotification.Payment>>()
    val unmatched = mutableListOf<RecentNotification>()

    notifications.forEach { notification ->
        val result = NotificationSourceRegistry.parseWith(
            preset = preset,
            templates = templates,
            title = notification.title,
            text = notification.text,
        )

        if (result is ParsedBankNotification.Payment) {
            matched += notification to result
        } else {
            unmatched += notification
        }
    }

    return TestRunResult(matched, unmatched)
}
