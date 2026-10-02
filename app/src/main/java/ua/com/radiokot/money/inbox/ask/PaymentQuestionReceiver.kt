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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCase
import ua.com.radiokot.money.inbox.logic.InboxCardAcceptance
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import java.util.UUID

/**
 * Records a pending payment to the category tapped in the "Payments to sort" notification.
 * Local write only, the upload happens with the next sync.
 */
class PaymentQuestionReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val log by lazyLogger("PaymentQuestionReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
            ?: return
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
            ?: return
        val subcategoryId = intent.getStringExtra(EXTRA_SUBCATEGORY_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val sessionScope = getKoin().getScopeOrNull(DI_SCOPE_SESSION)
        if (sessionScope == null) {
            log.debug {
                "onReceive(): skipping, there is no session"
            }
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = sessionScope.get<InboxRepository>()
                    .getPendingItemsFlow()
                    .first()
                    .firstOrNull { it.id == itemId }
                    ?: return@launch

                val account = item.accountId
                    ?.let { sessionScope.get<AccountRepository>().getAccount(it) }
                    ?: return@launch
                val categoryRepository = sessionScope.get<CategoryRepository>()
                val category = categoryRepository.getCategory(categoryId)
                    ?: return@launch
                val subcategory = subcategoryId?.let { categoryRepository.getSubcategory(it) }

                val decision = InboxCardAcceptance.decide(item, account, category, subcategory)
                        as? InboxCardAcceptance.Decision.Record
                    ?: return@launch

                val transferId = UUID.randomUUID().toString()
                sessionScope.get<TransferFundsUseCase>()
                    .invoke(
                        sourceId = decision.sourceId,
                        sourceAmount = decision.sourceAmount,
                        destinationId = decision.destinationId,
                        destinationAmount = decision.destinationAmount,
                        memo = decision.memo,
                        dateTime = item.receivedAt,
                        transferId = transferId,
                    )
                    .getOrThrow()

                sessionScope.get<CompleteInboxItemUseCase>()
                    .invoke(
                        itemId = item.id,
                        transferId = transferId,
                        rememberPayeePattern = null,
                        sourceId = decision.sourceId,
                        destinationId = decision.destinationId,
                    )
                    .getOrThrow()

                NotificationManagerCompat.from(context).cancel(notificationId)
                log.info {
                    "Recorded a payment from the notification"
                }
            } catch (error: Exception) {
                // No payee or amount: the log is public.
                log.error(error) {
                    "onReceive(): failed to record"
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_RECORD = "ua.com.radiokot.money.action.RECORD_PAYMENT"
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_CATEGORY_ID = "category_id"
        private const val EXTRA_SUBCATEGORY_ID = "subcategory_id"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun getPendingIntent(
            context: Context,
            requestCode: Int,
            itemId: String,
            categoryId: String,
            subcategoryId: String?,
            notificationId: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, PaymentQuestionReceiver::class.java)
                    .setAction(ACTION_RECORD)
                    .putExtra(EXTRA_ITEM_ID, itemId)
                    .putExtra(EXTRA_CATEGORY_ID, categoryId)
                    .putExtra(EXTRA_SUBCATEGORY_ID, subcategoryId)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
