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

package ua.com.radiokot.money.transfers.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates formatted for the app [Locale].
 */
object ViewDateFormats {

    /**
     * "October 2026" / "Октябрь 2026"
     */
    fun monthYear(
        date: LocalDate,
        locale: Locale,
    ): String =
        format(date, "LLLL yyyy", locale)
            .titlecaseFirst(locale)

    /**
     * "Thursday, 1" / "Четверг, 1"
     */
    fun weekdayDay(
        date: LocalDate,
        locale: Locale,
    ): String =
        format(date, "EEEE, d", locale)
            .titlecaseFirst(locale)

    /**
     * "1 October" / "1 октября", with the year if it is not the [today]'s one.
     */
    fun dayMonth(
        date: LocalDate,
        locale: Locale,
        today: LocalDate,
    ): String =
        format(
            date = date,
            pattern =
                if (date.year == today.year)
                    "d MMMM"
                else
                    "d MMMM yyyy",
            locale = locale,
        )

    /**
     * "14:05"
     */
    fun time(
        dateTime: LocalDateTime,
        locale: Locale,
    ): String =
        DateTimeFormatter.ofPattern("HH:mm", locale)
            .format(dateTime.toJavaLocalDateTime())

    private fun format(
        date: LocalDate,
        pattern: String,
        locale: Locale,
    ): String =
        DateTimeFormatter.ofPattern(pattern, locale)
            .format(date.toJavaLocalDate())

    private fun String.titlecaseFirst(locale: Locale): String =
        replaceFirstChar { it.titlecase(locale) }
}

/**
 * The app language locale, changes together with the language.
 */
@Composable
fun rememberAppLocale(): Locale =
    LocalConfiguration.current.locales[0]
