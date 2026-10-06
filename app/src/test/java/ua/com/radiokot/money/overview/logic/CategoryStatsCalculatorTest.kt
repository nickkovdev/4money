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

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.categories.logic.SubcategoryAmountKey
import java.math.BigInteger

class CategoryStatsCalculatorTest {

    @Test
    fun `rows sorted desc, zeros dropped, fractions of the total`() {
        val food = Subcategory(title = "Food", position = 1.0, categoryId = "c", id = "s1")
        val fuel = Subcategory(title = "Fuel", position = 2.0, categoryId = "c", id = "s2")
        val idle = Subcategory(title = "Idle", position = 3.0, categoryId = "c", id = "s3")
        val rows = CategoryStatsCalculator.subcategoryRows(
            mapOf(
                food to BigInteger.valueOf(25),
                fuel to BigInteger.valueOf(50),
                idle to BigInteger.ZERO,
                null to BigInteger.valueOf(25),
            )
        )
        assertEquals(
            listOf(
                SubcategoryAmountKey.Active(fuel),
                SubcategoryAmountKey.Active(food),
                SubcategoryAmountKey.None,
            ),
            rows.map { it.key }
        )
        assertEquals(listOf(0.5f, 0.25f, 0.25f), rows.map { it.fraction })
    }

    @Test
    fun `only uncategorized`() {
        val rows = CategoryStatsCalculator.subcategoryRows(mapOf(null to BigInteger.TEN))
        assertEquals(1, rows.size)
        assertEquals(1f, rows.single().fraction)
    }

    @Test
    fun `archived row with the right fraction`() {
        val food = Subcategory(title = "Food", position = 1.0, categoryId = "c", id = "s1")
        val oldA = Subcategory(
            title = "Old A", position = 2.0, categoryId = "c", id = "s2", isArchived = true,
        )
        val oldB = Subcategory(
            title = "Old B", position = 3.0, categoryId = "c", id = "s3", isArchived = true,
        )
        val rows = CategoryStatsCalculator.subcategoryRows(
            mapOf(
                food to BigInteger.valueOf(50),
                oldA to BigInteger.valueOf(20),
                oldB to BigInteger.valueOf(30),
            )
        )
        assertEquals(
            listOf(SubcategoryAmountKey.Active(food), SubcategoryAmountKey.Archived),
            rows.map { it.key }
        )
        assertEquals(BigInteger.valueOf(50), rows[1].amount)
        assertEquals(listOf(0.5f, 0.5f), rows.map { it.fraction })
    }

    @Test
    fun `ties ordered active then none then archived`() {
        val food = Subcategory(title = "Food", position = 1.0, categoryId = "c", id = "s1")
        val old = Subcategory(
            title = "Old", position = 2.0, categoryId = "c", id = "s2", isArchived = true,
        )
        val rows = CategoryStatsCalculator.subcategoryRows(
            mapOf(
                old to BigInteger.TEN,
                null to BigInteger.TEN,
                food to BigInteger.TEN,
            )
        )
        assertEquals(
            listOf(
                SubcategoryAmountKey.Active(food),
                SubcategoryAmountKey.None,
                SubcategoryAmountKey.Archived,
            ),
            rows.map { it.key }
        )
    }

    @Test
    fun `archived summing to zero produces no row`() {
        val old = Subcategory(
            title = "Old", position = 2.0, categoryId = "c", id = "s2", isArchived = true,
        )
        val older = Subcategory(
            title = "Older", position = 3.0, categoryId = "c", id = "s3", isArchived = true,
        )
        val rows = CategoryStatsCalculator.subcategoryRows(
            mapOf(old to BigInteger.valueOf(5), older to BigInteger.valueOf(-5))
        )
        assertEquals(emptyList<CategoryStatsCalculator.SubcategoryRow>(), rows)
    }

    @Test
    fun `empty period`() {
        assertEquals(
            emptyList<CategoryStatsCalculator.SubcategoryRow>(),
            CategoryStatsCalculator.subcategoryRows(emptyMap())
        )
    }
}
