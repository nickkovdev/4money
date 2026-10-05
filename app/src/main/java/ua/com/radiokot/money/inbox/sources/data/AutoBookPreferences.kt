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

import kotlinx.coroutines.flow.Flow

/**
 * Device-local auto-booking settings.
 */
interface AutoBookPreferences {

    /**
     * @return whether the built-in preset of the [packageName] is switched on, true by default
     */
    fun isPresetEnabled(packageName: String): Boolean

    fun setPresetEnabled(packageName: String, isEnabled: Boolean)

    /**
     * @return enabled state by package of the built-in presets
     */
    fun getPresetEnabledFlow(): Flow<Map<String, Boolean>>

    /**
     * A synchronous cache of the active source packages, read by the listener
     * on the main thread before the templates are loaded.
     *
     * @return the last saved set, or the enabled presets if none was saved yet
     */
    fun getCachedActivePackages(): Set<String>

    fun setCachedActivePackages(packages: Set<String>)
}
