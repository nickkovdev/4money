/* Copyright 2026 Nikita Kovalovs

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

import kotlinx.coroutines.CancellationException

/**
 * What the sign-out confirmation says about local changes not yet uploaded,
 * which signing out deletes.
 */
sealed interface UnsentChangesWarning {

    data object None : UnsentChangesWarning

    data class Count(val count: Int) : UnsentChangesWarning

    /**
     * The check failed, warn anyway.
     */
    data object Unknown : UnsentChangesWarning

    companion object {
        suspend fun check(countUnsentChanges: suspend () -> Int): UnsentChangesWarning =
            try {
                val count = countUnsentChanges()
                if (count > 0) Count(count) else None
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Unknown
            }
    }
}
