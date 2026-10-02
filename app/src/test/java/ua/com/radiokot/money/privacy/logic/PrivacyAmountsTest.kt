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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

class PrivacyAmountsTest {
    private fun b(v: Long) = BigInteger.valueOf(v)

    @Test
    fun `share rounds half up`() {
        assertEquals(33, PrivacyAmounts.sharePercent(b(1), b(3)))
        assertEquals(67, PrivacyAmounts.sharePercent(b(2), b(3)))
        assertEquals(50, PrivacyAmounts.sharePercent(b(1), b(2)))
        assertEquals(1, PrivacyAmounts.sharePercent(b(5), b(1000)))
        assertEquals("33%", PrivacyAmounts.shareText(b(1), b(3)))
    }

    @Test
    fun `zero total`() {
        assertNull(PrivacyAmounts.sharePercent(b(5), b(0)))
        assertNull(PrivacyAmounts.sharePercent(b(5), null))
        assertEquals("—", PrivacyAmounts.shareText(b(5), b(0)))
        assertEquals("—", PrivacyAmounts.shareText(b(0), null))
        assertEquals(0f, PrivacyAmounts.shareFraction(b(5), b(0)))
    }

    @Test
    fun `tiny and zero parts`() {
        assertEquals("<1%", PrivacyAmounts.shareText(b(1), b(1000)))
        assertEquals("0%", PrivacyAmounts.shareText(b(0), b(1000)))
    }

    @Test
    fun `uses absolute values and no cap`() {
        assertEquals("25%", PrivacyAmounts.shareText(b(-25), b(100)))
        assertEquals("150%", PrivacyAmounts.shareText(b(150), b(100)))
        assertEquals(0.25f, PrivacyAmounts.shareFraction(b(-25), b(100)))
        assertEquals(1f, PrivacyAmounts.shareFraction(b(150), b(100)))
    }

    @Test
    fun `mask ignores sign`() {
        assertEquals("•••", PrivacyAmounts.textOf(b(-1234), PrivateAmountDisplay.Mask))
        assertEquals("•••", PrivacyAmounts.textOf(b(0), PrivateAmountDisplay.Mask))
        assertEquals("10%", PrivacyAmounts.textOf(b(10), PrivateAmountDisplay.ShareOf(b(100))))
        assertEquals("—", PrivacyAmounts.textOf(b(10), PrivateAmountDisplay.ShareOf(null)))
    }

    @Test
    fun `transfer display`() {
        assertEquals(
            PrivateAmountDisplay.ShareOf(b(200)),
            PrivacyAmounts.forTransfer(isExpense = true, isIncome = false, isInTotalsCurrency = true, expenseTotal = b(200), incomeTotal = b(900)),
        )
        assertEquals(
            PrivateAmountDisplay.ShareOf(b(900)),
            PrivacyAmounts.forTransfer(isExpense = false, isIncome = true, isInTotalsCurrency = true, expenseTotal = b(200), incomeTotal = b(900)),
        )
        // Account to account.
        assertEquals(
            PrivateAmountDisplay.Mask,
            PrivacyAmounts.forTransfer(isExpense = false, isIncome = false, isInTotalsCurrency = true, expenseTotal = b(200), incomeTotal = b(900)),
        )
        // Foreign currency row.
        assertEquals(
            PrivateAmountDisplay.Mask,
            PrivacyAmounts.forTransfer(isExpense = true, isIncome = false, isInTotalsCurrency = false, expenseTotal = b(200), incomeTotal = b(900)),
        )
        // Totals not loaded yet → share of nothing → "—".
        assertEquals(
            PrivateAmountDisplay.ShareOf(null),
            PrivacyAmounts.forTransfer(isExpense = true, isIncome = false, isInTotalsCurrency = true, expenseTotal = null, incomeTotal = null),
        )
    }
}
