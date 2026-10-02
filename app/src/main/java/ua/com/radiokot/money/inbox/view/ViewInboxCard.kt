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

package ua.com.radiokot.money.inbox.view

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester
import ua.com.radiokot.money.uikit.ViewText

/**
 * A category an inbox card can be sorted to.
 */
@Immutable
data class ViewInboxCardCategory(
    val key: InboxCardSuggester.CategoryKey,
    val title: String,
    val subcategoryTitle: String?,
    val colorScheme: ItemColorScheme,
    val icon: ItemIcon?,
) {
    val fullTitle: String
        get() =
            if (subcategoryTitle != null)
                "$title · $subcategoryTitle"
            else
                title
}

/**
 * A pending inbox item as a card to swipe.
 */
@Immutable
data class ViewInboxCard(
    val key: String,
    val title: String,
    /**
     * The [title] is the last line of the raw bank text, which usually contains the amount.
     */
    val isTitleRawText: Boolean = false,
    /**
     * Negative for an outgoing payment.
     */
    val amount: ViewAmount?,
    val isIncoming: Boolean,
    val isForeignCurrency: Boolean,
    val receivedAt: LocalDateTime,
    /**
     * The account or the card, e.g. "Card"; shown after the time of receiving.
     */
    val sourceText: String,
    val suggestion: ViewInboxCardCategory?,
    /**
     * Why the suggestion, e.g. "Remembered payee → Food".
     */
    val reasonText: ViewText?,
    val alternatives: List<ViewInboxCardCategory>,
    /**
     * The "Remember" toggle, null when there is nothing to learn
     * (the suggestion comes from a rule or the payee is unknown).
     */
    val isRememberOn: Boolean? = null,
    /**
     * The payee went to several categories: offer amount rules instead of remembering.
     */
    val isAmountRulesHinted: Boolean = false,
)

@Immutable
data class ViewInboxCardsProgress(
    val sortedCount: Int,
    val totalCount: Int,
) {
    val remainingCount: Int
        get() = (totalCount - sortedCount).coerceAtLeast(0)
}

/**
 * The last action that can be undone from the snackbar.
 */
@Immutable
data class ViewInboxCardUndo(
    val text: ViewText,
    val id: Long,
)
