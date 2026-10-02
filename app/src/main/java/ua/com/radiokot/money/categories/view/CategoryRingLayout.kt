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

package ua.com.radiokot.money.categories.view

const val CATEGORY_RING_GRID_COLUMNS = 4

data class CategoryRingLayout<T>(
    val topRow: List<T>,
    /** Two cells (one per ring row) left of the 2×2 ring. */
    val ringLeft: List<T>,
    /** Two cells (one per ring row) right of the 2×2 ring. */
    val ringRight: List<T>,
    val rows: List<List<T>>,
)

/**
 * Places [items] in 4 columns: one regular row, then two rows with one item
 * on each side of a 2×2 ring (left, right, left, right), then regular rows.
 */
fun <T> layoutAroundRing(items: List<T>): CategoryRingLayout<T> {
    val columns = CATEGORY_RING_GRID_COLUMNS
    val ringItems = items.drop(columns).take(4)

    return CategoryRingLayout(
        topRow = items.take(columns),
        ringLeft = ringItems.filterIndexed { index, _ -> index % 2 == 0 },
        ringRight = ringItems.filterIndexed { index, _ -> index % 2 == 1 },
        rows = items.drop(columns + 4).chunked(columns),
    )
}
