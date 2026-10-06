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
import org.junit.Test
import ua.com.radiokot.money.categories.data.SubcategoryToUpdate

class VisibleSubcategoriesTest {

    private fun sub(id: String, isArchived: Boolean = false) =
        SubcategoryToUpdate(id = id, title = id, isNew = false, isArchived = isArchived)

    @Test
    fun activeFirstIsAStablePartition() {
        val result = VisibleSubcategories.activeFirst(
            listOf(sub("a", true), sub("b"), sub("c", true), sub("d"))
        )

        assertEquals(listOf("b", "d", "a", "c"), result.map(SubcategoryToUpdate::id))
    }

    @Test
    fun activeFirstKeepsAllActiveListUnchanged() {
        val input = listOf(sub("a"), sub("b"), sub("c"))

        assertEquals(input, VisibleSubcategories.activeFirst(input))
    }
}
