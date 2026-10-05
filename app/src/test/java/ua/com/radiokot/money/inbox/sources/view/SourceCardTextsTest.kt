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

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.data.SourceStats
import ua.com.radiokot.money.inbox.sources.logic.EXAMPLE_PACKAGE
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourcePreset
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry.Source
import ua.com.radiokot.money.inbox.sources.logic.SebLatviaPreset
import ua.com.radiokot.money.inbox.sources.logic.testTemplate
import ua.com.radiokot.money.uikit.ViewText

class SourceCardTextsTest {

    private val lastAt = LocalDateTime(2026, 10, 2, 9, 12)
    private val format: (LocalDateTime) -> String = { "<${it.hour}:${it.minute}>" }

    private val presetSource = Source(
        packageName = SebLatviaPreset.packageName,
        preset = SebLatviaPreset,
        templates = emptyList(),
        isEnabled = true,
    )

    private fun templateSource(vararg names: String) = Source(
        packageName = EXAMPLE_PACKAGE,
        preset = null,
        templates = names.mapIndexed { index, name ->
            testTemplate(id = "t$index").copy(name = name)
        },
        isEnabled = true,
    )

    @Test
    fun presetKindsAreLocalizedChips() {
        val texts = SourceCardTexts.of(presetSource, stats = null, formatLastReceived = format)

        assertEquals(
            listOf(
                ViewText.Res(R.string.sources_kind_card_payment),
                ViewText.Res(R.string.sources_kind_account_payment),
                ViewText.Res(R.string.sources_kind_incoming_payment),
            ),
            texts.kindChips,
        )
    }

    @Test
    fun everyPresetKindHasItsOwnLabel() {
        val labels = NotificationSourcePreset.Kind.entries.map(SourceCardTexts::kindLabel)

        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun presetKindsComeBeforeTemplateNames() {
        val source = presetSource.copy(
            templates = listOf(testTemplate(id = "t1").copy(name = "Top-up")),
        )

        val texts = SourceCardTexts.of(source, stats = null, formatLastReceived = format)

        assertEquals(
            ViewText.Plain("Top-up"),
            texts.kindChips.last(),
        )
        assertEquals(4, texts.kindChips.size)
    }

    @Test
    fun templateKindsAreChipsByName() {
        val texts = SourceCardTexts.of(
            templateSource("Paid", "Received"),
            stats = null,
            formatLastReceived = format,
        )

        assertEquals(
            listOf(ViewText.Plain("Paid"), ViewText.Plain("Received")),
            texts.kindChips,
        )
    }

    @Test
    fun noKindsSaysSoAndOffersSetUp() {
        val texts = SourceCardTexts.of(
            templateSource(),
            stats = null,
            formatLastReceived = format,
        )

        assertEquals(emptyList<ViewText>(), texts.kindChips)
        assertEquals(ViewText.Res(R.string.sources_meta_no_kinds), texts.meta)
        assertEquals(ViewText.Res(R.string.sources_set_up), texts.teachActionText)
    }

    @Test
    fun withKindsTheActionTeachesAnotherOne() {
        val texts = SourceCardTexts.of(presetSource, stats = null, formatLastReceived = format)

        assertEquals(ViewText.Res(R.string.sources_teach_another), texts.teachActionText)
    }

    @Test
    fun metaIsTheLastReceivedTime() {
        val texts = SourceCardTexts.of(
            presetSource,
            stats = SourceStats(recognizedCount = 47, lastReceivedAt = lastAt),
            formatLastReceived = format,
        )

        assertEquals(
            ViewText.Res(R.string.sources_meta_last, listOf(ViewText.Plain("<9:12>"))),
            texts.meta,
        )
    }

    @Test
    fun metaWithoutStatsSaysNothingReceived() {
        val texts = SourceCardTexts.of(presetSource, stats = null, formatLastReceived = format)

        assertEquals(ViewText.Res(R.string.sources_meta_nothing_yet), texts.meta)
    }

    @Test
    fun recognizedLineCountsThisMonth() {
        val texts = SourceCardTexts.of(
            presetSource,
            stats = SourceStats(recognizedCount = 47, lastReceivedAt = lastAt),
            formatLastReceived = format,
        )

        assertEquals(
            ViewText.Plural(R.plurals.sources_recognized_month, 47),
            texts.recognized,
        )
    }

    @Test
    fun noRecognizedLineWhenNothingWasRecognized() {
        val texts = SourceCardTexts.of(
            presetSource,
            stats = SourceStats(recognizedCount = 0, lastReceivedAt = lastAt),
            formatLastReceived = format,
        )

        assertNull(texts.recognized)
    }
}
