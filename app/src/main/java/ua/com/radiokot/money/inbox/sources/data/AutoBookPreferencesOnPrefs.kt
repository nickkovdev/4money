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


package ua.com.radiokot.money.inbox.sources.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @param presetPackageNames packages of the built-in presets
 */
class AutoBookPreferencesOnPrefs(
    private val preferences: SharedPreferences,
    private val presetPackageNames: Collection<String>,
) : AutoBookPreferences {

    private val activePackagesKey = "active_packages"
    private val presetEnabledStateFlow = MutableStateFlow(readPresetEnabled())
    private val behaviourStateFlow = MutableStateFlow(readBehaviour())

    private fun getPresetDisabledKey(packageName: String) =
        "preset_disabled_$packageName"

    override fun isPresetEnabled(packageName: String): Boolean =
        !preferences.getBoolean(getPresetDisabledKey(packageName), false)

    @Synchronized
    override fun setPresetEnabled(packageName: String, isEnabled: Boolean) {
        preferences.edit {
            putBoolean(getPresetDisabledKey(packageName), !isEnabled)
        }
        presetEnabledStateFlow.value = readPresetEnabled() + (packageName to isEnabled)
    }

    override fun getPresetEnabledFlow(): Flow<Map<String, Boolean>> =
        presetEnabledStateFlow.asStateFlow()

    override fun getCachedActivePackages(): Set<String> =
        preferences.getStringSet(activePackagesKey, null)
            ?.toSet()
            ?: presetPackageNames.filterTo(mutableSetOf(), ::isPresetEnabled)

    override fun setCachedActivePackages(packages: Set<String>) {
        if (preferences.getStringSet(activePackagesKey, null) == packages) {
            return
        }

        preferences.edit {
            // A copy: the stored set must not be modified afterwards.
            putStringSet(activePackagesKey, packages.toSet())
        }
    }

    private fun readPresetEnabled(): Map<String, Boolean> =
        presetPackageNames.associateWith(::isPresetEnabled)

    override var isRecordKnownPayeesEnabled: Boolean
        get() = preferences.getBoolean(RECORD_KNOWN_PAYEES_KEY, true)
        set(value) = setBehaviour(RECORD_KNOWN_PAYEES_KEY, value)

    override var isAskInNotificationEnabled: Boolean
        get() = preferences.getBoolean(ASK_IN_NOTIFICATION_KEY, true)
        set(value) = setBehaviour(ASK_IN_NOTIFICATION_KEY, value)

    override var isLearnFromHistoryEnabled: Boolean
        get() = preferences.getBoolean(LEARN_FROM_HISTORY_KEY, true)
        set(value) = setBehaviour(LEARN_FROM_HISTORY_KEY, value)

    override fun getBehaviourFlow(): Flow<AutoBookBehaviour> =
        behaviourStateFlow.asStateFlow()

    @Synchronized
    private fun setBehaviour(key: String, value: Boolean) {
        preferences.edit {
            putBoolean(key, value)
        }
        behaviourStateFlow.value = readBehaviour()
    }

    private fun readBehaviour() = AutoBookBehaviour(
        recordKnownPayees = isRecordKnownPayeesEnabled,
        askInNotification = isAskInNotificationEnabled,
        learnFromHistory = isLearnFromHistoryEnabled,
    )

    private companion object {
        const val RECORD_KNOWN_PAYEES_KEY = "record_known_payees"
        const val ASK_IN_NOTIFICATION_KEY = "ask_in_notification"
        const val LEARN_FROM_HISTORY_KEY = "learn_from_history"
    }
}
