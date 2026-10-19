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

import ua.com.radiokot.money.util.SternBrocotTreeSearch
import java.util.UUID

/**
 * Plans the DB writes for saving a category's subcategories in list order.
 * Existing subcategories become updates (keeping their archived flag),
 * new ones become inserts with a fresh ID.
 */
object SubcategoryWrites {

    data class Write(
        val id: String,
        val title: String,
        val position: Double,
        val isArchived: Boolean,
        val isInsert: Boolean,
    )

    fun plan(
        subcategories: List<SubcategoryToUpdate>,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<Write> {
        val sternBrocotTree = SternBrocotTreeSearch()

        return subcategories.map { item ->
            sternBrocotTree.goRight()

            Write(
                id = if (item.isNew) newId() else item.id,
                title = item.title,
                position = sternBrocotTree.value,
                isArchived = item.isArchived,
                isInsert = item.isNew,
            )
        }
    }
}
