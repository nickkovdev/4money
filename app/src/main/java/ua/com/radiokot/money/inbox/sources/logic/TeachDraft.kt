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
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.logic.SampleToken
import ua.com.radiokot.money.inbox.templates.logic.SampleTokenizer
import ua.com.radiokot.money.inbox.templates.logic.TemplateBuilder
import ua.com.radiokot.money.inbox.templates.logic.TemplateMatcher
import ua.com.radiokot.money.inbox.templates.logic.TokenRole

/**
 * The marks of the Teach step: what the user says each word of the [sample] is.
 * Pure, the sample is tokenized as `title + "\n" + text` like the templates are matched.
 *
 * @param marks roles by [SampleToken.index]
 * @param selectedTokenIndex the token whose role chooser is open
 */
data class TeachDraft(
    val sample: RecentNotification,
    val input: String,
    val tokens: List<SampleToken>,
    val marks: Map<Int, TokenRole>,
    val direction: NotificationTemplate.Direction,
    val selectedTokenIndex: Int? = null,
) {

    /**
     * @param payment what the template makes of the sample, null when it is not [TemplateBuilder.Result.Built]
     */
    class Analysis(
        val result: TemplateBuilder.Result,
        val payment: ParsedBankNotification.Payment?,
    )

    /**
     * Opens the role chooser of the token, closes it if it is open for this token already.
     */
    fun withTokenTapped(index: Int): TeachDraft =
        copy(selectedTokenIndex = index.takeIf { it != selectedTokenIndex })

    /**
     * Only a 4 to 6 digit number can be a card.
     */
    fun canBeCard(index: Int): Boolean {
        val token = tokens.getOrNull(index)
            ?: return false

        return token.kind == SampleToken.Kind.Number
                && token.text.length in CARD_DIGITS
                && token.text.all(Char::isDigit)
    }

    /**
     * Marks the token with the [role], null clears it. An amount, a currency and a card
     * are single, so the earlier one is moved. Closes the chooser.
     * A card mark on a token that can't be a card is ignored.
     */
    fun withRole(index: Int, role: TokenRole?): TeachDraft {
        if (index !in tokens.indices || role == TokenRole.Card && !canBeCard(index)) {
            return this
        }

        val newMarks = marks.toMutableMap()
        when (role) {
            null ->
                newMarks.remove(index)

            TokenRole.Amount,
            TokenRole.Currency,
            TokenRole.Card -> {
                newMarks.entries.removeAll { it.value == role }
                newMarks[index] = role
            }

            TokenRole.Payee,
            TokenRole.Varies ->
                newMarks[index] = role
        }

        return copy(
            marks = newMarks,
            selectedTokenIndex = null,
        )
    }

    fun withDirection(direction: NotificationTemplate.Direction): TeachDraft =
        copy(direction = direction)

    /**
     * The texts of the tokens marked with the [role], in the sample order.
     */
    fun markedTexts(role: TokenRole): List<String> =
        tokens.filter { marks[it.index] == role }.map(SampleToken::text)

    fun analyze(): Analysis {
        val result = TemplateBuilder.build(tokens, marks)
        val payment = (result as? TemplateBuilder.Result.Built)?.let { built ->
            TemplateMatcher.match(
                pattern = built.pattern,
                fields = built.fields,
                isIncoming = direction == NotificationTemplate.Direction.Incoming,
                title = sample.title,
                text = sample.text,
            )
        }

        return Analysis(result, payment)
    }

    /**
     * @return the template to save, null unless the marks build a valid one
     */
    fun toTemplate(
        id: String,
        sourcePackage: String,
        createdAt: LocalDateTime,
    ): NotificationTemplate? {
        val built = analyze().result as? TemplateBuilder.Result.Built
            ?: return null

        return NotificationTemplate(
            id = id,
            sourcePackage = sourcePackage,
            name = TemplateBuilder.defaultName(sample.title, tokens, marks),
            direction = direction,
            pattern = built.pattern,
            fields = built.fields,
            sampleText = input,
            isEnabled = true,
            createdAt = createdAt,
        )
    }

    companion object {
        private val CARD_DIGITS = 4..6

        /**
         * The amount and the currency are marked as [TemplateBuilder.detect] finds them.
         */
        fun of(sample: RecentNotification): TeachDraft {
            val input = SampleTokenizer.composeInput(sample.title, sample.text)
            val tokens = SampleTokenizer.tokenize(input)

            return TeachDraft(
                sample = sample,
                input = input,
                tokens = tokens,
                marks = TemplateBuilder.detect(tokens),
                direction = NotificationTemplate.Direction.Outgoing,
            )
        }
    }
}
