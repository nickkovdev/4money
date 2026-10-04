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

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * A token of a notification sample the user can mark.
 *
 * @param index position in the token list
 * @param start offset into the normalized sample
 * @param end exclusive offset into the normalized sample
 * @param spaceBefore whether there is whitespace (incl. "\n") between the previous token and this one
 */
data class SampleToken(
    val index: Int,
    val text: String,
    val start: Int,
    val end: Int,
    val kind: Kind,
    val spaceBefore: Boolean,
) {
    enum class Kind {
        Word,
        Amount,
        Number,
        Date,
        Time,
        Currency,
        Punctuation,
    }
}

object SampleTokenizer {

    private val datePattern = Pattern.compile("\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}(?!\\d)")
    private val timePattern = Pattern.compile("\\d{1,2}:\\d{2}(?::\\d{2})?(?!\\d)")

    // Unlike TemplateAmounts.AMOUNT_PATTERN, a plain integer is not an amount but a Number.
    // A space only joins groups of exactly 3 digits.
    private val amountPattern = Pattern.compile(
        "(?:\\d{1,3}(?:[ .,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+[.,]\\d{1,2})(?!\\d)"
    )
    private val numberPattern = Pattern.compile("\\d+")
    private val wordPattern = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M}\\d'’-]*")
    private val punctuationPattern = Pattern.compile(
        "[^\\s\\p{L}\\p{M}\\d${TemplateAmounts.CURRENCY_SYMBOLS}]+"
    )

    private val kindPatterns: List<Pair<SampleToken.Kind, Pattern>> = listOf(
        SampleToken.Kind.Date to datePattern,
        SampleToken.Kind.Time to timePattern,
        SampleToken.Kind.Amount to amountPattern,
        SampleToken.Kind.Number to numberPattern,
    )

    /**
     * NFC, any space character (NBSP, U+202F, …) → space, invisible format characters
     * (bidi marks, zero-width spaces) removed, trimmed.
     * Used for samples and for matching.
     */
    fun normalize(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)

        return buildString(nfc.length) {
            nfc.forEach { char ->
                when {
                    Character.getType(char) == Character.FORMAT.toInt() ->
                        Unit

                    char != '\n' && Character.isSpaceChar(char) ->
                        append(' ')

                    else ->
                        append(char)
                }
            }
        }.trim()
    }

    /**
     * @return title (or "") + "\n" + text, [normalize]d:
     * the input of templates, both when built and when matched.
     */
    fun composeInput(title: String?, text: String): String =
        normalize((title ?: "") + "\n" + text)

    /**
     * Splits a [normalize]d sample into words, amounts, numbers, dates, times, currencies
     * and punctuation runs. Whitespace is not a token but sets [SampleToken.spaceBefore].
     */
    fun tokenize(normalizedSample: String): List<SampleToken> {
        val tokens = mutableListOf<SampleToken>()
        val matcher = numberPattern.matcher(normalizedSample)
        val length = normalizedSample.length
        var position = 0
        var spaceBefore = false

        while (position < length) {
            if (normalizedSample[position].isRegexSpace()) {
                spaceBefore = true
                position++
                continue
            }

            val (kind, end) = readToken(normalizedSample, position, matcher)
            tokens += SampleToken(
                index = tokens.size,
                text = normalizedSample.substring(position, end),
                start = position,
                end = end,
                kind = kind,
                spaceBefore = spaceBefore && tokens.isNotEmpty(),
            )
            position = end
            spaceBefore = false
        }

        return tokens
    }

    private fun readToken(
        text: String,
        start: Int,
        matcher: java.util.regex.Matcher,
    ): Pair<SampleToken.Kind, Int> {
        fun lookingAt(pattern: Pattern): Int? {
            matcher.usePattern(pattern)
            matcher.region(start, text.length)
            return if (matcher.lookingAt()) matcher.end() else null
        }

        if (text[start].isDigit()) {
            kindPatterns.forEach { (kind, pattern) ->
                lookingAt(pattern)?.also { end ->
                    return kind to end
                }
            }
        }

        if (text[start] in TemplateAmounts.CURRENCY_SYMBOLS) {
            return SampleToken.Kind.Currency to start + 1
        }

        lookingAt(wordPattern)?.also { wordEnd ->
            val word = text.substring(start, wordEnd)
            val leadingCode = word.take(3)
            val isLeadingCode = word.length >= 3
                    && leadingCode.all { it in 'A'..'Z' }
                    && TemplateAmounts.isIsoCode(leadingCode)

            return when {
                // "EUR12,50": the code glued to the amount.
                isLeadingCode && word.length > 3 && word[3].isDigit() ->
                    SampleToken.Kind.Currency to start + 3

                isLeadingCode && word.length == 3 ->
                    SampleToken.Kind.Currency to wordEnd

                else ->
                    SampleToken.Kind.Word to wordEnd
            }
        }

        lookingAt(punctuationPattern)?.also { end ->
            return SampleToken.Kind.Punctuation to end
        }

        // Not reachable for normalized text, but never loop forever.
        return SampleToken.Kind.Punctuation to text.offsetByCodePoints(start, 1)
    }

    // The whitespace of java.util.regex "\s", which separates tokens in templates.
    private fun Char.isRegexSpace(): Boolean =
        this == ' ' || this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' || this == '\r'
}
