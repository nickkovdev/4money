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

package ua.com.radiokot.money.inbox.logic

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.data.PayeeRule

class PayeeWordSelectionTest {

    private fun sel(first: Int, last: Int) =
        PayeeWordSelection(listOf("mcdonalds", "akropole", "rig"), first, last)

    @Test
    fun wholeIsExact() = Assert.assertEquals(
        PayeeRememberChoice("mcdonalds akropole rig", PayeeRule.MatchType.Exact),
        sel(0, 2).toChoice()
    )

    @Test
    fun partIsContains() = Assert.assertEquals(
        PayeeRememberChoice("mcdonalds", PayeeRule.MatchType.Contains),
        sel(0, 0).toChoice()
    )

    @Test
    fun tapLastSelectedRemovesIt() = Assert.assertEquals(sel(0, 1), sel(0, 2).toggle(2))

    @Test
    fun tapFirstSelectedRemovesIt() = Assert.assertEquals(sel(1, 2), sel(0, 2).toggle(0))

    @Test
    fun cannotRemoveTheOnlyWord() = Assert.assertEquals(sel(1, 1), sel(1, 1).toggle(1))

    @Test
    fun interiorTapDoesNothing() = Assert.assertEquals(sel(0, 2), sel(0, 2).toggle(1))

    @Test
    fun tapUnselectedExtendsTheRun() = Assert.assertEquals(sel(0, 2), sel(0, 0).toggle(2))

    @Test
    fun outOfRangeTapDoesNothing() = Assert.assertEquals(sel(0, 0), sel(0, 0).toggle(7))

    @Test
    fun hintPatterns() {
        Assert.assertNull(sel(0, 2).hintPattern)
        Assert.assertEquals("mcdonalds …", sel(0, 0).hintPattern)
        Assert.assertEquals("… akropole …", sel(1, 1).hintPattern)
        Assert.assertEquals("… akropole rig", sel(1, 2).hintPattern)
    }

    @Test
    fun leadingClampsAndHandlesEmpty() {
        Assert.assertNull(PayeeWordSelection.leading("", 1))
        Assert.assertEquals(sel(0, 2), PayeeWordSelection.leading("mcdonalds akropole rig", 9))
        Assert.assertEquals(sel(0, 0), PayeeWordSelection.leading("mcdonalds akropole rig", 0))
    }

    @Test
    fun wholeSelectsEveryWord() {
        Assert.assertNull(PayeeWordSelection.whole(""))
        Assert.assertEquals(sel(0, 2), PayeeWordSelection.whole("mcdonalds akropole rig"))
        Assert.assertTrue(sel(0, 2).isWhole)
        Assert.assertFalse(sel(0, 1).isWhole)
    }
}
