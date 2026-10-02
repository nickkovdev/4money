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

package ua.com.radiokot.money.transfers.history.view

import org.junit.Assert
import org.junit.Test

class PendingTransferDeletionsTest {

    @Test
    fun schedule_HidesOnce() {
        val deletions = PendingTransferDeletions()
        Assert.assertTrue(deletions.schedule("a"))
        Assert.assertFalse(deletions.schedule("a"))
        Assert.assertEquals(setOf("a"), deletions.hiddenIds)
    }

    @Test
    fun undo_RestoresAndPreventsCommit() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        Assert.assertTrue(deletions.undo("a"))
        Assert.assertEquals(emptySet<String>(), deletions.hiddenIds)
        Assert.assertFalse(deletions.take("a"))
    }

    @Test
    fun take_CommitsOnce_AndCannotBeUndone() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        Assert.assertTrue(deletions.take("a"))
        Assert.assertFalse(deletions.take("a"))
        Assert.assertFalse(deletions.undo("a"))
        // Stays hidden while and after reverting.
        Assert.assertEquals(setOf("a"), deletions.hiddenIds)
        Assert.assertFalse(deletions.schedule("a"))
    }

    @Test
    fun restore_AfterFailedCommit_ShowsAgain() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        deletions.take("a")
        deletions.restore("a")
        Assert.assertEquals(emptySet<String>(), deletions.hiddenIds)
        Assert.assertTrue(deletions.schedule("a"))
    }

    @Test
    fun takeAll_ReturnsOnlyPending() {
        val deletions = PendingTransferDeletions()
        deletions.schedule("a")
        deletions.schedule("b")
        deletions.schedule("c")
        deletions.take("a")
        deletions.undo("b")
        Assert.assertEquals(setOf("c"), deletions.takeAll())
        Assert.assertEquals(emptySet<String>(), deletions.takeAll())
        Assert.assertEquals(setOf("a", "c"), deletions.hiddenIds)
    }
}
