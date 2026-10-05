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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import java.util.UUID

/**
 * Sorts a pending inbox item to a category: records the transfer, completes the item,
 * and optionally remembers the payee. Shared by the card stack and the Inbox tab.
 */
class AcceptInboxSuggestionUseCase(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
    private val cardAccountResolver: CardAccountResolver,
    private val transferFundsUseCase: TransferFundsUseCase,
    private val completeInboxItemUseCase: CompleteInboxItemUseCase,
) {
    private val log = KotlinLogging.logger("AcceptInboxSuggestion")

    /**
     * The account the item is paid from or received to, null if there is none usable.
     */
    suspend fun resolveAccount(item: InboxItem): Account? {
        val usableAccounts = accountRepository
            .getAccounts()
            .filterNot(Account::isArchived)
        val accountId = item.accountId
            ?.takeIf { id -> usableAccounts.any { it.id == id } }
            ?: cardAccountResolver.resolve(
                cardLast4 = item.cardLast4,
                ruleAccountId = null,
                usableAccountIds = usableAccounts.mapTo(mutableSetOf(), Account::id),
                sourcePackage = item.sourcePackage,
            )

        return usableAccounts
            .firstOrNull { it.id == accountId }
            .also { account ->
                if (account == null) {
                    log.warn { "resolveAccount(): no account to pay from or receive to" }
                }
            }
    }

    /**
     * @param remember whether to learn an exact rule for the payee of the item
     */
    suspend operator fun invoke(
        item: InboxItem,
        categoryId: String,
        subcategoryId: String?,
        remember: Boolean,
    ): Result {
        val account = resolveAccount(item)
        val category = categoryRepository.getCategory(categoryId)
        val subcategory = subcategoryId?.let { categoryRepository.getSubcategory(it) }

        if (account == null || category == null) {
            return Result.NoAccountOrCategory
        }

        val decision = InboxCardAcceptance.decide(item, account, category, subcategory)
        if (decision is InboxCardAcceptance.Decision.OpenSheet) {
            return Result.OpenSheet(decision.route)
        }
        decision as InboxCardAcceptance.Decision.Record

        val transferId = UUID.randomUUID().toString()
        val rememberPattern = item.payee
            ?.let(PayeeNormalizer::normalize)
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { remember }
        val rulesBefore = payeeRuleRepository.getRules()
        var learnedRuleId: String? = null

        log.debug { "invoke(): recording the item, remember=${rememberPattern != null}" }

        return transferFundsUseCase(
            sourceId = decision.sourceId,
            sourceAmount = decision.sourceAmount,
            destinationId = decision.destinationId,
            destinationAmount = decision.destinationAmount,
            memo = decision.memo,
            dateTime = item.receivedAt,
            transferId = transferId,
        )
            .onSuccess {
                completeInboxItemUseCase(
                    itemId = item.id,
                    transferId = transferId,
                    rememberPayeePattern = rememberPattern,
                    sourceId = decision.sourceId,
                    destinationId = decision.destinationId,
                ).onFailure { error ->
                    log.error(error) { "invoke(): failed to complete the item" }
                }

                if (rememberPattern != null) {
                    // The rule cache is refreshed by the database watch, wait for it a bit.
                    val rulesAfter = withTimeoutOrNull(RULES_REFRESH_TIMEOUT_MS) {
                        payeeRuleRepository
                            .getRulesFlow()
                            .first { rules ->
                                rules.any { rule ->
                                    rule.payeePattern == rememberPattern
                                            && rule.matchType == PayeeRule.MatchType.Exact
                                            && rule.amountRange == null
                                }
                            }
                    } ?: payeeRuleRepository.getRules()
                    learnedRuleId = LearnedRule.createdRuleId(
                        rulesBefore = rulesBefore,
                        rulesAfter = rulesAfter,
                        payeePattern = rememberPattern,
                    )
                }

                recordRuleHitIfFollowed(item, categoryId)
            }
            .fold(
                onSuccess = {
                    Result.Recorded(
                        item = item.copy(
                            status = InboxItem.Status.Done,
                            transferId = transferId,
                        ),
                        categoryTitle = category.title,
                        learnedRuleId = learnedRuleId,
                    )
                },
                onFailure = { error ->
                    log.error(error) { "invoke(): failed to record" }
                    Result.Failed(error)
                },
            )
    }

    /**
     * Removes a rule learned by [invoke], for an undo.
     * Only a rule an accept created is removed, never an older one.
     */
    suspend fun forgetLearnedRule(ruleId: String) {
        payeeRuleRepository.deleteRule(ruleId)
    }

    private suspend fun recordRuleHitIfFollowed(
        item: InboxItem,
        categoryId: String,
    ) {
        val normalizedPayee = item.payee
            ?.let(PayeeNormalizer::normalize)
            ?: return
        val rule = PayeeRuleMatcher.match(
            normalizedPayee = normalizedPayee,
            rules = payeeRuleRepository.getRules(),
            amount = item.amount,
        ) ?: return
        if (rule.categoryId == categoryId) {
            payeeRuleRepository.recordHit(rule.id, item.receivedAt)
        }
    }

    sealed interface Result {

        class Recorded(
            /** The item as it is now: done, with the transfer. */
            val item: InboxItem,
            val categoryTitle: String,
            /** The rule this accept created, if any. */
            val learnedRuleId: String?,
        ) : Result

        /**
         * The transfer needs the sheet (e.g. a foreign currency); the item stays pending
         * until the sheet completes it.
         */
        class OpenSheet(val route: TransferSheetRoute) : Result

        object NoAccountOrCategory : Result

        class Failed(val error: Throwable) : Result
    }

    private companion object {
        const val RULES_REFRESH_TIMEOUT_MS = 3000L
    }
}
