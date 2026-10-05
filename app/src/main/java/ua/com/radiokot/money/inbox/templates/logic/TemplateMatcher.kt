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

package ua.com.radiokot.money.inbox.templates.logic

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.TemplateFields

/**
 * Parses notifications with user templates built by [TemplateBuilder]
 * (or synced from another device, so the pattern is never trusted).
 */
object TemplateMatcher {

    private const val CACHE_SIZE = 32

    // Longer texts are not bank notifications; this also bounds backtracking time.
    private const val MAX_INPUT_LENGTH = 4096
    private val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val whitespace = Regex("\\s+")

    // Compiled patterns by source, LRU; null for a pattern that doesn't compile.
    private val cache = object : LinkedHashMap<String, Regex?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>?): Boolean =
            size > CACHE_SIZE
    }

    /**
     * Matches the whole `title + "\n" + text` ([SampleTokenizer.composeInput]) against the [pattern].
     *
     * @param fields capture group numbers of the [pattern]
     *
     * @return the payment, or null when the template does not match or the captured
     * amount, currency or payee are not valid, or the input is longer than 4096 chars;
     * never throws
     */
    fun match(
        pattern: String,
        fields: TemplateFields,
        isIncoming: Boolean,
        title: String?,
        text: String,
    ): ParsedBankNotification.Payment? = try {
        SampleTokenizer.composeInput(title, text)
            .takeIf { it.length <= MAX_INPUT_LENGTH }
            ?.let { input -> compile(pattern)?.matchEntire(input) }
            ?.let { result -> toPayment(result, fields, isIncoming) }
    } catch (e: Exception) {
        null
    } catch (e: StackOverflowError) {
        // A pathological synced pattern.
        null
    }

    /**
     * @see match
     */
    fun match(
        template: NotificationTemplate,
        title: String?,
        text: String,
    ): ParsedBankNotification.Payment? =
        match(
            pattern = template.pattern,
            fields = template.fields,
            isIncoming = template.direction == NotificationTemplate.Direction.Incoming,
            title = title,
            text = text,
        )

    private fun toPayment(
        result: MatchResult,
        fields: TemplateFields,
        isIncoming: Boolean,
    ): ParsedBankNotification.Payment? {
        // Group 0 is the whole text, never a field.
        if (fields.amount < 1 || fields.currency < 1 || fields.payee < 1) {
            return null
        }

        val amount = result.groups[fields.amount]
            ?.value
            ?.let(TemplateAmounts::parseAmount)
            ?: return null

        val currencyCode = result.groups[fields.currency]
            ?.value
            ?.let(TemplateAmounts::parseCurrency)
            ?: return null

        val payee = result.groups[fields.payee]
            ?.value
            ?.replace(whitespace, " ")
            ?.trim()
            ?.trimEnd('.', ' ')
            ?.takeIf(String::isNotEmpty)
            ?: return null

        val cardLast4 = fields.card
            ?.takeIf { it >= 1 }
            ?.let { result.groups[it] }
            ?.value
            ?.takeIf { it.length >= 4 && it.all(Char::isDigit) }
            ?.takeLast(4)

        return ParsedBankNotification.Payment(
            amount = amount,
            currencyCode = currencyCode,
            cardLast4 = cardLast4,
            payee = payee,
            isIncoming = isIncoming,
            hasTimestamp = fields.hasTimestamp,
        )
    }

    private fun compile(pattern: String): Regex? = synchronized(cache) {
        if (cache.containsKey(pattern)) {
            cache[pattern]
        } else {
            val regex =
                try {
                    Regex(pattern, options)
                } catch (e: Exception) {
                    null
                }
            cache[pattern] = regex
            regex
        }
    }
}
