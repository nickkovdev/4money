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

package ua.com.radiokot.money.privacy.logic

import java.math.BigInteger

/**
 * How an amount is shown while the privacy mode is on.
 */
sealed interface PrivateAmountDisplay {
    /**
     * A headline/absolute number: hidden completely.
     */
    data object Mask : PrivateAmountDisplay

    /**
     * Shown as a percentage of the [total], "—" when the total is unknown or zero.
     */
    data class ShareOf(val total: BigInteger?) : PrivateAmountDisplay
}

/**
 * Privacy mode texts and decisions. Pure.
 */
object PrivacyAmounts {
    const val MASK = "•••"
    const val NO_SHARE = "—"
    private val HUNDRED = BigInteger.valueOf(100)

    // BigInteger.TWO needs API 33.
    private val TWO = BigInteger.valueOf(2)

    /**
     * @return |[part]| of |[total]| in percent rounded half-up, null for a missing or zero total.
     */
    fun sharePercent(part: BigInteger, total: BigInteger?): Int? {
        val whole = total?.abs()
            ?.takeIf { it.signum() != 0 }
            ?: return null
        return ((part.abs() * HUNDRED + whole / TWO) / whole).toInt()
    }

    fun shareText(part: BigInteger, total: BigInteger?): String {
        val percent = sharePercent(part, total)
            ?: return NO_SHARE
        return if (percent == 0 && part.signum() != 0)
            "<1%"
        else
            "$percent%"
    }

    /**
     * @return share for a progress bar, within 0..1, 0 for a missing or zero total.
     */
    fun shareFraction(part: BigInteger, total: BigInteger?): Float {
        val whole = total?.abs()
            ?.takeIf { it.signum() != 0 }
            ?: return 0f
        return (part.abs().toDouble() / whole.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun textOf(value: BigInteger, display: PrivateAmountDisplay): String = when (display) {
        PrivateAmountDisplay.Mask -> MASK
        is PrivateAmountDisplay.ShareOf -> shareText(value, display.total)
    }

    /**
     * A transaction row: share of the period total of its direction,
     * masked for transfers between accounts and amounts not in the totals currency.
     */
    fun forTransfer(
        isExpense: Boolean,
        isIncome: Boolean,
        isInTotalsCurrency: Boolean,
        expenseTotal: BigInteger?,
        incomeTotal: BigInteger?,
    ): PrivateAmountDisplay = when {
        !isInTotalsCurrency -> PrivateAmountDisplay.Mask
        isExpense -> PrivateAmountDisplay.ShareOf(expenseTotal)
        isIncome -> PrivateAmountDisplay.ShareOf(incomeTotal)
        else -> PrivateAmountDisplay.Mask
    }
}
