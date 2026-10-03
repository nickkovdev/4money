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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ViewDateFormatsTest {
    private val en = Locale.ENGLISH
    private val ru = Locale.forLanguageTag("ru")
    private val date = LocalDate(2026, 10, 1)
    private val today = LocalDate(2026, 10, 3)

    @Test
    fun dateIsThursday() {
        assertEquals(java.time.DayOfWeek.THURSDAY, java.time.LocalDate.of(2026, 10, 1).dayOfWeek)
    }

    @Test
    fun monthYear() {
        assertEquals("October 2026", ViewDateFormats.monthYear(date, en))
        assertEquals("Октябрь 2026", ViewDateFormats.monthYear(date, ru))
    }

    @Test
    fun weekdayDay() {
        assertEquals("Thursday, 1", ViewDateFormats.weekdayDay(date, en))
        assertEquals("Четверг, 1", ViewDateFormats.weekdayDay(date, ru))
    }

    @Test
    fun dayMonthSameYear() {
        assertEquals("1 October", ViewDateFormats.dayMonth(date, en, today))
        assertEquals("1 октября", ViewDateFormats.dayMonth(date, ru, today))
    }

    @Test
    fun dayMonthOtherYear() {
        val other = LocalDate(2025, 12, 31)
        assertEquals("31 December 2025", ViewDateFormats.dayMonth(other, en, today))
        assertEquals("31 декабря 2025", ViewDateFormats.dayMonth(other, ru, today))
    }

    @Test
    fun time() {
        val dateTime = LocalDateTime(2026, 10, 1, 14, 5)
        assertEquals("14:05", ViewDateFormats.time(dateTime, en))
        assertEquals("14:05", ViewDateFormats.time(dateTime, ru))
    }
}
