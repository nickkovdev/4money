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

package ua.com.radiokot.money.inbox.templates.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateFieldsTest {

    @Test
    fun roundTripWithCard() {
        val fields = TemplateFields(amount = 1, currency = 2, payee = 3, card = 4, hasTimestamp = true)

        assertEquals(fields, TemplateFields.fromJson(fields.toJson()))
    }

    @Test
    fun roundTripWithoutCard() {
        val fields = TemplateFields(amount = 2, currency = 3, payee = 1)
        val parsed = TemplateFields.fromJson(fields.toJson())

        assertEquals(fields, parsed)
        assertNull(parsed.card)
        assertEquals(false, parsed.hasTimestamp)
    }

    @Test
    fun ignoresUnknownKeys() {
        assertEquals(
            TemplateFields(amount = 1, currency = 2, payee = 3),
            TemplateFields.fromJson("""{"amount":1,"currency":2,"payee":3,"extra":5}"""),
        )
    }

    @Test
    fun parsesServerStyleJsonWithSpaces() {
        val parsed = TemplateFields.fromJson(
            """{"amount": 1, "currency": 2, "payee": 3, "card": 4, "hasTimestamp": true}"""
        )

        assertEquals(TemplateFields(1, 2, 3, 4, true), parsed)
        assertTrue(parsed.hasTimestamp)
    }
}
