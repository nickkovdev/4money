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

package ua.com.radiokot.money.inbox.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CardAccountPreferencesOnPrefs(
    private val preferences: SharedPreferences,
) : CardAccountPreferences {

    private val knownCardsKey = "known_cards"
    private val cardAccountsStateFlow = MutableStateFlow(readCardAccounts())

    private fun getKey(cardLast4: String) =
        "card_account_$cardLast4"

    override fun getAccountIdForCard(cardLast4: String): String? =
        preferences.getString(getKey(cardLast4), null)

    override fun setAccountIdForCard(cardLast4: String, accountId: String) {
        preferences.edit {
            putStringSet(
                knownCardsKey,
                preferences.getStringSet(knownCardsKey, emptySet())!! + cardLast4
            )
            putString(getKey(cardLast4), accountId)
        }
        cardAccountsStateFlow.update { it + (cardLast4 to accountId) }
    }

    override fun getCardAccountsFlow(): Flow<Map<String, String>> =
        cardAccountsStateFlow.asStateFlow()

    private fun readCardAccounts(): Map<String, String> = buildMap {
        preferences
            .getStringSet(knownCardsKey, emptySet())!!
            .forEach { cardLast4 ->
                getAccountIdForCard(cardLast4)?.also { put(cardLast4, it) }
            }
    }
}
