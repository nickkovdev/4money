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

package ua.com.radiokot.money.preferences.view

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.radiokot.money.auth.data.UserSession
import ua.com.radiokot.money.auth.logic.SignOutUseCase
import ua.com.radiokot.money.currency.data.CurrencyPreferences
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.lock.logic.AppLock
import ua.com.radiokot.money.lock.logic.DisableAppLockUseCase
import ua.com.radiokot.money.preferences.logic.AppLanguage
import ua.com.radiokot.money.syncerrors.data.SyncErrorRepository
import ua.com.radiokot.money.theme.data.ThemeMode
import ua.com.radiokot.money.theme.data.ThemePreferences

class PreferencesScreenViewModel(
    private val currencyPreferences: CurrencyPreferences,
    session: UserSession,
    syncErrorRepository: SyncErrorRepository,
    inboxRepository: InboxRepository,
    private val signOutUseCase: SignOutUseCase,
    appLock: AppLock,
    private val disableAppLockUseCase: DisableAppLockUseCase,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    private val log by lazyLogger("PreferencesScreenVM")
    private val _primaryCurrencyCodeValue: MutableStateFlow<String> =
        MutableStateFlow(currencyPreferences.primaryCurrencyCode.value)
    val primaryCurrencyCodeValue = _primaryCurrencyCodeValue.asStateFlow()
    val userId: StateFlow<String> = MutableStateFlow(session.userInfo.id)
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    val isSaveCurrencyPreferencesEnabled: StateFlow<Boolean> =
        combine(
            primaryCurrencyCodeValue,
            currencyPreferences.primaryCurrencyCode,
            transform = ::Pair,
        )
            .map { (enteredPrimaryCurrencyCode, savedPrimaryCurrencyCode) ->
                enteredPrimaryCurrencyCode.isNotBlank()
                        && enteredPrimaryCurrencyCode != savedPrimaryCurrencyCode
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isSyncErrorsNoticeVisible: StateFlow<Boolean> =
        syncErrorRepository
            .getErrorCountFlow()
            .map { it > 0 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isAppLockEnabled: StateFlow<Boolean> =
        appLock.isEnabled

    private val _isNotificationAccessGranted: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isNotificationAccessGranted = _isNotificationAccessGranted.asStateFlow()

    val pendingInboxCount: StateFlow<Long> =
        inboxRepository
            .getPendingCountFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun onNotificationAccessChecked(isGranted: Boolean) {
        _isNotificationAccessGranted.value = isGranted
    }

    fun onNotificationAccessClicked() {
        _events.tryEmit(Event.ProceedToNotificationAccessSettings)
    }

    fun onInboxClicked() {
        _events.tryEmit(Event.ProceedToInbox)
    }

    val themeMode: StateFlow<ThemeMode> =
        themePreferences.themeMode

    fun onThemeModeClicked(mode: ThemeMode) {
        if (themePreferences.themeMode.value == mode) {
            return
        }

        log.debug {
            "onThemeModeClicked(): switching theme mode:" +
                    "\nmode=$mode"
        }

        themePreferences.themeMode.value = mode
    }

    private val _language: MutableStateFlow<AppLanguage> =
        MutableStateFlow(
            AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
        )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun refreshLanguage() {
        _language.value =
            AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    fun onLanguageClicked(language: AppLanguage) {
        if (_language.value == language) {
            return
        }

        log.debug {
            "onLanguageClicked(): switching language:" +
                    "\nlanguage=$language"
        }

        _language.value = language

        // AppCompat stores the choice and recreates the activities.
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.tag)
        )
    }

    fun onPrimaryCurrencyCodeChanged(newValue: String) {
        _primaryCurrencyCodeValue.value = newValue
    }

    fun onSaveCurrencyPreferencesClicked() {
        if (!isSaveCurrencyPreferencesEnabled.value) {
            log.warn {
                "onSaveCurrencyPreferencesClicked(): ignoring as save is not enabled"
            }
            return
        }

        currencyPreferences.primaryCurrencyCode.value = primaryCurrencyCodeValue.value
    }

    fun onSignOutClicked() {
        _events.tryEmit(Event.ProceedToSignOutConfirmation)
    }

    fun onSignOutConfirmed() {
        signOut()
    }

    private var signOutJob: Job? = null
    private fun signOut() {

        signOutJob?.cancel()
        signOutJob = viewModelScope.launch {
            log.debug {
                "signOut: signing out"
            }

            signOutUseCase
                .invoke()
                .onSuccess {
                    log.debug {
                        "signOut(): successfully signed out"
                    }

                    _events.tryEmit(Event.SignedOut)
                }
                .onFailure { error ->
                    log.error(error) {
                        "signOut(): failed to sign out"
                    }
                }
        }
    }

    fun onAppLockClicked() {
        if (isAppLockEnabled.value) {
            disableAppLock()
        } else {
            _events.tryEmit(Event.ProceedToPasscodeSetup)
        }
    }

    private var disableAppLockJob: Job? = null
    private fun disableAppLock() {

        disableAppLockJob?.cancel()
        disableAppLockJob = viewModelScope.launch {
            log.debug {
                "disableAppLock(): disabling"
            }

            disableAppLockUseCase
                .invoke()
                .onSuccess {
                    log.debug {
                        "disableAppLock(): successfully disabled"
                    }
                }
                .onFailure { error ->
                    log.error(error) {
                        "disableAppLock(): failed to disable"
                    }
                }
        }
    }

    sealed interface Event {

        object ProceedToPasscodeSetup : Event
        object SignedOut : Event
        object ProceedToNotificationAccessSettings : Event
        object ProceedToInbox : Event

        /**
         * Pass the confirmation to [onSignOutConfirmed].
         */
        object ProceedToSignOutConfirmation : Event
    }
}
