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

package ua.com.radiokot.money.widget.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import ua.com.radiokot.money.auth.logic.UserSessionScopeListener
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.widget.HOME_WIDGET_COROUTINE_SCOPE

/**
 * Keeps the widget badge in sync with the pending inbox count of every new session
 * without polling: the count is a PowerSync watch that only emits on table changes.
 * Closing the session scope (sign-out) redraws the widget without the badge.
 */
class HomeWidgetBadgeStarter(
    private val updater: HomeWidgetUpdater,
) : UserSessionScopeListener {

    private val log by lazyLogger("HomeWidgetBadgeStarter")

    override fun onSessionScopeCreated(sessionScope: Scope) {
        sessionScope.registerCallback(object : ScopeCallback {
            override fun onScopeClose(scope: Scope) = updater.requestUpdate()
        })

        val coroutineScope = sessionScope.get<CoroutineScope>(named(HOME_WIDGET_COROUTINE_SCOPE))

        // Off the main thread: getting the repository touches the database.
        coroutineScope.launch {
            // The scope may be closed by then (a quick sign-out).
            if (!sessionScope.isNotClosed()) {
                return@launch
            }

            runCatching { sessionScope.get<InboxRepository>() }
                .onFailure { error ->
                    log.error(error) {
                        "onSessionScopeCreated(): failed to get the inbox repository"
                    }
                }
                .getOrNull()
                ?.getPendingCountFlow()
                ?.distinctUntilChanged()
                ?.catch { error ->
                    log.error(error) {
                        "onSessionScopeCreated(): pending count flow failed"
                    }
                }
                ?.collect { updater.requestUpdate() }
        }
    }
}
