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

package ua.com.radiokot.money.inbox.sources.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.logic.EXAMPLE_PACKAGE
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry.Source
import ua.com.radiokot.money.inbox.sources.logic.SebLatviaPreset
import ua.com.radiokot.money.inbox.sources.logic.testTemplate
import ua.com.radiokot.money.uikit.ViewText
import java.math.BigDecimal

class TestTextRunnerTest {

    private val labelOf: (String) -> String = { "label:$it" }

    private val presetSource = Source(
        packageName = SebLatviaPreset.packageName,
        preset = SebLatviaPreset,
        templates = emptyList(),
        isEnabled = true,
    )

    private fun templateSource(vararg ids: String, isEnabled: Boolean = true) = Source(
        packageName = EXAMPLE_PACKAGE,
        preset = null,
        templates = ids.map { testTemplate(id = it, isEnabled = isEnabled) },
        isEnabled = isEnabled,
    )

    @Test
    fun matchingTemplateGivesThePayment() {
        val outcome = TestTextRunner.run(
            sources = listOf(templateSource("t1")),
            title = null,
            text = "Paid 12,50 EUR at COFFEE POINT",
            labelOf = labelOf,
        )

        assertEquals(1, outcome.matches.size)
        val match = outcome.matches.single()
        assertEquals("label:$EXAMPLE_PACKAGE", match.sourceLabel)
        assertEquals(ViewText.Plain("Paid"), match.kind)
        assertEquals("COFFEE POINT", match.payment.payee)
        assertEquals(0, BigDecimal("12.50").compareTo(match.payment.amount))
        assertEquals("EUR", match.payment.currencyCode)
        assertTrue(outcome.noMatch.isEmpty())
    }

    @Test
    fun nonMatchingKindsAreCollected() {
        val outcome = TestTextRunner.run(
            sources = listOf(presetSource, templateSource("t1", "t2")),
            title = null,
            text = "Something else entirely",
            labelOf = labelOf,
        )

        assertTrue(outcome.matches.isEmpty())
        // The preset once, every template on its own.
        assertEquals(3, outcome.noMatch.size)
        assertEquals(ViewText.Res(R.string.test_builtin), outcome.noMatch.first().kind)
    }

    @Test
    fun disabledTemplatesAreTriedToo() {
        val outcome = TestTextRunner.run(
            sources = listOf(templateSource("t1", isEnabled = false)),
            title = null,
            text = "Paid 3,40 EUR at Fuelstop",
            labelOf = labelOf,
        )

        assertEquals(1, outcome.matches.size)
    }

    @Test
    fun blankTitleIsNoTitle() {
        val outcome = TestTextRunner.run(
            sources = listOf(templateSource("t1")),
            title = "  ",
            text = "Paid 3,40 EUR at Fuelstop",
            labelOf = labelOf,
        )

        assertEquals(1, outcome.matches.size)
    }

    @Test
    fun titleCountsAsTheFirstLine() {
        val outcome = TestTextRunner.run(
            sources = listOf(templateSource("t1")),
            title = "Card payment",
            text = "Paid 3,40 EUR at Fuelstop",
            labelOf = labelOf,
        )

        assertEquals(1, outcome.matches.size)
    }

    @Test
    fun blankTextGivesNothing() {
        val outcome = TestTextRunner.run(
            sources = listOf(presetSource, templateSource("t1")),
            title = "Title",
            text = "   ",
            labelOf = labelOf,
        )

        assertTrue(outcome.matches.isEmpty())
        assertTrue(outcome.noMatch.isEmpty())
    }
}
