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
import ua.com.radiokot.money.categories.data.Subcategory
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

    private fun pickerSub(id: String, isArchived: Boolean = false) =
        Subcategory(
            title = id,
            position = 0.0,
            categoryId = "category",
            id = id,
            isArchived = isArchived,
        )

    @Test
    fun forPickerHidesArchived() {
        val result = VisibleSubcategories.forPicker(
            listOf(pickerSub("a"), pickerSub("b", true), pickerSub("c")),
            keepSubcategoryId = null,
        )

        assertEquals(listOf("a", "c"), result.map(Subcategory::id))
    }

    @Test
    fun forPickerKeepsTheInitialArchivedOne() {
        val result = VisibleSubcategories.forPicker(
            listOf(pickerSub("a"), pickerSub("b", true), pickerSub("c", true)),
            keepSubcategoryId = "b",
        )

        assertEquals(listOf("a", "b"), result.map(Subcategory::id))
    }

    // The view model passes the subcategory the sheet was opened with,
    // not the current selection, so the chip stays after the user unselects it.
    @Test
    fun keepsTheInitialArchivedOneAfterUnselect() {
        val list = listOf(pickerSub("a"), pickerSub("b", true))
        val initialId = "b"

        // The current selection is null (unselected), the initial id is still passed.
        val result = VisibleSubcategories.forPicker(list, keepSubcategoryId = initialId)

        assertEquals(listOf("a", "b"), result.map(Subcategory::id))
    }
}
