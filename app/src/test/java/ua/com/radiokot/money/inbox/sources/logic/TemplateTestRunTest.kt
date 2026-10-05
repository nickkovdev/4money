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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.sources.data.RecentNotification

class TemplateTestRunTest {

    private fun notification(
        text: String,
        packageName: String = EXAMPLE_PACKAGE,
        title: String? = null,
        time: Long = 1L,
    ) = RecentNotification(
        packageName = packageName,
        postTimeMillis = time,
        title = title,
        text = text,
    )

    @Test
    fun sebPresetAndDraft_splitMatchedAndUnmatched() {
        val seb = notification(
            packageName = SebLatviaPreset.packageName,
            text = "Jūs samaksājāt 3,40 EUR par 04/10/2026 09:12 karte...1234 COFFEE POINT .",
        )
        val draftPaid = notification(
            packageName = SebLatviaPreset.packageName,
            text = "Paid 7,80 EUR at Fuelstop",
        )
        val other = notification(
            packageName = SebLatviaPreset.packageName,
            text = "Something else entirely",
        )

        val result = runTest(
            preset = SebLatviaPreset,
            templates = listOf(testTemplate("draft", sourcePackage = SebLatviaPreset.packageName)),
            notifications = listOf(seb, draftPaid, other),
        )

        assertEquals(listOf(seb, draftPaid), result.matched.map { it.first })
        assertEquals("COFFEE POINT", result.matched[0].second.payee)
        assertEquals("1234", result.matched[0].second.cardLast4)
        assertEquals("Fuelstop", result.matched[1].second.payee)
        assertEquals(listOf(other), result.unmatched)
    }

    @Test
    fun draftForUnknownPackage_withoutPreset_matchesItsTexts() {
        val paid = notification(text = "Paid 12,50 EUR at Fuelstop")
        val other = notification(text = "Hello")

        val result = runTest(
            preset = null,
            templates = listOf(testTemplate("draft")),
            notifications = listOf(paid, other),
        )

        assertEquals(listOf(paid), result.matched.map { it.first })
        assertEquals(listOf(other), result.unmatched)
    }

    @Test
    fun disabledTemplatesAreIgnored() {
        val paid = notification(text = "Paid 12,50 EUR at Fuelstop")

        val result = runTest(
            preset = null,
            templates = listOf(testTemplate("off", isEnabled = false)),
            notifications = listOf(paid),
        )

        assertTrue(result.matched.isEmpty())
        assertEquals(listOf(paid), result.unmatched)
    }

    @Test
    fun titleIsPartOfTheInput() {
        val paid = notification(title = "Card", text = "Paid 12,50 EUR at Fuelstop")

        val result = runTest(
            preset = null,
            templates = listOf(testTemplate("draft")),
            notifications = listOf(paid),
        )

        assertEquals(listOf(paid), result.matched.map { it.first })
    }

    @Test
    fun noNotifications_isEmpty() {
        val result = runTest(
            preset = SebLatviaPreset,
            templates = emptyList(),
            notifications = emptyList(),
        )

        assertTrue(result.matched.isEmpty())
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun orderOfNotificationsIsKept() {
        val first = notification(text = "Paid 1,00 EUR at A", time = 3L)
        val second = notification(text = "nope", time = 2L)
        val third = notification(text = "Paid 2,00 EUR at B", time = 1L)

        val result = runTest(null, listOf(testTemplate("t")), listOf(first, second, third))

        assertEquals(listOf(first, third), result.matched.map { it.first })
    }
}
