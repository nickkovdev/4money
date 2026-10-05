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

package ua.com.radiokot.money.inbox.sources.view.setup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ua.com.radiokot.money.R
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.templates.logic.TemplateBuilder
import ua.com.radiokot.money.inbox.templates.logic.TokenRole
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * The text color of a marked word and the tint behind it.
 */
internal class RoleStyle(
    val content: Color,
    val background: Color,
)

/**
 * Every role has its own tint from the theme tokens, the amount follows the expense color.
 */
@Composable
internal fun TokenRole.style(): RoleStyle {
    val colors = MoneyTheme.colors

    return when (this) {
        TokenRole.Amount -> RoleStyle(colors.expense, colors.expenseTint)
        TokenRole.Currency -> RoleStyle(colors.accent, colors.accentTint)
        TokenRole.Payee -> RoleStyle(colors.income, colors.incomeTint)
        TokenRole.Card -> RoleStyle(colors.warning, colors.warning.copy(alpha = 0.16f))
        TokenRole.Varies -> RoleStyle(colors.ink2, colors.line)
    }
}

@StringRes
internal fun TokenRole.labelRes(): Int = when (this) {
    TokenRole.Amount -> R.string.setup_role_amount
    TokenRole.Currency -> R.string.setup_role_currency
    TokenRole.Payee -> R.string.setup_role_payee
    TokenRole.Card -> R.string.setup_role_card
    TokenRole.Varies -> R.string.setup_role_varies
}

@StringRes
internal fun TemplateBuilder.Problem.hintRes(): Int = when (this) {
    TemplateBuilder.Problem.MissingAmount -> R.string.setup_problem_missing_amount
    TemplateBuilder.Problem.MissingCurrency -> R.string.setup_problem_missing_currency
    TemplateBuilder.Problem.MissingPayee -> R.string.setup_problem_missing_payee
    TemplateBuilder.Problem.PayeeNotContiguous -> R.string.setup_problem_payee_not_contiguous
    TemplateBuilder.Problem.SeveralAmounts -> R.string.setup_problem_several_amounts
    TemplateBuilder.Problem.SeveralCurrencies -> R.string.setup_problem_several_currencies
    TemplateBuilder.Problem.SeveralCards -> R.string.setup_problem_several_cards
    TemplateBuilder.Problem.PayeeNextToVaries -> R.string.setup_problem_payee_next_to_varies
    TemplateBuilder.Problem.SampleDoesNotMatch -> R.string.setup_problem_sample_does_not_match
}

/**
 * The bank amount as an app amount, signed by the direction:
 * 3.40 EUR → −3.40 € for an expense, 3.40 € for an income.
 */
internal fun viewAmountOfPayment(payment: ParsedBankNotification.Payment): ViewAmount {
    val javaCurrency = runCatching {
        java.util.Currency.getInstance(payment.currencyCode.uppercase())
    }.getOrNull()
    val precision = javaCurrency
        ?.defaultFractionDigits
        ?.takeIf { it >= 0 }
        ?: 2
    val minorUnits = payment.amount
        .movePointRight(precision)
        .setScale(0, java.math.RoundingMode.HALF_UP)
        .toBigIntegerExact()
        .abs()

    return ViewAmount(
        value =
            if (payment.isIncoming)
                minorUnits
            else
                minorUnits.negate(),
        currency = ViewCurrency(
            symbol = javaCurrency?.symbol ?: payment.currencyCode,
            precision = precision,
        ),
    )
}
