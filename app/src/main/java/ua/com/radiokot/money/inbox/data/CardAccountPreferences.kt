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

import kotlinx.coroutines.flow.Flow

/**
 * Card last 4 digits → account ID. Device-local, not synced.
 */
interface CardAccountPreferences {

    fun getAccountIdForCard(cardLast4: String): String?

    fun setAccountIdForCard(cardLast4: String, accountId: String)

    fun getCardAccountsFlow(): Flow<Map<String, String>>

    /**
     * The account for payments of the source [sourcePackage] that carry no card.
     */
    fun getAccountIdForSource(sourcePackage: String): String?

    fun setAccountIdForSource(sourcePackage: String, accountId: String)

    fun getSourceAccountsFlow(): Flow<Map<String, String>>
}
