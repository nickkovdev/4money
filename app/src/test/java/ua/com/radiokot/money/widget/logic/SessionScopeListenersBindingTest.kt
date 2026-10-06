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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.junit.Test
import ua.com.radiokot.money.auth.logic.UserSessionScopeListener
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistryStarter

/**
 * Both root singles bound to [UserSessionScopeListener] must be returned
 * by getAll, as the session holder does.
 */
class SessionScopeListenersBindingTest {

    @Test
    fun allListenersAreReturned() {
        val koin = koinApplication {
            modules(
                module {
                    single { NotificationSourceRegistryStarter() } bind UserSessionScopeListener::class
                    single { HomeWidgetBadgeStarter(updater = {}) } bind UserSessionScopeListener::class
                }
            )
        }.koin

        val listeners = koin.getAll<UserSessionScopeListener>()

        assertEquals(2, listeners.size)
        assertTrue(listeners.any { it is NotificationSourceRegistryStarter })
        assertTrue(listeners.any { it is HomeWidgetBadgeStarter })
    }
}
