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
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.sources.data.ActiveNotificationsSource
import ua.com.radiokot.money.inbox.sources.data.AppInfo
import ua.com.radiokot.money.inbox.sources.data.AppInfoSource
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.sources.logic.DetectedApp
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.inbox.sources.logic.detectApps
import ua.com.radiokot.money.inbox.sources.logic.distinctNotifications
import ua.com.radiokot.money.lazyLogger
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

        object Close : Event
    }
}
