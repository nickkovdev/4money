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

package ua.com.radiokot.money.inbox.ask

import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester

/**
 * What the "Payments to sort" notification asks about a pending payment. Pure.
 */
object PaymentQuestion {

    const val MAX_ACTIONS = 3

    /**
     * Ask only when the user wants to (an Ask rule). Payees without a rule just wait in the inbox,
     * to keep notifications few.
     */
    fun shouldAsk(
        reason: AutoExpenseResolver.PendingReason,
    ): Boolean =
        reason == AutoExpenseResolver.PendingReason.AskRequested

    /**
     * The notification title: "Fuelstop · −18.40 €", or just the payee in privacy mode.
     */
    fun notificationTitle(
        payee: String,
        signedAmount: String,
        isPrivate: Boolean,
    ): String =
        if (isPrivate)
            payee
        else
            "$payee · $signedAmount"

    /**
     * @return up to [MAX_ACTIONS] categories for the action buttons: the suggestion first,
     * then the alternatives.
     */
    fun actionCategories(
        suggestions: InboxCardSuggester.Result,
    ): List<InboxCardSuggester.CategoryKey> =
        (listOfNotNull(suggestions.suggestion?.category) + suggestions.alternatives)
            .distinct()
            .take(MAX_ACTIONS)
}
