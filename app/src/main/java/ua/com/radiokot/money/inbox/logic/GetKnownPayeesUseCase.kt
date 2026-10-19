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

import io.github.oshai.kotlinlogging.KotlinLogging
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository

/**
 * Loads the payees the app already knows a category for: Record rules
 * and memos of the recent transfers. Used to suggest which words of a payee to remember.
 */
class GetKnownPayeesUseCase(
    private val payeeRuleRepository: PayeeRuleRepository,
    private val transferHistoryRepository: TransferHistoryRepository,
) {
    private val log = KotlinLogging.logger("GetKnownPayees")

    /**
     * @param knownPayees payee-to-category pairs
     * @param rules all the payee rules, to avoid re-pointing one by a default selection
     */
    data class KnownPayeeData(
        val knownPayees: List<PayeeRulePatternSuggester.KnownPayee>,
        val rules: List<PayeeRule>,
    )

    suspend operator fun invoke(): KnownPayeeData {
        val rules = payeeRuleRepository.getRules()

        val history = runCatching {
            transferHistoryRepository
                .getTransferHistoryPage(
                    cursor = null,
                    limit = HISTORY_LIMIT,
                    withinPeriod = HistoryPeriod.Since70th,
                    counterpartyIds = null,
                )
                .data
        }
            .onFailure { error ->
                log.warn(error) { "invoke(): failed to load the history, using the rules only" }
            }
            .getOrDefault(emptyList())
            .mapNotNull { transfer ->
                val category =
                    (transfer.destination as? TransferCounterparty.Category)?.category
                        ?: (transfer.source as? TransferCounterparty.Category)?.category
                category?.let {
                    InboxCardSuggester.HistoryEntry(
                        normalizedMemo = transfer.memo
                            ?.let(PayeeNormalizer::normalize)
                            .orEmpty(),
                        category = InboxCardSuggester.CategoryKey(
                            categoryId = category.id,
                            subcategoryId = null,
                        ),
                    )
                }
            }

        return KnownPayeeData(
            knownPayees = PayeeRulePatternSuggester.knownPayees(
                rules = rules,
                history = history,
            ),
            rules = rules,
        )
    }

    private companion object {
        const val HISTORY_LIMIT = 400
    }
}
