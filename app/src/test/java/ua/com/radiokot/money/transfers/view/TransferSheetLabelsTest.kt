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

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.currency.view.ViewCurrency

class TransferSheetLabelsTest {

    private val scheme = ItemColorScheme(name = "Red2", primary = 0xFFF1B6B7, onPrimary = 0xFFAC2B2E)
    private val usd = ViewCurrency(symbol = "$", precision = 2)
    private val account = ViewTransferCounterparty.Account(
        accountTitle = "Card",
        currency = usd,
        colorScheme = scheme,
        icon = null,
    )
    private val category = ViewTransferCounterparty.Category(
        categoryTitle = "Food",
        subcategoryTitle = null,
        currency = usd,
        colorScheme = scheme,
        icon = null,
    )

    @Test
    fun kind() {
        Assert.assertEquals(TransferKind.Expense, transferKindOf(source = account, destination = category))
        Assert.assertEquals(TransferKind.Income, transferKindOf(source = category, destination = account))
        Assert.assertEquals(TransferKind.Transfer, transferKindOf(source = account, destination = account))
        Assert.assertEquals("Expense", TransferKind.Expense.label)
        Assert.assertEquals("Income", TransferKind.Income.label)
        Assert.assertEquals("Transfer", TransferKind.Transfer.label)
    }

    @Test
    fun halfLabels() {
        Assert.assertEquals("From account", counterpartyHalfLabel(isSource = true, counterparty = account))
        Assert.assertEquals("To category", counterpartyHalfLabel(isSource = false, counterparty = category))
        Assert.assertEquals("From category", counterpartyHalfLabel(isSource = true, counterparty = category))
        Assert.assertEquals("To account", counterpartyHalfLabel(isSource = false, counterparty = account))
    }
}
