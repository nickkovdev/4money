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

package ua.com.radiokot.money.inbox.ask

import androidx.appcompat.app.AppCompatDelegate
import android.os.LocaleList
import android.content.res.Configuration
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import ua.com.radiokot.money.R
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.view.InboxActivity
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.privacy.data.PrivacyPreferences
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * Posts the app's own "Fuelstop · −18.40 €" notification with up to 3 category buttons
 * for a payment left pending. A tap records it via [PaymentQuestionReceiver] without opening
 * the app; a dismissed notification leaves the item pending in the inbox.
 * Local reads only, no network.
 */
class PaymentQuestionNotifier(
    private val context: Context,
    private val inboxRepository: InboxRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transferHistoryRepository: TransferHistoryRepository,
    private val privacyPreferences: PrivacyPreferences,
) {
    private val log by lazyLogger("PaymentQuestionNotifier")

    suspend fun ask(
        itemId: String,
        reason: AutoExpenseResolver.PendingReason,
    ) {
        if (!PaymentQuestion.shouldAsk(reason)) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            log.debug {
                "ask(): skipping, notifications are not allowed"
            }
            return
        }

        val item = inboxRepository
            .getPendingItemsFlow()
            .first()
            .firstOrNull { it.id == itemId }
            ?: return
        val amount = item.amount
            ?: return
        val account = item.accountId
            ?.let { accountRepository.getAccount(it) }
            ?: return
        if (!item.currencyCode.equals(account.currency.code, ignoreCase = true)) {
            // A foreign amount needs converting in the app.
            return
        }

        val isIncoming = item.direction == InboxItem.Direction.Incoming
        val categories = categoryRepository
            .getCategories(isIncome = isIncoming)
            .filterNot { it.isArchived }
            .filter { it.currency == account.currency }
            .associateBy { it.id }
        val normalizedPayee = item.payee?.let(PayeeNormalizer::normalize).orEmpty()
        val history = runCatching {
            transferHistoryRepository
                .getTransferHistoryPage(
                    cursor = null,
                    limit = HISTORY_LIMIT,
                    withinPeriod = HistoryPeriod.Since70th,
                    counterpartyIds = null,
                )
                .data
        }.getOrDefault(emptyList())

        val suggestions = InboxCardSuggester.suggest(
            normalizedPayee = normalizedPayee,
            rules = payeeRuleRepository.getRules(),
            history = history.mapNotNull { transfer ->
                val categoryCounterparty =
                    (if (isIncoming) transfer.source else transfer.destination)
                            as? TransferCounterparty.Category
                        ?: return@mapNotNull null
                InboxCardSuggester.HistoryEntry(
                    normalizedMemo = transfer.memo?.let(PayeeNormalizer::normalize).orEmpty(),
                    category = InboxCardSuggester.CategoryKey(
                        categoryId = categoryCounterparty.category.id,
                        subcategoryId = categoryCounterparty.subcategory?.id,
                    ),
                )
            },
            isUsable = { key -> key.categoryId in categories },
            amount = amount,
        )
        val actionCategories = PaymentQuestion.actionCategories(suggestions)

        // Resolved now, not cached: a language change applies to the next notification.
        val localizedContext = getLocalizedContext()
        ensureChannel(localizedContext)

        val payee = item.payee?.let(PayeeNormalizer::displayName)?.takeIf(String::isNotEmpty)
            ?: localizedContext.getString(R.string.inbox_private_title)
        val sign = if (isIncoming) "+" else "−"
        val title = PaymentQuestion.notificationTitle(
            payee = payee,
            signedAmount = "$sign${formatAmount(amount, localizedContext)} ${account.currency.symbol}",
            isPrivate = privacyPreferences.isPrivacyModeEnabled.value,
        )
        val text = localizedContext.getString(
            if (reason == AutoExpenseResolver.PendingReason.AskRequested)
                R.string.ask_text_ask_range
            else
                R.string.ask_text_new_payee
        )
        val notificationId = notificationIdOf(item.id)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tabler_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent(context, InboxActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )

        actionCategories.forEachIndexed { index, key ->
            val category = categories[key.categoryId]
                ?: return@forEachIndexed
            val subcategoryTitle = key.subcategoryId
                ?.let { categoryRepository.getSubcategory(it) }
                ?.title
            builder.addAction(
                0,
                subcategoryTitle ?: category.title,
                PaymentQuestionReceiver.getPendingIntent(
                    context = context,
                    requestCode = notificationId * 4 + index,
                    itemId = item.id,
                    categoryId = key.categoryId,
                    subcategoryId = key.subcategoryId,
                    notificationId = notificationId,
                ),
            )
        }

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (error: SecurityException) {
            log.warn(error) {
                "ask(): no permission to post"
            }
        }
    }

    /**
     * On Android 13+ the framework applies the per-app language to the application context,
     * so it is always correct there. On API 26-32 [AppCompatDelegate.getApplicationLocales]
     * only returns the in-process value, set when an activity attaches or the language is changed;
     * in a cold process without an activity (e.g. started by the notification listener)
     * it is empty and the notification uses the device language.
     */
    private fun getLocalizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            return context
        }
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(configuration)
    }

    /**
     * Creating a channel again with the same ID updates its name and description,
     * but keeps the importance the user may have set.
     */
    private fun ensureChannel(localizedContext: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(R.string.ask_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = localizedContext.getString(R.string.ask_channel_description)
            }
        )
    }

    private fun formatAmount(amount: BigDecimal, localizedContext: Context): String =
        NumberFormat.getNumberInstance(localizedContext.resources.configuration.locales[0])
            .apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
                roundingMode = RoundingMode.HALF_UP
            }
            .format(amount.abs())

    companion object {
        const val CHANNEL_ID = "payments_to_sort"
        private const val HISTORY_LIMIT = 400

        fun notificationIdOf(itemId: String): Int =
            (itemId.hashCode() and 0x0FFFFFFF) or 0x10000000
    }
}
