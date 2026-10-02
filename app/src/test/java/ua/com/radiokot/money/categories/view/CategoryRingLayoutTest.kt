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

import org.junit.Assert
import org.junit.Test

class CategoryRingLayoutTest {

    private fun items(count: Int) = (1..count).toList()

    @Test
    fun empty() {
        Assert.assertEquals(
            CategoryRingLayout<Int>(emptyList(), emptyList(), emptyList(), emptyList()),
            layoutAroundRing(items(0)),
        )
    }

    @Test
    fun onlyTopRow() {
        Assert.assertEquals(
            CategoryRingLayout(listOf(1, 2, 3), emptyList(), emptyList(), emptyList()),
            layoutAroundRing(items(3)),
        )
    }

    @Test
    fun partialRing() {
        Assert.assertEquals(
            CategoryRingLayout(listOf(1, 2, 3, 4), listOf(5, 7), listOf(6), emptyList()),
            layoutAroundRing(items(7)),
        )
    }

    @Test
    fun fullRing_ThenRows() {
        Assert.assertEquals(
            CategoryRingLayout(
                topRow = listOf(1, 2, 3, 4),
                ringLeft = listOf(5, 7),
                ringRight = listOf(6, 8),
                rows = listOf(listOf(9, 10, 11, 12), listOf(13)),
            ),
            layoutAroundRing(items(13)),
        )
    }
}
