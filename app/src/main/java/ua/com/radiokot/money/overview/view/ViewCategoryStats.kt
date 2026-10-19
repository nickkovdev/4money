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

package ua.com.radiokot.money.overview.view

import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.R
import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryWithAmountsBySubcategory
import ua.com.radiokot.money.categories.logic.SubcategoryAmountKey
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.overview.logic.CategoryStatsCalculator
import ua.com.radiokot.money.overview.logic.OverviewData
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import java.math.BigInteger

@Immutable
class ViewCategoryStats(
    val category: Category,
    val title: String,
    val colorScheme: ItemColorScheme,
    val icon: ItemIcon?,
    val isIncome: Boolean,
    val transferCount: Int,
    /**
     * In the category currency.
     */
    val total: ViewAmount,
    /**
     * The category amount within the period total, in the primary currency.
     */
    val periodShare: BigInteger,
    val periodTotal: ViewAmount,
    val periodShareFraction: Float,
    /**
     * The text is resolved in the composition.
     */
    val period: ViewHistoryPeriod,
    val subcategories: List<ViewCategoryStatsSubcategory>,
) {
    companion object {
        fun from(
            categoryWithAmounts: CategoryWithAmountsBySubcategory,
            transferCount: Int,
            overview: OverviewData?,
            period: ViewHistoryPeriod,
        ): ViewCategoryStats {
            val category = categoryWithAmounts.category
            val categoryCurrency = ViewCurrency(category.currency)
            val total = categoryWithAmounts.amountBySubcategory.values
                .fold(BigInteger.ZERO, BigInteger::add)

            val periodStats = overview?.let {
                if (category.isIncome)
                    it.income
                else
                    it.expense
            }
            val periodShare = periodStats
                ?.categories
                ?.firstOrNull { it.categoryId == category.id }
                ?.amount
                ?: BigInteger.ZERO
            val periodTotal = periodStats?.total ?: BigInteger.ZERO
            val primaryCurrency = overview?.primaryCurrency?.let(::ViewCurrency)
                ?: categoryCurrency

            return ViewCategoryStats(
                category = category,
                title = category.title,
                colorScheme = category.colorScheme,
                icon = category.icon,
                isIncome = category.isIncome,
                transferCount = transferCount,
                total = ViewAmount(total, categoryCurrency),
                periodShare = periodShare,
                periodTotal = ViewAmount(periodTotal, primaryCurrency),
                periodShareFraction = PrivacyAmounts.shareFraction(periodShare, periodTotal),
                period = period,
                subcategories = CategoryStatsCalculator
                    .subcategoryRows(categoryWithAmounts.amountBySubcategory)
                    .map { row ->
                        ViewCategoryStatsSubcategory(
                            key = when (val key = row.key) {
                                is SubcategoryAmountKey.Active -> key.subcategory.id
                                SubcategoryAmountKey.None -> "none"
                                SubcategoryAmountKey.Archived -> "archived"
                            },
                            title = when (val key = row.key) {
                                is SubcategoryAmountKey.Active ->
                                    ViewText.Plain(key.subcategory.title)

                                SubcategoryAmountKey.None ->
                                    ViewText.Res(R.string.overview_no_subcategory)

                                SubcategoryAmountKey.Archived ->
                                    ViewText.Res(R.string.subcategories_archived_row)
                            },
                            isUncategorized = row.key !is SubcategoryAmountKey.Active,
                            amount = ViewAmount(row.amount, categoryCurrency),
                            fraction = row.fraction,
                        )
                    },
            )
        }
    }
}

@Immutable
class ViewCategoryStatsSubcategory(
    val key: String,
    val title: ViewText,
    val isUncategorized: Boolean,
    val amount: ViewAmount,
    val fraction: Float,
)
