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

package ua.com.radiokot.money.inbox.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.transfers.view.ViewDateFormats
import ua.com.radiokot.money.transfers.view.rememberAppLocale
import kotlin.time.Clock

/**
 * "Today at 11:55" or "3 Oct at 11:55" in the app locale.
 */
@Composable
fun receivedAtText(receivedAt: LocalDateTime): String {
    val locale = rememberAppLocale()
    val time = remember(receivedAt, locale) {
        ViewDateFormats.time(receivedAt, locale)
    }
    val today = remember(receivedAt) {
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }
    val dayMonthText = remember(receivedAt, locale, today) {
        ViewDateFormats.dayMonth(receivedAt.date, locale, today)
    }
    return if (receivedAt.date == today)
        stringResource(R.string.date_today_at, time)
    else
        stringResource(R.string.date_at_time, dayMonthText, time)
}
