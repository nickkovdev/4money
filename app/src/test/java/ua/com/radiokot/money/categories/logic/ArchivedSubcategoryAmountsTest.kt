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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ua.com.radiokot.money.categories.data.Subcategory
import java.math.BigInteger

class ArchivedSubcategoryAmountsTest {

    private val cafe = Subcategory(title = "Cafe", position = 1.0, categoryId = "c", id = "s1")
    private val oldA = Subcategory(
        title = "Old A", position = 2.0, categoryId = "c", id = "s2", isArchived = true,
    )
    private val oldB = Subcategory(
        title = "Old B", position = 3.0, categoryId = "c", id = "s3", isArchived = true,
    )

    @Test
    fun `two archived are summed into one entry`() {
        val folded = ArchivedSubcategoryAmounts.fold(
            mapOf(
                cafe to BigInteger.valueOf(10),
                oldA to BigInteger.valueOf(3),
                oldB to BigInteger.valueOf(4),
                null to BigInteger.valueOf(1),
            )
        )
        assertEquals(
            mapOf(
                SubcategoryAmountKey.Active(cafe) to BigInteger.valueOf(10),
                SubcategoryAmountKey.Archived to BigInteger.valueOf(7),
                SubcategoryAmountKey.None to BigInteger.valueOf(1),
            ),
            folded
        )
    }

    @Test
    fun `archived summing to zero is dropped`() {
        val folded = ArchivedSubcategoryAmounts.fold(
            mapOf(
                cafe to BigInteger.valueOf(10),
                oldA to BigInteger.valueOf(5),
                oldB to BigInteger.valueOf(-5),
            )
        )
        assertFalse(folded.containsKey(SubcategoryAmountKey.Archived))
        assertEquals(setOf<SubcategoryAmountKey>(SubcategoryAmountKey.Active(cafe)), folded.keys)
    }

    @Test
    fun `only archived`() {
        val folded = ArchivedSubcategoryAmounts.fold(mapOf(oldA to BigInteger.valueOf(5)))
        assertEquals(
            mapOf<SubcategoryAmountKey, BigInteger>(
                SubcategoryAmountKey.Archived to BigInteger.valueOf(5)
            ),
            folded
        )
    }

    @Test
    fun `zero active and none entries are kept`() {
        val folded = ArchivedSubcategoryAmounts.fold(
            mapOf(cafe to BigInteger.ZERO, null to BigInteger.ZERO)
        )
        assertEquals(
            setOf(SubcategoryAmountKey.Active(cafe), SubcategoryAmountKey.None),
            folded.keys
        )
    }

    @Test
    fun `total is preserved`() {
        val source = mapOf(
            cafe to BigInteger.valueOf(10),
            oldA to BigInteger.valueOf(3),
            oldB to BigInteger.valueOf(-8),
            null to BigInteger.valueOf(2),
        )
        assertEquals(
            source.values.fold(BigInteger.ZERO, BigInteger::add),
            ArchivedSubcategoryAmounts.fold(source).values.fold(BigInteger.ZERO, BigInteger::add)
        )
    }
}
