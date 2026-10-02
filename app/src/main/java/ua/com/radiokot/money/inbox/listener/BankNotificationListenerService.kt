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
 */
class BankNotificationListenerService :
    NotificationListenerService(),
    KoinComponent {

    private val log by lazyLogger("BankNotificationListener")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in BankNotificationSources.packageNames) {
            return
        }

        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
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
                "onNotificationPosted(): skipping, there is no session"
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
                }
                .onFailure { error ->
                    log.error(error) {
                        "onNotificationPosted(): failed to process:" +
                                "\nincoming=$incoming"
                    }
                }
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}
