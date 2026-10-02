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

package ua.com.radiokot.money.inbox.data

import java.math.BigDecimal

sealed interface ParsedBankNotification {

    /**
     * @param amount positive decimal amount
     * @param currencyCode upper-case ISO 4217 code
     * @param cardLast4 last 4 card digits, if the text has them
     * @param payee merchant name as written by the bank (possibly truncated), trimmed
     */
    data class CardPayment(
        val amount: BigDecimal,
        val currencyCode: String,
        val cardLast4: String?,
        val payee: String,
    ) : ParsedBankNotification

    /**
     * A notification of a source bank matching no known template.
     */
    data object Unrecognized : ParsedBankNotification
}
