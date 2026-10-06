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

import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.transfers.data.Transfer
import ua.com.radiokot.money.transfers.data.TransferCounterparty

/**
 * Everything needed to suggest a category for inbox items,
 * shared by the card stack and the Inbox tab.
 */
class InboxSuggestionLookup(
    val accountsById: Map<String, Account>,
    val categoriesById: Map<String, Category>,
    val subcategoriesById: Map<String, Subcategory>,
    val rules: List<PayeeRule>,
    history: List<Transfer>,
) {
    private val outgoingHistory: List<InboxCardSuggester.HistoryEntry> by lazy {
        historyEntries(history, isIncoming = false)
    }
    private val incomingHistory: List<InboxCardSuggester.HistoryEntry> by lazy {
        historyEntries(history, isIncoming = true)
    }

    /**
     * Rules and recent history of both directions, as payee-to-category pairs.
     */
    val knownPayees: List<PayeeRulePatternSuggester.KnownPayee> by lazy {
        PayeeRulePatternSuggester.knownPayees(
            rules = rules,
            history = outgoingHistory + incomingHistory,
        )
    }

    /**
     * @return the words of the payee of [item] to remember by default when sorting it
     * into [categoryId], null if the item has no payee.
     */
    fun defaultRememberSelection(
        item: InboxItem,
        categoryId: String,
    ): PayeeWordSelection? {
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return PayeeRulePatternSuggester.defaultSelection(
            normalizedPayee = normalizedPayee,
            categoryId = categoryId,
            known = knownPayees,
        )
    }

    fun suggest(
        item: InboxItem,
        useHistory: Boolean,
    ): InboxCardSuggester.Result {
        val isIncoming = item.direction == InboxItem.Direction.Incoming
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            .orEmpty()
        val rulesOfDirection = rules.filter { rule ->
            val category = rule.categoryId?.let(categoriesById::get)
            category == null || category.isIncome == isIncoming
        }

        return InboxCardSuggester.suggest(
            normalizedPayee = normalizedPayee,
            rules = rulesOfDirection,
            history = if (isIncoming) incomingHistory else outgoingHistory,
            amount = item.amount,
            useHistory = useHistory,
            mapKey = { key ->
                ArchivedSubcategoryFallback.dropArchived(key) { id ->
                    subcategoriesById[id]?.isArchived == true
                }
            },
            isUsable = { key ->
                val category = categoriesById[key.categoryId]
                category != null
                        && !category.isArchived
                        && category.isIncome == isIncoming
                        && (key.subcategoryId == null
                        || subcategoriesById[key.subcategoryId]?.categoryId == category.id)
            },
        )
    }

    private companion object {
        fun historyEntries(
            history: List<Transfer>,
            isIncoming: Boolean,
        ): List<InboxCardSuggester.HistoryEntry> =
            history.mapNotNull { transfer ->
                val categoryCounterparty =
                    if (isIncoming)
                        transfer.source as? TransferCounterparty.Category
                    else
                        transfer.destination as? TransferCounterparty.Category
                categoryCounterparty
                    ?.takeIf { it.category.isIncome == isIncoming }
                    ?.let { counterparty ->
                        InboxCardSuggester.HistoryEntry(
                            normalizedMemo = transfer.memo
                                ?.let(PayeeNormalizer::normalize)
                                .orEmpty(),
                            category = InboxCardSuggester.CategoryKey(
                                categoryId = counterparty.category.id,
                                subcategoryId = counterparty.subcategory?.id,
                            ),
                        )
                    }
            }
    }
}
