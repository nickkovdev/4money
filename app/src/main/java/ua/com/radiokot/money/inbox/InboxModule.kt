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

package ua.com.radiokot.money.inbox

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.money.auth.logic.sessionScope
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.CardAccountPreferencesOnPrefs
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.data.PowerSyncInboxRepository
import ua.com.radiokot.money.inbox.data.PowerSyncMostUsedAccountSource
import ua.com.radiokot.money.inbox.data.PowerSyncPayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.CardAccountResolver
import ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCase
import ua.com.radiokot.money.inbox.logic.DefaultCardAccountResolver
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase
import ua.com.radiokot.money.inbox.logic.SebLatviaNotificationParser
import ua.com.radiokot.money.transfers.transfersModule

val inboxModule = module {
    includes(
        transfersModule,
    )

    single {
        CardAccountPreferencesOnPrefs(
            preferences = androidContext().getSharedPreferences(
                "bank_cards",
                Context.MODE_PRIVATE,
            )
        )
    } bind CardAccountPreferences::class

    sessionScope {

        scoped {
            PowerSyncInboxRepository(
                database = get(),
            )
        } bind InboxRepository::class

        scoped {
            PowerSyncPayeeRuleRepository(
                database = get(),
            )
        } bind PayeeRuleRepository::class

        scoped {
            PowerSyncMostUsedAccountSource(
                database = get(),
            )
        } bind MostUsedAccountSource::class

        scoped {
            DefaultCardAccountResolver(
                cardAccountPreferences = get(),
                mostUsedAccountSource = get(),
            )
        } bind CardAccountResolver::class

        // Scoped, not factory: the instance mutex must be shared.
        scoped {
            ProcessBankNotificationUseCase(
                parsers = listOf(
                    SebLatviaNotificationParser(),
                ),
                inboxRepository = get(),
                payeeRuleRepository = get(),
                accountRepository = get(),
                categoryRepository = get(),
                cardAccountResolver = get(),
                transferFundsUseCase = get(),
            )
        } bind ProcessBankNotificationUseCase::class

        factory {
            CompleteInboxItemUseCase(
                inboxRepository = get(),
                payeeRuleRepository = get(),
            )
        } bind CompleteInboxItemUseCase::class
    }
}
