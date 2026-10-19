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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.logic.BankNotificationParsing
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Turns a bank notification into an expense or an income (rule matched) or a pending inbox item.
 * Local writes only. Bind as a single instance per session: [mutex] serializes
 * the dedup check and the writes for notifications arriving at once.
 */
class ProcessBankNotificationUseCase(
    private val parsing: BankNotificationParsing,
    private val inboxRepository: InboxRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val cardAccountResolver: CardAccountResolver,
    private val transferFundsUseCase: TransferFundsUseCase,
    // Resolved per notification: the switches may change while the process lives.
    private val behaviour: () -> AutoBookBehaviour,
    // Resolved per call: the listener process may outlive a time zone change.
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    sealed interface Outcome {
        data object Ignored : Outcome
        data object Duplicate : Outcome

        data class AutoRecorded(
            val itemId: String,
            val transferId: String,
            val ruleId: String,
        ) : Outcome

        data class Pending(
            val itemId: String,
            val reason: AutoExpenseResolver.PendingReason,
        ) : Outcome
    }

    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        notification: IncomingBankNotification,
    ): Result<Outcome> = runCatching {

        val parsed = parsing.parse(notification.packageName, notification.title, notification.text)
            ?: return@runCatching Outcome.Ignored

        val payment = parsed as? ParsedBankNotification.Payment
        val dedupHash = BankNotificationDedupHash.compute(
            notification = notification,
            includePostTime = payment == null || !payment.hasTimestamp,
        )

        mutex.withLock {
            if (inboxRepository.existsWithDedupHash(dedupHash)) {
                return@withLock Outcome.Duplicate
            }

            val receivedAt = Instant
                .fromEpochMilliseconds(notification.postTimeMillis)
                .toLocalDateTime(timeZone())

            val accountsById = accountRepository
                .getAccounts()
                .filterNot(Account::isArchived)
                .associateBy(Account::id)

            val rule: PayeeRule? = payment
                ?.payee
                ?.let(PayeeNormalizer::normalize)
                ?.let { normalizedPayee ->
                    PayeeRuleMatcher.match(
                        normalizedPayee = normalizedPayee,
                        rules = getRulesOfDirection(isIncome = payment.isIncoming),
                        amount = payment.amount,
                    )
                }

            val account: Account? = cardAccountResolver
                .resolve(
                    cardLast4 = payment?.cardLast4,
                    ruleAccountId = rule?.accountId,
                    usableAccountIds = accountsById.keys,
                    sourcePackage = notification.packageName,
                )
                ?.let(accountsById::get)

            val resolution = AutoExpenseResolver.resolve(
                payment = payment,
                rule = rule,
                account = account?.let { acc ->
                    AutoExpenseResolver.AccountRef(
                        id = acc.id,
                        currencyCode = acc.currency.code,
                        precision = acc.currency.precision,
                    )
                },
                category = rule?.let { getCategoryRef(it) },
                recordKnownPayees = behaviour().recordKnownPayees,
            )

            val item = InboxItem(
                id = newId(),
                receivedAt = receivedAt,
                sourcePackage = notification.packageName,
                rawText = listOfNotNull(notification.title, notification.text).joinToString("\n"),
                amount = payment?.amount,
                currencyCode = payment?.currencyCode,
                payee = payment?.payee,
                cardLast4 = payment?.cardLast4,
                accountId = account?.id,
                status = InboxItem.Status.Pending,
                transferId = null,
                dedupHash = dedupHash,
                direction =
                    if (payment?.isIncoming == true)
                        InboxItem.Direction.Incoming
                    else
                        InboxItem.Direction.Outgoing,
            )

            when (resolution) {
                is AutoExpenseResolver.Resolution.Create -> {
                    val transferId = newId()

                    // The pending item is written first: it is the dedup marker.
                    // If the transfer or the process fails afterwards, a re-post is
                    // a duplicate instead of a second expense, and the item stays
                    // pending in the inbox for the user to resolve. The failure is
                    // returned to the caller as is.
                    inboxRepository.addItem(item)

                    // A separate PowerSync transaction on purpose: the connector uploads
                    // a transaction containing a transfer through the `transfer` RPC
                    // and drops its other rows, so the inbox item must not share it.
                    val accountId = TransferCounterpartyId.Account(resolution.account.id)
                    val categoryId = TransferCounterpartyId.Category(
                        categoryId = resolution.category.categoryId,
                        subcategoryId = resolution.category.subcategoryId,
                    )
                    val isIncoming = requireNotNull(payment).isIncoming

                    // An income goes from the category to the account.
                    transferFundsUseCase(
                        sourceId = if (isIncoming) categoryId else accountId,
                        sourceAmount =
                            if (isIncoming)
                                resolution.categoryAmount
                            else
                                resolution.accountAmount,
                        destinationId = if (isIncoming) accountId else categoryId,
                        destinationAmount =
                            if (isIncoming)
                                resolution.accountAmount
                            else
                                resolution.categoryAmount,
                        memo = PayeeNormalizer.displayName(payment.payee),
                        dateTime = receivedAt,
                        transferId = transferId,
                    ).getOrThrow()

                    inboxRepository.markDone(item.id, transferId)
                    payeeRuleRepository.recordHit(resolution.rule.id, receivedAt)

                    Outcome.AutoRecorded(
                        itemId = item.id,
                        transferId = transferId,
                        ruleId = resolution.rule.id,
                    )
                }

                is AutoExpenseResolver.Resolution.Pending -> {
                    inboxRepository.addItem(item)

                    Outcome.Pending(
                        itemId = item.id,
                        reason = resolution.reason,
                    )
                }
            }
        }
    }

    /**
     * Rules whose category is of the given direction, so an expense rule
     * never records an income from the same payee (e.g. a refund) and vice versa.
     * Rules with a missing category are kept for the resolver to report.
     */
    private suspend fun getRulesOfDirection(isIncome: Boolean): List<PayeeRule> =
        payeeRuleRepository
            .getRules()
            .filter { rule ->
                // Ask rules have no category, they apply to both directions.
                val category = rule.categoryId?.let { categoryRepository.getCategory(it) }
                category == null || category.isIncome == isIncome
            }

    private suspend fun getCategoryRef(rule: PayeeRule): AutoExpenseResolver.CategoryRef? {
        val category = categoryRepository
            .getCategory(rule.categoryId ?: return null)
            ?.takeUnless { it.isArchived }
            ?: return null

        // A deleted or archived subcategory falls back to the parent category.
        val subcategoryId = rule.subcategoryId
            ?.takeIf {
                categoryRepository.getSubcategory(it)
                    ?.let { s -> s.categoryId == category.id && !s.isArchived } == true
            }

        return AutoExpenseResolver.CategoryRef(
            categoryId = category.id,
            subcategoryId = subcategoryId,
            currencyCode = category.currency.code,
            precision = category.currency.precision,
            isIncome = category.isIncome,
        )
    }
}
