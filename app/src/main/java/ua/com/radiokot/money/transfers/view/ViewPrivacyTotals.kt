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

package ua.com.radiokot.money.transfers.view

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.currency.view.ViewCurrency
import java.math.BigInteger

/**
 * Whole-period expense and income totals in the primary currency,
 * the denominators of the transaction shares shown in the privacy mode.
 */
@Immutable
class ViewPrivacyTotals(
    val currency: ViewCurrency,
    val expense: BigInteger,
    val income: BigInteger,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewPrivacyTotals) return false

        if (currency != other.currency) return false
        if (expense != other.expense) return false
        if (income != other.income) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currency.hashCode()
        result = 31 * result + expense.hashCode()
        result = 31 * result + income.hashCode()
        return result
    }
}
