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

package ua.com.radiokot.money.inbox.sources.view.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.sources.data.ActiveNotificationsSource
import ua.com.radiokot.money.inbox.sources.data.AppInfo
import ua.com.radiokot.money.inbox.sources.data.AppInfoSource
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.sources.logic.DetectedApp
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.inbox.sources.logic.SaveSourceSetupUseCase
import ua.com.radiokot.money.inbox.sources.logic.TeachDraft
import ua.com.radiokot.money.inbox.sources.logic.TestRunResult
import ua.com.radiokot.money.inbox.sources.logic.detectApps
import ua.com.radiokot.money.inbox.sources.logic.distinctNotifications
import ua.com.radiokot.money.inbox.sources.logic.runTest
import ua.com.radiokot.money.inbox.sources.view.ViewAccountMapping
import ua.com.radiokot.money.inbox.sources.view.ViewAccountMappingRow
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.logic.TokenRole
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.uikit.ViewText
import java.util.UUID
import kotlin.time.Clock

/**
 * The source setup wizard. The state machine is [SourceSetupState],
 * this class loads the notifications and the apps around it.
 *
 * @param packageName the source to teach, null to add a new app from the intro
 */
class SourceSetupViewModel(
    packageName: String?,
    private val registry: NotificationSourceRegistry,
    private val appInfoSource: AppInfoSource,
    private val recentNotificationBuffer: RecentNotificationBuffer,
    private val activeNotificationsSource: ActiveNotificationsSource,
    accountRepository: AccountRepository,
    cardAccountPreferences: CardAccountPreferences,
    autoBookPreferences: AutoBookPreferences,
    private val saveSourceSetup: SaveSourceSetupUseCase,
) : ViewModel() {

    private val log by lazyLogger("SourceSetupVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    private val _state = MutableStateFlow(SourceSetupState.initial(packageName))
    val state: StateFlow<SourceSetupState> = _state.asStateFlow()

    private val _isAccessGranted = MutableStateFlow(false)
    val isAccessGranted = _isAccessGranted.asStateFlow()

    // Active and buffered notifications of all the apps, newest first, distinct.
    private val allNotifications = MutableStateFlow<List<RecentNotification>>(emptyList())

    // The user went to the system settings to grant the access.
    private var isAwaitingAccess = false

    /**
     * Apps that sent money-like notifications and are not sources yet.
     */
    val detectedApps: StateFlow<List<ViewApp>> =
        combine(
            allNotifications,
            registry.sourcesFlow,
        ) { notifications, sources ->
            detectApps(
                notifications = notifications,
                now = Clock.System.now().toEpochMilliseconds(),
                excludePackages = sources.mapTo(mutableSetOf()) { it.packageName },
            ).map { app ->
                ViewApp(
                    app = app,
                    label = appInfoSource.getLabel(app.packageName)
                        ?: app.packageName,
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sourcePackages: StateFlow<Set<String>> =
        registry.sourcesFlow
            .map { sources -> sources.mapTo(mutableSetOf()) { it.packageName } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _isPickingApp = MutableStateFlow(false)
    val isPickingApp = _isPickingApp.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>?>(null)

    /**
     * Launchable apps, null until loaded.
     */
    val allApps: StateFlow<List<AppInfo>?> = _allApps.asStateFlow()

    /**
     * The label of the chosen app, null if none is chosen.
     */
    val chosenAppLabel: StateFlow<String?> =
        _state
            .map { state ->
                state.packageName?.let { appInfoSource.getLabel(it) ?: it }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Called whenever the screen is resumed, e.g. back from the system settings.
     * Without the access, the recent notifications are not kept.
     */
    fun onResumed(isNotificationAccessGranted: Boolean) {
        _isAccessGranted.value = isNotificationAccessGranted

        if (!isNotificationAccessGranted) {
            allNotifications.value = emptyList()
            _state.update { it.withNotifications(emptyList()) }
            viewModelScope.launch(Dispatchers.IO) {
                recentNotificationBuffer.clear()
            }
            return
        }

        refreshNotifications()

        if (isAwaitingAccess && _state.value.step == SourceSetupState.Step.Intro) {
            isAwaitingAccess = false
            _state.update(SourceSetupState::next)
        }
    }

    fun onGrantAccessClicked() {
        isAwaitingAccess = true
        _events.tryEmit(Event.ProceedToAccessSettings)
    }

    fun onLaterClicked() {
        _events.tryEmit(Event.Close)
    }

    fun onNextClicked() {
        _state.update { it.next().withNotifications(allNotifications.value) }

        if (_state.value.step == SourceSetupState.Step.Teach) {
            prepareTeach()
        }
    }

    fun onBackClicked() {
        when {
            _isPickingApp.value ->
                _isPickingApp.value = false

            _state.value.canGoBack ->
                _state.update(SourceSetupState::back)

            else ->
                _events.tryEmit(Event.Close)
        }
    }

    fun onAppSelected(packageName: String) {
        _state.update { it.withPackage(packageName) }
    }

    fun onPickFromAllAppsClicked() {
        _isPickingApp.value = true

        if (_allApps.value == null) {
            viewModelScope.launch {
                _allApps.value = withContext(Dispatchers.IO) {
                    try {
                        appInfoSource.getLaunchableApps()
                    } catch (e: Exception) {
                        log.error(e) {
                            "onPickFromAllAppsClicked(): failed to list the apps"
                        }
                        emptyList()
                    }
                }
            }
        }
    }

    fun onAppPickedFromAll(app: AppInfo) {
        _isPickingApp.value = false
        onAppSelected(app.packageName)
    }

    fun onSampleSelected(sample: RecentNotification) {
        _state.update { it.copy(selectedSample = sample) }
    }

    // Step 4: Teach.

    private val _teach = MutableStateFlow<TeachDraft?>(null)

    /**
     * The marks of the sample being taught, null outside of the Teach step.
     */
    val teach: StateFlow<TeachDraft?> = _teach.asStateFlow()

    // The marks stay while the wizard is open: back from Test, or the same sample again.
    private val teachBySample = mutableMapOf<RecentNotification, TeachDraft>()

    private fun prepareTeach() {
        val sample = _state.value.selectedSample
            ?: return

        _teach.value = teachBySample.getOrPut(sample) { TeachDraft.of(sample) }
    }

    private fun updateTeach(transform: (TeachDraft) -> TeachDraft) {
        val draft = _teach.value
            ?: return
        val updated = transform(draft)

        teachBySample[updated.sample] = updated
        _teach.value = updated
    }

    fun onTokenTapped(index: Int) {
        updateTeach { it.withTokenTapped(index) }
    }

    /**
     * @param role null to clear the mark
     */
    fun onRoleChosen(index: Int, role: TokenRole?) {
        updateTeach { it.withRole(index, role) }
    }

    fun onDirectionChosen(direction: NotificationTemplate.Direction) {
        updateTeach { it.withDirection(direction) }
    }

    /**
     * "Check on others": the taught kind becomes a draft, nothing is saved yet.
     */
    fun onCheckOnOthersClicked() {
        val packageName = _state.value.packageName
            ?: return
        val template = _teach.value
            ?.toTemplate(
                id = UUID.randomUUID().toString(),
                sourcePackage = packageName,
                createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            )
            ?: return

        _state.update { it.withDraft(template).next().withNotifications(allNotifications.value) }
    }

    // Step 5: Test.

    /**
     * The preset and the existing and taught templates tried on the notifications
     * of the app, null outside of the Test and Accounts steps.
     */
    val testRun: StateFlow<TestRunResult?> =
        combine(
            _state,
            registry.sourcesFlow,
        ) { state, sources ->
            if (state.step != SourceSetupState.Step.Test && state.step != SourceSetupState.Step.Accounts) {
                return@combine null
            }

            val source = sources.firstOrNull { it.packageName == state.packageName }

            runTest(
                preset = source?.preset,
                templates = source?.templates.orEmpty() + state.drafts,
                notifications = state.samples,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onTeachAnotherClicked() {
        _state.update { it.teachAnother().withNotifications(allNotifications.value) }
    }

    // Step 6: Accounts and behaviour. Held here until Done, nothing is written before.

    private class AccountDrafts(
        val cards: Map<String, String> = emptyMap(),
        val source: String? = null,
    )

    private val accountDrafts = MutableStateFlow(AccountDrafts())
    private var rowBeingMapped: ViewAccountMappingRow.Target? = null

    /**
     * The cards of the understood notifications and the already mapped ones,
     * and the payments of the app without a card.
     */
    val mapping: StateFlow<ViewAccountMapping> =
        combine(
            testRun,
            cardAccountPreferences.getCardAccountsFlow(),
            cardAccountPreferences.getSourceAccountsFlow(),
            accountRepository.getAccountsFlow(),
            accountDrafts,
        ) { testRun, savedCards, savedSources, accounts, drafts ->
            val accountsById = accounts.associateBy(Account::id)
            val packageName = _state.value.packageName
            val cards = testRun?.matched.orEmpty().mapNotNull { it.second.cardLast4 } +
                    savedCards.keys +
                    drafts.cards.keys

            ViewAccountMapping(
                cardRows = cards
                    .distinct()
                    .sorted()
                    .map { cardLast4 ->
                        ViewAccountMappingRow(
                            target = ViewAccountMappingRow.Target.Card(cardLast4),
                            title = ViewText.Res(R.string.inbox_card_title, listOf(cardLast4)),
                            accountTitle = (drafts.cards[cardLast4] ?: savedCards[cardLast4])
                                ?.let(accountsById::get)
                                ?.title,
                        )
                    },
                sourceRows =
                    if (packageName != null)
                        listOf(
                            ViewAccountMappingRow(
                                target = ViewAccountMappingRow.Target.Source(packageName),
                                title = ViewText.Res(
                                    R.string.cards_without_card,
                                    listOf(appInfoSource.getLabel(packageName) ?: packageName),
                                ),
                                accountTitle = (drafts.source ?: savedSources[packageName])
                                    ?.let(accountsById::get)
                                    ?.title,
                            )
                        )
                    else
                        emptyList(),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ViewAccountMapping(emptyList(), emptyList()),
            )

    private val _behaviour = MutableStateFlow(
        AutoBookBehaviour(
            recordKnownPayees = autoBookPreferences.isRecordKnownPayeesEnabled,
            askInNotification = autoBookPreferences.isAskInNotificationEnabled,
            learnFromHistory = autoBookPreferences.isLearnFromHistoryEnabled,
        )
    )
    val behaviour: StateFlow<AutoBookBehaviour> = _behaviour.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isSaveFailed = MutableStateFlow(false)
    val isSaveFailed: StateFlow<Boolean> = _isSaveFailed.asStateFlow()

    fun onAccountRowClicked(row: ViewAccountMappingRow) {
        rowBeingMapped = row.target
        _events.tryEmit(Event.ProceedToAccountSelection)
    }

    /**
     * The result of the account selection sheet opened by [Event.ProceedToAccountSelection].
     */
    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        val target = rowBeingMapped
            ?: return
        rowBeingMapped = null

        val accountId = (result.selectedCounterparty as? TransferCounterparty.Account)
            ?.account
            ?.id
            ?: return

        accountDrafts.update { drafts ->
            when (target) {
                is ViewAccountMappingRow.Target.Card ->
                    AccountDrafts(drafts.cards + (target.cardLast4 to accountId), drafts.source)

                is ViewAccountMappingRow.Target.Source ->
                    AccountDrafts(drafts.cards, accountId)
            }
        }
    }

    fun onRecordKnownPayeesChanged(isEnabled: Boolean) {
        _behaviour.update { it.copy(recordKnownPayees = isEnabled) }
    }

    fun onAskInNotificationChanged(isEnabled: Boolean) {
        _behaviour.update { it.copy(askInNotification = isEnabled) }
    }

    fun onLearnFromHistoryChanged(isEnabled: Boolean) {
        _behaviour.update { it.copy(learnFromHistory = isEnabled) }
    }

    /**
     * Saves the taught kinds, the accounts and the behaviour, clears the samples
     * and closes the wizard.
     */
    fun onDoneClicked() {
        val state = _state.value
        val packageName = state.packageName
            ?: return
        if (_isSaving.value) {
            return
        }

        _isSaving.value = true
        _isSaveFailed.value = false

        viewModelScope.launch {
            try {
                saveSourceSetup(
                    SaveSourceSetupUseCase.Request(
                        packageName = packageName,
                        templates = state.drafts,
                        cardAccounts = accountDrafts.value.cards,
                        sourceAccountId = accountDrafts.value.source,
                        behaviour = _behaviour.value,
                    )
                )

                _events.tryEmit(Event.Close)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) {
                    "onDoneClicked(): failed to save the setup"
                }
                _isSaveFailed.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            val notifications = withContext(Dispatchers.IO) {
                try {
                    (activeNotificationsSource.getActive() + recentNotificationBuffer.getAll())
                        .sortedByDescending(RecentNotification::postTimeMillis)
                        .distinctNotifications()
                } catch (e: Exception) {
                    log.error(e) {
                        "refreshNotifications(): failed to read the notifications"
                    }
                    emptyList()
                }
            }

            allNotifications.value = notifications
            _state.update { it.withNotifications(notifications) }
        }
    }

    class ViewApp(
        val app: DetectedApp,
        val label: String,
    )

    sealed interface Event {

        object ProceedToAccessSettings : Event

        /**
         * Pass the result to [onCounterpartySelected].
         */
        object ProceedToAccountSelection : Event

        object Close : Event
    }
}
