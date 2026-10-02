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

package ua.com.radiokot.money.transfers.view

import org.junit.Assert
import org.junit.Test

class MemoCasingTest {

    @Test
    fun allCaps_IsTitleCased() {
        Assert.assertEquals(
            "Example Employer Ltd (Publ) Rigas Filiale",
            softenAllCaps("EXAMPLE EMPLOYER LTD (PUBL) RIGAS FILIALE"),
        )
        Assert.assertEquals(
            "Fuelstop Riga",
            softenAllCaps("FUELSTOP RIGA"),
        )
        Assert.assertEquals(
            "Rimi-Mols",
            softenAllCaps("RIMI-MOLS"),
        )
        Assert.assertEquals(
            "Продукты Магазин",
            softenAllCaps("ПРОДУКТЫ МАГАЗИН"),
        )
    }

    @Test
    fun mixedCase_IsKept() {
        Assert.assertEquals("Example SIA", softenAllCaps("Example SIA"))
        Assert.assertEquals("coffee with Anna", softenAllCaps("coffee with Anna"))
    }

    @Test
    fun shortOrSymbolic_IsKept() {
        Assert.assertEquals("SEB", softenAllCaps("SEB"))
        Assert.assertEquals("", softenAllCaps(""))
        Assert.assertEquals("24/7 Shop", softenAllCaps("24/7 SHOP"))
    }
}
