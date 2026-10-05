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

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.inbox.sources.data.RecentNotification

const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"

/**
 * An app that recently posted money-like notifications.
 *
 * @param count all the notifications of the app
 * @param todayCount the ones posted today
 * @param isDuplicateWallet the app is Google Wallet, which repeats the notifications of the bank
 */
data class DetectedApp(
    val packageName: String,
    val count: Int,
    val todayCount: Int,
    val isDuplicateWallet: Boolean,
)

/**
 * @return the apps of the [notifications] but [excludePackages],
 * the most talkative first, then by the package
 */
fun detectApps(
    notifications: List<RecentNotification>,
    now: Long,
    excludePackages: Set<String>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<DetectedApp> {
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date

    return notifications
        .filter { it.packageName !in excludePackages }
        .groupBy(RecentNotification::packageName)
        .map { (packageName, appNotifications) ->
            DetectedApp(
                packageName = packageName,
                count = appNotifications.size,
                todayCount = appNotifications.count {
                    Instant.fromEpochMilliseconds(it.postTimeMillis)
                        .toLocalDateTime(timeZone).date == today
                },
                isDuplicateWallet = packageName == GOOGLE_WALLET_PACKAGE,
            )
        }
        .sortedWith(
            compareByDescending<DetectedApp> { it.count }
                .thenBy { it.packageName }
        )
}

/**
 * Drops the notifications of the same package, title and text, keeping the first.
 */
fun List<RecentNotification>.distinctNotifications(): List<RecentNotification> =
    distinctBy { Triple(it.packageName, it.title, it.text) }

/**
 * @return the notifications of the [packageName] from both lists, distinct, newest first
 */
fun mergeSamples(
    buffered: List<RecentNotification>,
    active: List<RecentNotification>,
    packageName: String,
): List<RecentNotification> =
    (active + buffered)
        .filter { it.packageName == packageName }
        .sortedByDescending(RecentNotification::postTimeMillis)
        .distinctNotifications()
