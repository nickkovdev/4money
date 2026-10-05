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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import ua.com.radiokot.money.auth.logic.sessionScope
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.CardAccountPreferencesOnPrefs
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.data.PowerSyncInboxRepository
import ua.com.radiokot.money.inbox.data.PowerSyncMostUsedAccountSource
import ua.com.radiokot.money.inbox.data.PowerSyncPayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.AcceptInboxSuggestionUseCase
import ua.com.radiokot.money.inbox.logic.CardAccountResolver
import ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCase
import ua.com.radiokot.money.inbox.logic.DefaultCardAccountResolver
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase
import ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase
import ua.com.radiokot.money.inbox.ask.PaymentQuestionNotifier
import ua.com.radiokot.money.inbox.listener.BankNotificationListenerService
import ua.com.radiokot.money.inbox.sources.data.ActiveNotificationsSource
import ua.com.radiokot.money.inbox.sources.data.AndroidAppInfoSource
import ua.com.radiokot.money.inbox.sources.data.AppInfoSource
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferencesOnPrefs
import ua.com.radiokot.money.inbox.sources.data.FileRecentNotificationBuffer
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.sources.logic.BankNotificationParsing
import ua.com.radiokot.money.inbox.sources.logic.BuiltInPresets
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplateRepository
import ua.com.radiokot.money.inbox.templates.data.PowerSyncNotificationTemplateRepository
import ua.com.radiokot.money.inbox.sources.view.CardAccountsScreenViewModel
import ua.com.radiokot.money.inbox.sources.view.SourcesScreenViewModel
import ua.com.radiokot.money.inbox.sources.view.setup.SourceSetupViewModel
import ua.com.radiokot.money.inbox.sources.view.TestTextScreenViewModel
import ua.com.radiokot.money.inbox.view.InboxCardsViewModel
import ua.com.radiokot.money.inbox.view.InboxScreenViewModel
import ua.com.radiokot.money.inbox.view.RulesScreenViewModel
import ua.com.radiokot.money.transfers.history.data.TransferHistoryRepository
import ua.com.radiokot.money.transfers.transfersModule
import java.io.File

private const val SOURCES_COROUTINE_SCOPE = "notification-sources"

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

    single {
        AutoBookPreferencesOnPrefs(
            preferences = androidContext().getSharedPreferences(
                "autobook",
                Context.MODE_PRIVATE,
            ),
            presetPackageNames = BuiltInPresets.all.map { it.packageName },
        )
    } bind AutoBookPreferences::class

    single {
        FileRecentNotificationBuffer(
            file = File(androidContext().noBackupFilesDir, "recent_money_notifications.json"),
        )
    } bind RecentNotificationBuffer::class

    single {
        BankNotificationListenerService.ActiveNotifications()
    } bind ActiveNotificationsSource::class

    single {
        AndroidAppInfoSource(
            context = androidContext(),
        )
    } bind AppInfoSource::class

    sessionScope {

        // Cancelled when the session ends, stopping the registry's collection.
        scoped(named(SOURCES_COROUTINE_SCOPE)) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        } onClose { it?.cancel() }

        scoped {
            NotificationSourceRegistry(
                templateRepository = get(),
                autoBookPreferences = get(),
                scope = get(named(SOURCES_COROUTINE_SCOPE)),
            )
        } bind BankNotificationParsing::class

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
            PowerSyncNotificationTemplateRepository(
                database = get(),
            )
        } bind NotificationTemplateRepository::class

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
                parsing = get(),
                inboxRepository = get(),
                payeeRuleRepository = get(),
                accountRepository = get(),
                categoryRepository = get(),
                cardAccountResolver = get(),
                transferFundsUseCase = get(),
                behaviour = {
                    val preferences = get<AutoBookPreferences>()
                    AutoBookBehaviour(
                        recordKnownPayees = preferences.isRecordKnownPayeesEnabled,
                        askInNotification = preferences.isAskInNotificationEnabled,
                        learnFromHistory = preferences.isLearnFromHistoryEnabled,
                    )
                },
            )
        } bind ProcessBankNotificationUseCase::class

        scoped {
            PaymentQuestionNotifier(
                context = androidContext(),
                inboxRepository = get(),
                payeeRuleRepository = get(),
                accountRepository = get(),
                categoryRepository = get(),
                transferHistoryRepository = get(),
                privacyPreferences = get(),
                autoBookPreferences = get(),
            )
        } bind PaymentQuestionNotifier::class

        factory {
            CompleteInboxItemUseCase(
                inboxRepository = get(),
                payeeRuleRepository = get(),
            )
        } bind CompleteInboxItemUseCase::class

        factory {
            AcceptInboxSuggestionUseCase(
                accountRepository = get(),
                categoryRepository = get(),
                payeeRuleRepository = get(),
                cardAccountResolver = get(),
                transferFundsUseCase = get(),
                completeInboxItemUseCase = get(),
            )
        } bind AcceptInboxSuggestionUseCase::class

        factory {
            UndoInboxItemUseCase(
                inboxRepository = get(),
                revertTransferUseCase = get(),
                transferExists = { transferId ->
                    get<TransferHistoryRepository>().getTransferOrNull(transferId) != null
                },
            )
        } bind UndoInboxItemUseCase::class

        viewModel {
            InboxScreenViewModel(
                inboxRepository = get(),
                accountRepository = get(),
                categoryRepository = get(),
                payeeRuleRepository = get(),
                transferHistoryRepository = get(),
                undoInboxItemUseCase = get(),
                acceptInboxSuggestionUseCase = get(),
                autoBookPreferences = get(),
            )
        } bind InboxScreenViewModel::class

        viewModel {
            InboxCardsViewModel(
                inboxRepository = get(),
                payeeRuleRepository = get(),
                accountRepository = get(),
                categoryRepository = get(),
                transferHistoryRepository = get(),
                acceptInboxSuggestionUseCase = get(),
                undoInboxItemUseCase = get(),
                autoBookPreferences = get(),
            )
        } bind InboxCardsViewModel::class

        viewModel {
            RulesScreenViewModel(
                payeeRuleRepository = get(),
                categoryRepository = get(),
            )
        } bind RulesScreenViewModel::class

        viewModel {
            SourcesScreenViewModel(
                registry = get(),
                appInfoSource = get(),
                recentNotificationBuffer = get(),
                inboxRepository = get(),
                payeeRuleRepository = get(),
            )
        } bind SourcesScreenViewModel::class

        viewModel { params ->
            SourceSetupViewModel(
                packageName = params.getOrNull<String>(),
                registry = get(),
                appInfoSource = get(),
                recentNotificationBuffer = get(),
                activeNotificationsSource = get(),
            )
        } bind SourceSetupViewModel::class

        viewModel {
            TestTextScreenViewModel(
                registry = get(),
                appInfoSource = get(),
            )
        } bind TestTextScreenViewModel::class

        viewModel {
            CardAccountsScreenViewModel(
                inboxRepository = get(),
                accountRepository = get(),
                registry = get(),
                cardAccountPreferences = get(),
                autoBookPreferences = get(),
                appInfoSource = get(),
            )
        } bind CardAccountsScreenViewModel::class
    }
}
