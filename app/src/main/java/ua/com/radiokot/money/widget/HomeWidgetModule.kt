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

package ua.com.radiokot.money.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import ua.com.radiokot.money.auth.logic.UserSessionScopeListener
import ua.com.radiokot.money.auth.logic.sessionScope
import ua.com.radiokot.money.inbox.inboxModule
import ua.com.radiokot.money.widget.logic.GlanceHomeWidgetUpdater
import ua.com.radiokot.money.widget.logic.HomeWidgetBadgeStarter
import ua.com.radiokot.money.widget.logic.HomeWidgetUpdater

const val HOME_WIDGET_COROUTINE_SCOPE = "home-widget"

val homeWidgetModule = module {

    includes(
        inboxModule,
    )

    single {
        GlanceHomeWidgetUpdater(
            context = androidContext(),
        )
    } bind HomeWidgetUpdater::class

    single {
        HomeWidgetBadgeStarter(
            updater = get(),
        )
    } bind UserSessionScopeListener::class

    sessionScope {

        // Cancelled when the session ends, stopping the pending count collection.
        scoped(named(HOME_WIDGET_COROUTINE_SCOPE)) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        } onClose { it?.cancel() }
    }
}
