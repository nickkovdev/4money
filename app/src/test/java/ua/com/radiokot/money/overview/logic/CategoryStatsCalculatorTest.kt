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
        assertEquals(listOf(fuel, food, null), rows.map { it.subcategory })
        assertEquals(listOf(0.5f, 0.25f, 0.25f), rows.map { it.fraction })
    }

    @Test
    fun `only uncategorized`() {
        val rows = CategoryStatsCalculator.subcategoryRows(mapOf(null to BigInteger.TEN))
        assertEquals(1, rows.size)
        assertEquals(1f, rows.single().fraction)
    }

    @Test
    fun `empty period`() {
        assertEquals(
            emptyList<CategoryStatsCalculator.SubcategoryRow>(),
            CategoryStatsCalculator.subcategoryRows(emptyMap())
        )
    }
}
