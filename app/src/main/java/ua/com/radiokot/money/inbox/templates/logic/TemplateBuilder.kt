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

import ua.com.radiokot.money.inbox.templates.data.TemplateFields

/**
 * What a token marked by the user is.
 * [Varies] is any text that changes between notifications, e.g. a payment purpose.
 */
enum class TokenRole {
    Amount,
    Currency,
    Payee,
    Card,
    Varies,
}

/**
 * Turns a tokenized sample and the user's marks into a template regex,
 * matched by [TemplateMatcher] with IGNORE_CASE and DOT_MATCHES_ALL.
 */
object TemplateBuilder {

    private const val DATE_PATTERN = "\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}"
    private const val TIME_PATTERN = "\\d{1,2}:\\d{2}(?::\\d{2})?"
    private const val SIGNED_AMOUNT_GROUP = "([-\u2212+]?(?:${TemplateAmounts.AMOUNT_PATTERN}))"
    private const val CURRENCY_GROUP = "(${TemplateAmounts.CURRENCY_PATTERN})"
    private const val CARD_GROUP = "(\\d{2,6})"
    private const val PAYEE_GROUP = "(.+?)"
    private const val VARIES = ".+?"
    private const val REGEX_META_CHARS = "\\.[]{}()*+?^$|"
    private const val MAX_NAME_LENGTH = 32
    private val signs = setOf("-", "\u2212", "+")

    /**
     * Finds the payment amount and its currency on the sample:
     * the first amount right before or after a currency (a sign may stand in between).
     * A plain integer is only taken when there is no such decimal or grouped amount.
     *
     * @return the amount and currency token indices, or an empty map
     */
    fun detect(tokens: List<SampleToken>): Map<Int, TokenRole> {
        fun isCurrency(position: Int): Boolean =
            tokens.getOrNull(position)?.kind == SampleToken.Kind.Currency

        fun currencyNear(position: Int): Int? = when {
            isCurrency(position + 1) ->
                position + 1

            isCurrency(position - 1) ->
                position - 1

            tokens.getOrNull(position - 1)?.text in signs && isCurrency(position - 2) ->
                position - 2

            else ->
                null
        }

        listOf(SampleToken.Kind.Amount, SampleToken.Kind.Number).forEach { amountKind ->
            tokens.forEachIndexed { position, token ->
                if (token.kind == amountKind && TemplateAmounts.parseAmount(token.text) != null) {
                    val currencyPosition = currencyNear(position)
                    if (currencyPosition != null) {
                        return mapOf(
                            token.index to TokenRole.Amount,
                            tokens[currencyPosition].index to TokenRole.Currency,
                        )
                    }
                }
            }
        }

        return emptyMap()
    }

    sealed interface Result {
        data class Built(val pattern: String, val fields: TemplateFields) : Result
        data class Invalid(val problem: Problem) : Result
    }

    /**
     * Checked in this order.
     * A mark on a token that can't be an amount (or a currency) counts as a missing one.
     */
    enum class Problem {
        MissingAmount,
        MissingCurrency,
        MissingPayee,
        PayeeNotContiguous,
        SeveralAmounts,
        SeveralCurrencies,
        SeveralCards,
    }

