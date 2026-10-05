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
import org.koin.core.component.inject
import ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION
import ua.com.radiokot.money.inbox.ask.PaymentQuestionNotifier
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase
import ua.com.radiokot.money.inbox.sources.data.ActiveNotificationsSource
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.inbox.templates.logic.MoneyTextHeuristic
import ua.com.radiokot.money.lazyLogger

/**
 * Bound by the system and called only on notifications:
 * no foreground service, no wakelock, no polling, no network.
 * A notification of an app that is not an active source costs one cheap regex
 * and, if it looks like money, one small file write on the IO dispatcher
 * (the recent buffer the source setup takes samples from).
 *
 * On connecting (after a reboot, an app update or granting the access)
 * the source notifications still shown are processed too, so payments notified
 * while the listener was not bound are not lost. Already processed ones
 * are duplicates by their dedup hash.
 */
class BankNotificationListenerService :
    NotificationListenerService(),
    KoinComponent {

    private val log by lazyLogger("BankNotificationListener")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val autoBookPreferences: AutoBookPreferences by inject()
    private val recentNotificationBuffer: RecentNotificationBuffer by inject()

    override fun onListenerConnected() {
        instance = this

        // Created early, so the active package cache follows the templates
        // synced from other devices even before a source notification comes.
        getKoin().getScopeOrNull(DI_SCOPE_SESSION)?.also { sessionScope ->
            runCatching { sessionScope.get<NotificationSourceRegistry>() }
                .onFailure { error ->
                    log.error(error) {
                        "onListenerConnected(): failed to get the source registry"
                    }
                }
        }

        val activeNotifications = try {
            activeNotifications
        } catch (error: SecurityException) {
            log.warn(error) {
                "onListenerConnected(): can't get active notifications"
            }
            return
        }

        // Not buffered: the source setup reads the shown ones live.
        activeNotifications
            ?.sortedBy(StatusBarNotification::getPostTime)
            ?.forEach { processNotification(it, isPosted = false) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) =
        processNotification(sbn, isPosted = true)

    /**
     * @param isPosted whether the notification has just been posted,
     * so it is kept in the recent buffer if it looks like money
     */
    private fun processNotification(
        sbn: StatusBarNotification,
        isPosted: Boolean,
    ) {
        val incoming = extract(sbn, ownPackageName = packageName)
            ?: return

        if (incoming.packageName in autoBookPreferences.getCachedActivePackages()) {
            processSourceNotification(incoming)
        }

        if (isPosted && looksLikeMoney(incoming)) {
            coroutineScope.launch {
                runCatching {
                    recentNotificationBuffer.add(incoming.toRecent())
                }.onFailure { error ->
                    log.error(error) {
                        "processNotification(): failed to buffer"
                    }
                }
            }
        }
    }

    private fun processSourceNotification(incoming: IncomingBankNotification) {
        val sessionScope = getKoin().getScopeOrNull(DI_SCOPE_SESSION)
        if (sessionScope == null) {
            log.debug {
                "processSourceNotification(): skipping, there is no session"
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
                                "processSourceNotification(): failed to ask"
                            }
                        }
                    }
                }
                .onFailure { error ->
                    // No text, payee, amount or card digits: the log is public.
                    log.error(error) {
                        "processSourceNotification(): failed to process:" +
                                "\npackageName=${incoming.packageName}"
                    }
                }
        }
    }

    override fun onListenerDisconnected() {
        clearInstance()
        clearBufferIfAccessRevoked()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        clearInstance()
        clearBufferIfAccessRevoked()
        coroutineScope.cancel()
        super.onDestroy()
    }

    private fun clearInstance() {
        if (instance === this) {
            instance = null
        }
    }

    private fun clearBufferIfAccessRevoked() {
        if (NotificationAccess.isGranted(this)) {
            return
        }

        runCatching(recentNotificationBuffer::clear)
            .onFailure { error ->
                log.error(error) {
                    "clearBufferIfAccessRevoked(): failed to clear"
                }
            }
    }

    /**
     * Reads the notifications shown, from the connected listener.
     */
    class ActiveNotifications : ActiveNotificationsSource {

        override fun getActive(): List<RecentNotification> {
            val listener = instance
                ?: return emptyList()

            val activeNotifications = try {
                listener.activeNotifications
            } catch (error: SecurityException) {
                return emptyList()
            } catch (error: RuntimeException) {
                // The listener may get unbound in between.
                return emptyList()
            }

            return activeNotifications
                .orEmpty()
                .mapNotNull { extract(it, ownPackageName = listener.packageName) }
                .filter(::looksLikeMoney)
                .sortedByDescending(IncomingBankNotification::postTimeMillis)
                .map { it.toRecent() }
        }
    }

    private companion object {
        const val IGNORED_FLAGS =
            Notification.FLAG_GROUP_SUMMARY or
                    Notification.FLAG_ONGOING_EVENT or
                    Notification.FLAG_FOREGROUND_SERVICE

        @Volatile
        var instance: BankNotificationListenerService? = null

        /**
         * @return the notification title and text, or null for own notifications,
         * summaries, ongoing status notifications and blank texts
         */
        fun extract(
            sbn: StatusBarNotification,
            ownPackageName: String,
        ): IncomingBankNotification? {
            if (sbn.packageName == ownPackageName) {
                return null
            }

            val notification = sbn.notification
                ?: return null
            // Summaries and ongoing status notifications are not payments.
            if (notification.flags and IGNORED_FLAGS != 0) {
                return null
            }

            val extras = notification.extras
                ?: return null
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?: return null

            return IncomingBankNotification(
                packageName = sbn.packageName,
                postTimeMillis = sbn.postTime,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = text,
            )
        }

        fun looksLikeMoney(notification: IncomingBankNotification): Boolean =
            MoneyTextHeuristic.looksLikeMoney(
                listOfNotNull(notification.title, notification.text).joinToString(" ")
            )

        fun IncomingBankNotification.toRecent() = RecentNotification(
            packageName = packageName,
            postTimeMillis = postTimeMillis,
            title = title,
            text = text,
        )
    }
}
