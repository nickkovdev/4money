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

package ua.com.radiokot.money.overview.logic

import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.categories.logic.ArchivedSubcategoryAmounts
import ua.com.radiokot.money.categories.logic.SubcategoryAmountKey
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import java.math.BigInteger

/**
 * Pure logic of the category stats sheet.
 */
object CategoryStatsCalculator {

    data class SubcategoryRow(
        val key: SubcategoryAmountKey,
        val amount: BigInteger,
        /**
         * Share of the sum of all the amounts, within 0..1.
         */
        val fraction: Float,
    )

    /**
     * Archived subcategories are folded into one row.
     *
     * @return rows with non-zero amounts, the biggest first,
     * ties ordered by title, then the no-subcategory bucket, then the archived one.
     */
    fun subcategoryRows(
        amountBySubcategory: Map<Subcategory?, BigInteger>,
    ): List<SubcategoryRow> {
        val folded = ArchivedSubcategoryAmounts.fold(amountBySubcategory)
        val total = folded.values.fold(BigInteger.ZERO, BigInteger::add)

        return folded.entries
            .filter { (_, amount) -> amount.signum() != 0 }
            .sortedWith(
                compareByDescending<Map.Entry<SubcategoryAmountKey, BigInteger>> { it.value }
                    .thenBy {
                        when (it.key) {
                            is SubcategoryAmountKey.Active -> 0
                            SubcategoryAmountKey.None -> 1
                            SubcategoryAmountKey.Archived -> 2
                        }
                    }
                    .thenBy { (it.key as? SubcategoryAmountKey.Active)?.subcategory?.title }
            )
            .map { (key, amount) ->
                SubcategoryRow(
                    key = key,
                    amount = amount,
                    fraction = PrivacyAmounts.shareFraction(amount, total),
                )
            }
    }
}