    /**
     * Builds the template regex: `^\s*` + tokens + `\s*$`, tokens separated by `\s+` where
     * the sample has whitespace and by `\s*` where it has none.
     * Unmarked words and punctuation are literal; unmarked numbers, amounts, dates, times
     * and currencies are wildcards. The only capture groups are the marked amount, currency,
     * card and payee (a contiguous run, lazy); a "varies" run is a lazy wildcard.
     *
     * @param marks roles by [SampleToken.index]
     */
    fun build(tokens: List<SampleToken>, marks: Map<Int, TokenRole>): Result {
        val positionsByRole: Map<TokenRole, List<Int>> = tokens
            .mapIndexedNotNull { position, token -> marks[token.index]?.let { it to position } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        fun positionsOf(role: TokenRole) = positionsByRole[role].orEmpty()

        val amounts = positionsOf(TokenRole.Amount)
        val currencies = positionsOf(TokenRole.Currency)
        val payees = positionsOf(TokenRole.Payee)

        val problem = when {
            amounts.isEmpty()
                    || amounts.size == 1 && TemplateAmounts.parseAmount(tokens[amounts[0]].text) == null ->
                Problem.MissingAmount

            currencies.isEmpty()
                    || currencies.size == 1 && TemplateAmounts.parseCurrency(tokens[currencies[0]].text) == null ->
                Problem.MissingCurrency

            payees.isEmpty() ->
                Problem.MissingPayee

            payees.last() - payees.first() + 1 != payees.size ->
                Problem.PayeeNotContiguous

            amounts.size > 1 ->
                Problem.SeveralAmounts

            currencies.size > 1 ->
                Problem.SeveralCurrencies

            positionsOf(TokenRole.Card).size > 1 ->
                Problem.SeveralCards

            else ->
                null
        }
        if (problem != null) {
            return Result.Invalid(problem)
        }

        val pattern = StringBuilder("^\\s*")
        var groupCount = 0
        var amountGroup = 0
        var currencyGroup = 0
        var payeeGroup = 0
        var cardGroup: Int? = null

        var position = 0
        while (position < tokens.size) {
            val token = tokens[position]
            val role = marks[token.index]

            if (position > 0) {
                pattern.append(if (token.spaceBefore) "\\s+" else "\\s*")
            }

            if (role == TokenRole.Payee || role == TokenRole.Varies) {
                // Adjacent tokens of the same mark are one run, inner spaces included.
                while (position + 1 < tokens.size && marks[tokens[position + 1].index] == role) {
                    position++
                }
                if (role == TokenRole.Payee) {
                    payeeGroup = ++groupCount
                    pattern.append(PAYEE_GROUP)
                } else {
                    pattern.append(VARIES)
                }
                position++
                continue
            }

            when (role) {
                TokenRole.Amount -> {
                    amountGroup = ++groupCount
                    pattern.append(SIGNED_AMOUNT_GROUP)
                }

                TokenRole.Currency -> {
                    currencyGroup = ++groupCount
                    pattern.append(CURRENCY_GROUP)
                }

                TokenRole.Card -> {
                    cardGroup = ++groupCount
                    pattern.append(CARD_GROUP)
                }

                else ->
                    pattern.append(unmarkedTokenPattern(token))
            }
            position++
        }

        pattern.append("\\s*$")

        return Result.Built(
            pattern = pattern.toString(),
            fields = TemplateFields(
                amount = amountGroup,
                currency = currencyGroup,
                payee = payeeGroup,
                card = cardGroup,
                hasTimestamp = tokens.any { it.kind == SampleToken.Kind.Time },
            ),
        )
    }

    /**
     * @return the sample title, or else the first 3 unmarked words of the sample,
     * at most 32 chars
     */
    fun defaultName(
        title: String?,
        tokens: List<SampleToken>,
        marks: Map<Int, TokenRole>,
    ): String {
        val trimmedTitle = title?.trim()
        val name =
            if (!trimmedTitle.isNullOrEmpty())
                trimmedTitle
            else
                tokens
                    .filter { it.kind == SampleToken.Kind.Word && marks[it.index] == null }
                    .take(3)
                    .joinToString(" ", transform = SampleToken::text)

        return name.take(MAX_NAME_LENGTH).trimEnd()
    }

    private fun unmarkedTokenPattern(token: SampleToken): String = when (token.kind) {
        // A Number may be an integer amount (a balance) that has decimals next time.
        SampleToken.Kind.Amount,
        SampleToken.Kind.Number ->
            "(?:${TemplateAmounts.AMOUNT_PATTERN})"

        SampleToken.Kind.Date ->
            DATE_PATTERN

        SampleToken.Kind.Time ->
            TIME_PATTERN

        // E.g. the balance currency follows the account, not the sample.
        SampleToken.Kind.Currency ->
            TemplateAmounts.CURRENCY_PATTERN

        SampleToken.Kind.Word,
        SampleToken.Kind.Punctuation ->
            escapeLiteral(token.text)
    }

    // Escaping each metachar keeps the pattern readable, unlike \Q…\E.
    private fun escapeLiteral(text: String): String = buildString(text.length * 2) {
        text.forEach { char ->
            if (char in REGEX_META_CHARS) {
                append('\\')
            }
            append(char)
        }
    }
}
