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

package ua.com.radiokot.money.inbox.view

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.logic.originalAmountText

@Immutable
class ViewInboxItem(
    val title: String,
    val amountText: String?,
    val dateText: String,
    val isForeignCurrency: Boolean,
    val key: String,
    val source: InboxItem? = null,
) {
    /**
     * @param accountCurrencyCode currency of the item's account, if known
     */
    constructor(
        item: InboxItem,
        accountCurrencyCode: String?,
    ) : this(
        title = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?: item.rawText.lineSequence().last().take(120),
        amountText = item.originalAmountText().takeIf(String::isNotEmpty),
        dateText = item.receivedAt.toString().replace('T', ' ').take(16),
        isForeignCurrency = accountCurrencyCode != null
                && item.currencyCode != null
                && !item.currencyCode.equals(accountCurrencyCode, ignoreCase = true),
        key = item.id,
        source = item,
    )
}

@Immutable
class ViewCardAccountItem(
    val cardLast4: String,
    /**
     * Null if not mapped: the most used account is used.
     */
    val accountTitle: String?,
)
