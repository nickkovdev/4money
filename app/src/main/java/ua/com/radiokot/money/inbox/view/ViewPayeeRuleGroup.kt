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
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.inbox.data.AmountRange
import ua.com.radiokot.money.inbox.data.PayeeRule
import java.math.BigDecimal

/**
 * All rules of one payee pattern: the plain one and the amount ranges.
 */
@Immutable
data class ViewPayeeRuleGroup(
    val key: String,
    val displayPattern: String,
    val matchType: PayeeRule.MatchType,
    val subtitle: String,
    val rows: List<ViewPayeeRuleRow>,
    val colorScheme: ItemColorScheme?,
    val icon: ItemIcon?,
    /**
     * Any rule of the group, for pattern-wide actions.
     */
    val anyRule: PayeeRule,
    val currencyCode: String?,
)

@Immutable
data class ViewPayeeRuleRow(
    /**
     * "Any amount" for the plain rule.
     */
    val rangeText: String,
    val targetTitle: String,
    val isAsk: Boolean,
    val colorScheme: ItemColorScheme?,
    val icon: ItemIcon?,
    val rule: PayeeRule,
)

/**
 * The target of a range being edited.
 */
@Immutable
sealed interface ViewRangeTarget {
    data object Ask : ViewRangeTarget

    data class Category(
        val categoryId: String,
        val subcategoryId: String?,
        val title: String,
    ) : ViewRangeTarget
}

/**
 * A range rule being added or edited.
 */
@Immutable
data class ViewRangeDraft(
    val ruleId: String?,
    val payeePattern: String,
    val matchType: PayeeRule.MatchType,
    val displayPattern: String,
    val currencyCode: String?,
    val fromText: String,
    val underText: String,
    val target: ViewRangeTarget?,
    /**
     * Categories already used for this payee, offered as chips.
     */
    val categoryOptions: List<ViewRangeTarget.Category>,
    val isIncome: Boolean,
    val error: String? = null,
)

/**
 * Validates the range fields of a draft: "From" is inclusive, "Under" is exclusive,
 * at least one is needed, decimal comma or dot.
 *
 * @return the range or an error text
 */
fun parseRangeDraft(
    fromText: String,
    underText: String,
): Result<AmountRange> {
    fun parse(text: String): BigDecimal? =
        text.trim()
            .replace(',', '.')
            .replace(" ", "")
            .takeIf(String::isNotEmpty)
            ?.toBigDecimalOrNull()

    val from = parse(fromText)
    val under = parse(underText)

    if (fromText.isNotBlank() && from == null || underText.isNotBlank() && under == null) {
        return Result.failure(IllegalArgumentException("Enter amounts like 10 or 9.99"))
    }
    if (from == null && under == null) {
        return Result.failure(IllegalArgumentException("Enter at least one of the amounts"))
    }
    if ((from != null && from.signum() < 0) || (under != null && under.signum() < 0)) {
        return Result.failure(IllegalArgumentException("Amounts can't be negative"))
    }
    if (from != null && under != null && from >= under) {
        return Result.failure(IllegalArgumentException("“From” must be less than “Under”"))
    }

    return Result.success(
        AmountRange(
            min = from,
            isMinInclusive = true,
            max = under,
            isMaxInclusive = false,
        )
    )
}
