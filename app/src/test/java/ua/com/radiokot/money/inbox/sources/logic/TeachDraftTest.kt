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

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.logic.TemplateBuilder
import ua.com.radiokot.money.inbox.templates.logic.TokenRole

class TeachDraftTest {

    private val sample = RecentNotification(
        packageName = EXAMPLE_PACKAGE,
        postTimeMillis = 1L,
        title = "New booking",
        text = "You paid 3,40 EUR at 04/10/2026 09:12 card 1234 COFFEE POINT .",
    )

    private fun indexOf(draft: TeachDraft, text: String) =
        draft.tokens.first { it.text == text }.index

    @Test
    fun of_marksTheDetectedAmountAndCurrency() {
        val draft = TeachDraft.of(sample)

        assertEquals(listOf("3,40"), draft.markedTexts(TokenRole.Amount))
        assertEquals(listOf("EUR"), draft.markedTexts(TokenRole.Currency))
        assertEquals(NotificationTemplate.Direction.Outgoing, draft.direction)
    }

    @Test
    fun withoutPayee_isMissingPayee() {
        val result = TeachDraft.of(sample).analyze().result

        assertEquals(
            TemplateBuilder.Problem.MissingPayee,
            (result as TemplateBuilder.Result.Invalid).problem,
        )
        assertNull(TeachDraft.of(sample).analyze().payment)
    }

    @Test
    fun markedPayeeAndCard_buildsAndPreviewsThePayment() {
        var draft = TeachDraft.of(sample)
        draft = draft.withRole(indexOf(draft, "COFFEE"), TokenRole.Payee)
        draft = draft.withRole(indexOf(draft, "POINT"), TokenRole.Payee)
        draft = draft.withRole(indexOf(draft, "1234"), TokenRole.Card)

        val analysis = draft.analyze()
        val payment = analysis.payment

        assertTrue(analysis.result is TemplateBuilder.Result.Built)
        assertNotNull(payment)
        assertEquals("COFFEE POINT", payment!!.payee)
        assertEquals("1234", payment.cardLast4)
        assertEquals("EUR", payment.currencyCode)
        assertFalse(payment.isIncoming)
    }

    @Test
    fun incomingDirection_isPassedToThePreview() {
        var draft = TeachDraft.of(sample)
        draft = draft.withRole(indexOf(draft, "COFFEE"), TokenRole.Payee)
        draft = draft.withRole(indexOf(draft, "POINT"), TokenRole.Payee)
        draft = draft.withDirection(NotificationTemplate.Direction.Incoming)

        assertTrue(draft.analyze().payment!!.isIncoming)
    }

    @Test
    fun cardIsOfferedOnlyOnFourToSixDigitNumbers() {
        val draft = TeachDraft.of(
            sample.copy(text = "Paid 3,40 EUR card 1234 ref 12 code 1234567 COFFEE POINT")
        )

        assertTrue(draft.canBeCard(indexOf(draft, "1234")))
        assertFalse(draft.canBeCard(indexOf(draft, "12")))
        assertFalse(draft.canBeCard(indexOf(draft, "1234567")))
        assertFalse(draft.canBeCard(indexOf(draft, "COFFEE")))
        assertFalse(draft.canBeCard(indexOf(draft, "3,40")))
    }

    @Test
    fun cardMarkOnAWord_isIgnored() {
        val draft = TeachDraft.of(sample)
        val coffee = indexOf(draft, "COFFEE")

        assertEquals(draft.marks, draft.withRole(coffee, TokenRole.Card).marks)
    }

    @Test
    fun amountMark_movesToTheNewToken() {
        val draft = TeachDraft.of(
            sample.copy(text = "You paid 3,40 EUR at 04/10/2026 balance 100,00 EUR COFFEE POINT")
        )
        val balance = indexOf(draft, "100,00")

        val moved = draft.withRole(balance, TokenRole.Amount)

        assertEquals(listOf("100,00"), moved.markedTexts(TokenRole.Amount))
    }

    @Test
    fun clearRemovesTheMark() {
        val draft = TeachDraft.of(sample)
        val cleared = draft.withRole(indexOf(draft, "3,40"), null)

        assertTrue(cleared.markedTexts(TokenRole.Amount).isEmpty())
    }

    @Test
    fun payeeNextToVaries_isReported() {
        var draft = TeachDraft.of(sample)
        draft = draft.withRole(indexOf(draft, "COFFEE"), TokenRole.Payee)
        draft = draft.withRole(indexOf(draft, "POINT"), TokenRole.Varies)

        assertEquals(
            TemplateBuilder.Problem.PayeeNextToVaries,
            (draft.analyze().result as TemplateBuilder.Result.Invalid).problem,
        )
    }

    @Test
    fun tappingTwice_closesTheChooser_marking_closesIt() {
        val draft = TeachDraft.of(sample)
        val coffee = indexOf(draft, "COFFEE")

        val open = draft.withTokenTapped(coffee)
        assertEquals(coffee, open.selectedTokenIndex)
        assertNull(open.withTokenTapped(coffee).selectedTokenIndex)
        assertNull(open.withRole(coffee, TokenRole.Payee).selectedTokenIndex)
    }

    @Test
    fun toTemplate_isNullUntilBuilt_thenNamedByTheTitle() {
        var draft = TeachDraft.of(sample)
        val at = LocalDateTime(2026, 10, 4, 9, 30)
        assertNull(draft.toTemplate("id", EXAMPLE_PACKAGE, at))

        draft = draft.withRole(indexOf(draft, "COFFEE"), TokenRole.Payee)
        draft = draft.withRole(indexOf(draft, "POINT"), TokenRole.Payee)
        val template = draft.toTemplate("id", EXAMPLE_PACKAGE, at)!!

        assertEquals("New booking", template.name)
        assertEquals(draft.input, template.sampleText)
        assertEquals(EXAMPLE_PACKAGE, template.sourcePackage)
        assertTrue(template.isEnabled)
        assertTrue(template.fields.hasTimestamp)
        assertEquals(at, template.createdAt)
    }
}
