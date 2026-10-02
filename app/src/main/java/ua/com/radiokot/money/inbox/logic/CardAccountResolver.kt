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

package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource

interface CardAccountResolver {

    /**
     * @param usableAccountIds IDs of existing non-archived accounts;
     * a mapped, rule or most used account not among them is skipped.
     *
     * @return the account to use: the account mapped to [cardLast4] in settings,
     * otherwise [ruleAccountId], otherwise the most used account.
     */
    suspend fun resolve(
        cardLast4: String?,
        ruleAccountId: String?,
        usableAccountIds: Set<String>,
    ): String?
}

class DefaultCardAccountResolver(
    private val cardAccountPreferences: CardAccountPreferences,
    private val mostUsedAccountSource: MostUsedAccountSource,
) : CardAccountResolver {

    override suspend fun resolve(
        cardLast4: String?,
        ruleAccountId: String?,
        usableAccountIds: Set<String>,
    ): String? =
        cardLast4
            ?.let(cardAccountPreferences::getAccountIdForCard)
            ?.takeIf(usableAccountIds::contains)
            ?: ruleAccountId?.takeIf(usableAccountIds::contains)
            ?: mostUsedAccountSource.getMostUsedAccountId()?.takeIf(usableAccountIds::contains)
}
