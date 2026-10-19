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

package ua.com.radiokot.money.categories.data

import org.junit.Assert
import org.junit.Test

class SubcategoryWritesTest {
    @Test
    fun keepsArchivedFlagsOfExistingSubcategories() {
        val writes = SubcategoryWrites.plan(
            listOf(
                SubcategoryToUpdate(id = "a", title = "Cafe", isNew = false, isArchived = false),
                SubcategoryToUpdate(id = "b", title = "Bakery", isNew = false, isArchived = true),
                SubcategoryToUpdate(id = "c", title = "Canteen", isNew = false, isArchived = true),
            )
        )
        Assert.assertEquals(listOf(false, true, true), writes.map { it.isArchived })
        Assert.assertEquals(listOf("a", "b", "c"), writes.map { it.id })
        Assert.assertTrue(writes.none { it.isInsert })
    }

    @Test
    fun newSubcategoryIsInsertedActiveWithAFreshId() {
        val writes = SubcategoryWrites.plan(
            listOf(
                SubcategoryToUpdate(id = "b", title = "Bakery", isNew = false, isArchived = true),
                SubcategoryToUpdate(id = "temp", title = "Snacks", isNew = true),
            ),
            newId = { "fresh" },
        )
        Assert.assertEquals(Pair("fresh", true), writes[1].id to writes[1].isInsert)
        Assert.assertFalse(writes[1].isArchived)
        Assert.assertTrue(writes[0].isArchived)
    }

    @Test
    fun positionsIncreaseInListOrder() {
        val writes = SubcategoryWrites.plan(
            (1..4).map { SubcategoryToUpdate(id = "$it", title = "S$it", isNew = false) }
        )
        Assert.assertEquals(writes.map { it.position }.sorted(), writes.map { it.position })
        Assert.assertEquals(4, writes.map { it.position }.distinct().size)
    }

    @Test
    fun copyFromSubcategoryKeepsTheFlag() {
        val sub = Subcategory(title = "Bakery", position = 1.0, categoryId = "food", id = "b", isArchived = true)
        Assert.assertTrue(SubcategoryToUpdate(sub).isArchived)
    }

    @Test
    fun twoSavesInARowKeepTheFlags() {
        val first = SubcategoryWrites.plan(
            listOf(SubcategoryToUpdate(id = "b", title = "Bakery", isNew = false, isArchived = true))
        )
        // What the editor would read back after the first save.
        val reread = first.map { Subcategory(it.title, it.position, "food", it.id, it.isArchived) }
        val second = SubcategoryWrites.plan(reread.map(::SubcategoryToUpdate))
        Assert.assertTrue(second.single().isArchived)
    }
}
