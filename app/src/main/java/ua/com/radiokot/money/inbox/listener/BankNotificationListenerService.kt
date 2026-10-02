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

package ua.com.radiokot.money.inbox.listener

import ua.com.radiokot.money.inbox.ask.PaymentQuestionNotifier
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.logic.BankNotificationSources
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase
import ua.com.radiokot.money.lazyLogger

/**
 * Bound by the system and called only on notifications:
 * no foreground service, no wakelock, no polling, no network.
 * Everything not from a source bank returns before any other work.
 *
 * On connecting (after a reboot, an app update or granting the access)
 * the notifications still shown are processed too, so payments notified
 * while the listener was not bound are not lost. Already processed ones
 * are duplicates by their dedup hash.
 */
class BankNotificationListenerService :
    NotificationListenerService(),
    KoinComponent {

    private val log by lazyLogger("BankNotificationListener")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        val activeNotifications = try {
            activeNotifications
        } catch (error: SecurityException) {
            log.warn(error) {
                "onListenerConnected(): can't get active notifications"
            }
            return
        }

        activeNotifications
            ?.sortedBy(StatusBarNotification::getPostTime)
            ?.forEach(::processNotification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) =
        processNotification(sbn)

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName !in BankNotificationSources.packageNames) {
            return
        }

        val notification = sbn.notification
        // Summaries and ongoing status notifications are not payments.
        if (notification.flags and IGNORED_FLAGS != 0) {
            return
        }

        val extras = notification.extras
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: return

        val incoming = IncomingBankNotification(
            packageName = sbn.packageName,
            postTimeMillis = sbn.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = text,
        )

        val sessionScope = getKoin().getScopeOrNull(DI_SCOPE_SESSION)
        if (sessionScope == null) {
            log.debug {
                "processNotification(): skipping, there is no session"
            }
            return
        }

        coroutineScope.launch {
            runCatching {
                sessionScope
                    .get<ProcessBankNotificationUseCase>()
                    .invoke(incoming)
                    .getOrThrow()
            }
                .onSuccess { outcome ->
                    log.info {
                        "Bank notification processed: $outcome"
                    }

                    if (outcome is ProcessBankNotificationUseCase.Outcome.Pending) {
                        runCatching {
                            sessionScope
                                .get<PaymentQuestionNotifier>()
                                .ask(
                                    itemId = outcome.itemId,
                                    reason = outcome.reason,
                                )
                        }.onFailure { error ->
                            log.error(error) {
                                "processNotification(): failed to ask"
                            }
                        }
                    }
                }
                .onFailure { error ->
                    // No text, payee, amount or card digits: the log is public.
                    log.error(error) {
                        "processNotification(): failed to process:" +
                                "\npackageName=${incoming.packageName}"
                    }
                }
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val IGNORED_FLAGS =
            Notification.FLAG_GROUP_SUMMARY or
                    Notification.FLAG_ONGOING_EVENT or
                    Notification.FLAG_FOREGROUND_SERVICE
    }
}
