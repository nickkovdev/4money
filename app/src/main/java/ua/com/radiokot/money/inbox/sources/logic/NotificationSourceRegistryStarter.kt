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
package ua.com.radiokot.money.inbox.sources.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.scope.Scope
import ua.com.radiokot.money.auth.logic.UserSessionScopeListener
import ua.com.radiokot.money.lazyLogger

/**
 * Creates the [NotificationSourceRegistry] of every new session off the main thread,
 * so the cached active packages follow the templates (also the ones synced
 * from other devices) from the session start, not only once some screen
 * or a source notification needs the registry.
 */
class NotificationSourceRegistryStarter(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : UserSessionScopeListener {

    private val log by lazyLogger("NotificationSourceRegistryStarter")

    override fun onSessionScopeCreated(sessionScope: Scope) {
        coroutineScope.launch {
            // The scope may be closed by then (a quick sign-out).
            if (!sessionScope.isNotClosed()) {
                return@launch
            }

            runCatching { sessionScope.get<NotificationSourceRegistry>() }
                .onFailure { error ->
                    log.error(error) {
                        "onSessionScopeCreated(): failed to get the source registry"
                    }
                }
        }
    }
}
