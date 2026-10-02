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

package ua.com.radiokot.money.theme.data

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(
    val storedName: String,
) {
    /**
     * Follow the system dark mode setting: Paper in light, Midnight in dark. The default.
     */
    System("system"),

    /**
     * The Paper palette.
     */
    Light("light"),

    /**
     * The Midnight palette.
     */
    Dark("dark"),

    /**
     * A warm dark palette.
     */
    Ember("ember"),

    /**
     * A teal dark palette.
     */
    Aurora("aurora"),
    ;

    val appCompatNightMode: Int
        get() = when (this) {
            System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Light -> AppCompatDelegate.MODE_NIGHT_NO
            Dark, Ember, Aurora -> AppCompatDelegate.MODE_NIGHT_YES
        }

    companion object {
        fun fromStoredName(storedName: String?): ThemeMode =
            entries.firstOrNull { it.storedName == storedName }
                ?: System
    }
}
