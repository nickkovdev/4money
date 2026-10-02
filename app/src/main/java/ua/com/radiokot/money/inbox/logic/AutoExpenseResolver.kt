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

package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Decides whether a parsed payment can become an expense
 * (or an income, for an incoming payment) without the user.
 */
object AutoExpenseResolver {

    data class AccountRef(
        val id: String,
        val currencyCode: String,
        val precision: Int,
    )

    data class CategoryRef(
        val categoryId: String,
        val subcategoryId: String?,
        val currencyCode: String,
        val precision: Int,
        val isIncome: Boolean = false,
    )

    enum class PendingReason {
        NotParsed,
        NoRule,
        NoAccount,
        ForeignCurrency,
        CategoryMissing,
        CategoryDirectionMismatch,
        CategoryCurrencyMismatch,
        UnsupportedPrecision,

        /**
         * The matched rule asks the user (e.g. an amount range that can be either).
         */
        AskRequested,
    }

    sealed interface Resolution {

        data class Create(
            val rule: PayeeRule,
            val account: AccountRef,
            val category: CategoryRef,
            /**
             * The payment amount in the account currency minor units.
             */
            val accountAmount: BigInteger,
            /**
             * The payment amount in the category currency minor units.
             */
            val categoryAmount: BigInteger,
        ) : Resolution

        data class Pending(
            val reason: PendingReason,
        ) : Resolution
    }

    /**
     * @param payment null if the notification was not recognized
     * @param rule matched rule, if any
     * @param account resolved, existing, non-archived account, if any
     * @param category resolved, existing, non-archived rule category, if any;
     * an income one for an incoming payment, an expense one otherwise
     */
    fun resolve(
        payment: ParsedBankNotification.Payment?,
        rule: PayeeRule?,
        account: AccountRef?,
        category: CategoryRef?,
    ): Resolution {
        if (payment == null || payment.amount.signum() <= 0) {
            return Resolution.Pending(PendingReason.NotParsed)
        }
        if (rule == null) {
            return Resolution.Pending(PendingReason.NoRule)
        }
        if (rule.action == PayeeRule.Action.Ask) {
            return Resolution.Pending(PendingReason.AskRequested)
        }
        if (account == null) {
            return Resolution.Pending(PendingReason.NoAccount)
        }
        if (!payment.currencyCode.equals(account.currencyCode, ignoreCase = true)) {
            return Resolution.Pending(PendingReason.ForeignCurrency)
        }
        if (category == null) {
            return Resolution.Pending(PendingReason.CategoryMissing)
        }
        if (category.isIncome != payment.isIncoming) {
            return Resolution.Pending(PendingReason.CategoryDirectionMismatch)
        }
        if (!category.currencyCode.equals(account.currencyCode, ignoreCase = true)) {
            return Resolution.Pending(PendingReason.CategoryCurrencyMismatch)
        }

        val accountAmount = toMinorUnits(payment.amount, account.precision)
        val categoryAmount = toMinorUnits(payment.amount, category.precision)
        if (accountAmount == null || categoryAmount == null) {
            return Resolution.Pending(PendingReason.UnsupportedPrecision)
        }

        return Resolution.Create(
            rule = rule,
            account = account,
            category = category,
            accountAmount = accountAmount,
            categoryAmount = categoryAmount,
        )
    }

    /**
     * @return [amount] in minor units of a currency with [precision],
     * or null if it has more decimals than the currency allows.
     */
    fun toMinorUnits(
        amount: BigDecimal,
        precision: Int,
    ): BigInteger? =
        try {
            amount.movePointRight(precision).toBigIntegerExact()
        } catch (_: ArithmeticException) {
            null
        }
}
