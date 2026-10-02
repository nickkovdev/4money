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

enum class TransferKind {
    Expense,
    Income,
    Transfer,
    ;

    val label: String
        get() = when (this) {
            Expense -> "Expense"
            Income -> "Income"
            Transfer -> "Transfer"
        }
}

/**
 * Income comes from an income category, expense goes to a category,
 * everything else is a transfer between accounts.
 */
fun transferKindOf(
    source: ViewTransferCounterparty,
    destination: ViewTransferCounterparty,
): TransferKind = when {
    source is ViewTransferCounterparty.Category -> TransferKind.Income
    destination is ViewTransferCounterparty.Category -> TransferKind.Expense
    else -> TransferKind.Transfer
}

fun counterpartyHalfLabel(
    isSource: Boolean,
    counterparty: ViewTransferCounterparty,
): String =
    (if (isSource) "From " else "To ") +
            (if (counterparty is ViewTransferCounterparty.Category) "category" else "account")
