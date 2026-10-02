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

package ua.com.radiokot.money.colors.data

import org.junit.Assert
import org.junit.Test

class ItemColorSchemeAccentsTest {

    private val schemesByName = HardcodedItemColorSchemeRepository().getItemColorSchemesByName()

    @Test
    fun accent_IsFamilyLevel4() {
        listOf("Red1", "Red3", "Red6").forEach { name ->
            Assert.assertEquals(
                0xFFBF3D3F,
                ItemColorSchemeAccents.accent(schemesByName.getValue(name), isDark = false, schemesByName),
            )
        }
        Assert.assertEquals(
            0xFF35AAB3,
            ItemColorSchemeAccents.accent(schemesByName.getValue("Blue2"), isDark = true, schemesByName),
        )
    }

    @Test
    fun accent_BlackDependsOnTheme() {
        val black1 = schemesByName.getValue("Black1")
        Assert.assertEquals(0xFF797979, ItemColorSchemeAccents.accent(black1, isDark = false, schemesByName))
        Assert.assertEquals(0xFFD6D5D5, ItemColorSchemeAccents.accent(black1, isDark = true, schemesByName))
    }

    @Test
    fun accent_UnknownFamilyFallsBackToOwnPrimary() {
        val custom = ItemColorScheme(name = "Custom7", primary = 0xFF112233, onPrimary = 0xFFFFFFFF)
        Assert.assertEquals(0xFF112233, ItemColorSchemeAccents.accent(custom, isDark = true, schemesByName))
    }

    @Test
    fun onAccent_IsAccentSchemeOnPrimary() {
        Assert.assertEquals(
            0xFFFFF6F6,
            ItemColorSchemeAccents.onAccent(schemesByName.getValue("Red2"), isDark = false, schemesByName),
        )
        Assert.assertEquals(
            0xFF181818,
            ItemColorSchemeAccents.onAccent(schemesByName.getValue("Black5"), isDark = true, schemesByName),
        )
    }

    @Test
    fun blend() {
        Assert.assertEquals(0xFF808080, ItemColorSchemeAccents.blend(0xFFFFFFFF, 0xFF000000, 0.5f))
        Assert.assertEquals(0xFFFFFFFF, ItemColorSchemeAccents.blend(0xFFFFFFFF, 0xFF000000, 1f))
        Assert.assertEquals(0xFF000000, ItemColorSchemeAccents.blend(0xFFFFFFFF, 0xFF000000, 0f))
    }

    @Test
    fun darkLogoColors_ForegroundIsAccent_BackgroundIsDarkTint() {
        schemesByName.values.forEach { scheme ->
            val colors = ItemColorSchemeAccents.darkLogoColors(scheme, schemesByName)
            Assert.assertEquals(
                ItemColorSchemeAccents.accent(scheme, isDark = true, schemesByName),
                colors.foreground,
            )
            Assert.assertNotEquals(scheme.name, colors.foreground, colors.background)
            Assert.assertTrue(
                "${scheme.name} background must be dark",
                luminance(colors.background) < 0.3,
            )
            Assert.assertTrue(
                "${scheme.name} foreground must be lighter than background",
                luminance(colors.foreground) > luminance(colors.background),
            )
        }
    }

    private fun luminance(argb: Long): Double {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    @Test
    fun themedAccent_KeepsLevelsOfAFamilyDistinct() {
        listOf(false, true).forEach { isDark ->
            val colors = (1..6).map { level ->
                ItemColorSchemeAccents.themedAccent(schemesByName.getValue("Red$level"), isDark)
            }
            Assert.assertEquals(
                "isDark=$isDark: $colors",
                6,
                colors.toSet().size,
            )
        }
    }

    @Test
    fun themedAccent_PaleOnLightUsesOnColor_DeepOnDarkUsesOnColor() {
        val red1 = schemesByName.getValue("Red1")
        val red6 = schemesByName.getValue("Red6")
        Assert.assertEquals(red1.onPrimary, ItemColorSchemeAccents.themedAccent(red1, isDark = false))
        Assert.assertEquals(red6.onPrimary, ItemColorSchemeAccents.themedAccent(red6, isDark = true))
        Assert.assertEquals(red1.primary, ItemColorSchemeAccents.themedAccent(red1, isDark = true))
    }
}
