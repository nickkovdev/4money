/* Copyright 2026 Nikita Kovalovs

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

package ua.com.radiokot.money.powersync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TransferInputTest {

    private val complete = mapOf(
        "source_id" to "s",
        "source_amount" to "1",
        "destination_id" to "d",
        "destination_amount" to "1",
        "memo" to null,
        "time" to "2026-11-02T10:00:00Z",
    )

    @Test
    fun completeEntryIsConverted() {
        val input = AtomicCrudSupabaseConnector.TransferInput(
            testCrudEntry(table = "transfers", id = "t1", opData = complete)
        )
        assertEquals("t1", input.id)
        assertEquals("s", input.sourceId)
        assertNull(input.memo)
    }

    @Test
    fun missingFieldIsMalformedEntryNotNpe() {
        try {
            AtomicCrudSupabaseConnector.TransferInput(
                testCrudEntry(table = "transfers", id = "t1", opData = complete - "source_amount")
            )
            fail("Expected MalformedCrudEntryException")
        } catch (e: MalformedCrudEntryException) {
            assertTrue(e.message!!.contains("source_amount"))
        }
    }

    @Test
    fun missingOpDataIsMalformedEntry() {
        try {
            AtomicCrudSupabaseConnector.TransferInput(
                testCrudEntry(table = "transfers", id = "t1", opData = null)
            )
            fail("Expected MalformedCrudEntryException")
        } catch (_: MalformedCrudEntryException) {
        }
    }
}
