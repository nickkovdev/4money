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

package ua.com.radiokot.money.categories.logic

import ua.com.radiokot.money.categories.data.Subcategory
import java.math.BigInteger

sealed interface SubcategoryAmountKey {

    data class Active(val subcategory: Subcategory) : SubcategoryAmountKey

    /**
     * Transfers without a subcategory.
     */
    data object None : SubcategoryAmountKey

    /**
     * All archived subcategories together.
     */
    data object Archived : SubcategoryAmountKey
}

object ArchivedSubcategoryAmounts {

    /**
     * Archived entry only when its sum is non-zero;
     * None and Active entries kept as given (zero ones too).
     */
    fun fold(
        amountBySubcategory: Map<Subcategory?, BigInteger>,
    ): Map<SubcategoryAmountKey, BigInteger> {
        val result = LinkedHashMap<SubcategoryAmountKey, BigInteger>()
        var archivedSum = BigInteger.ZERO

        amountBySubcategory.forEach { (subcategory, amount) ->
            when {
                subcategory == null ->
                    result[SubcategoryAmountKey.None] = amount

                subcategory.isArchived ->
                    archivedSum += amount

                else ->
                    result[SubcategoryAmountKey.Active(subcategory)] = amount
            }
        }

        if (archivedSum.signum() != 0) {
            result[SubcategoryAmountKey.Archived] = archivedSum
        }

        return result
    }
}
