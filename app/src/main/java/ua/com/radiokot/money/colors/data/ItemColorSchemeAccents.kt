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

import kotlin.math.roundToInt

data class DarkItemLogoColors(
    val background: Long,
    val foreground: Long,
)

/**
 * Saturated colors derived from the pastel [ItemColorScheme]s:
 * the accent of a scheme is its family's level-4 entry (e.g. Red2 → Red4).
 */
object ItemColorSchemeAccents {

    /**
     * The surface the dark avatar tint is blended over.
     * Matches DarkMoneyColors.surface.
     */
    const val DARK_LOGO_SURFACE: Long = 0xFF1E1E1E
    private const val DARK_LOGO_TINT_ALPHA = 0.24f

    private val defaultSchemesByName: Map<String, ItemColorScheme> by lazy {
        HardcodedItemColorSchemeRepository().getItemColorSchemesByName()
    }

    private fun accentSchemeName(scheme: ItemColorScheme, isDark: Boolean): String {
        val family = scheme.name.trimEnd(Char::isDigit)
        return if (family == "Black")
            if (isDark) "Black2" else "Black4"
        else
            family + "4"
    }

    fun accent(
        scheme: ItemColorScheme,
        isDark: Boolean,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): Long =
        schemesByName[accentSchemeName(scheme, isDark)]?.primary
            ?: scheme.primary

    /**
     * @return a color readable on top of [accent].
     */
    fun onAccent(
        scheme: ItemColorScheme,
        isDark: Boolean,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): Long =
        schemesByName[accentSchemeName(scheme, isDark)]?.onPrimary
            ?: scheme.onPrimary

    fun darkLogoColors(
        scheme: ItemColorScheme,
        schemesByName: Map<String, ItemColorScheme> = defaultSchemesByName,
    ): DarkItemLogoColors {
        val accent = accent(scheme, isDark = true, schemesByName)

        return DarkItemLogoColors(
            background = blend(
                top = accent,
                bottom = DARK_LOGO_SURFACE,
                alpha = DARK_LOGO_TINT_ALPHA,
            ),
            foreground = accent,
        )
    }

    /**
     * @return opaque ARGB of [top] drawn with [alpha] over opaque [bottom].
     */
    fun blend(top: Long, bottom: Long, alpha: Float): Long {
        fun channel(shift: Int): Long {
            val t = (top shr shift) and 0xFF
            val b = (bottom shr shift) and 0xFF
            return (t * alpha + b * (1 - alpha)).roundToInt().toLong() and 0xFF
        }

        return 0xFF000000 or
                (channel(16) shl 16) or
                (channel(8) shl 8) or
                channel(0)
    }
}
