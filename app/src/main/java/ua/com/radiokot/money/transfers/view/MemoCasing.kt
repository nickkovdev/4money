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

/**
 * Softens shouting bank payee names for display only:
 * "EXAMPLE EMPLOYER LTD (PUBL)" → "Example Employer Ltd (Publ)".
 * Text with any lowercase letter, or with fewer than 4 letters, is kept as is.
 * Words with symbols inside (P&C, 24/7) keep their case.
 */
fun softenAllCaps(text: String): String {
    val letters = text.filter(Char::isLetter)
    if (letters.length < 4 || letters.any(Char::isLowerCase)) {
        return text
    }

    return text
        .split(' ')
        .joinToString(" ") { word ->
            val core = word.trim('(', ')', '"', '\'', ',', '.', ':', ';')
            if (core.isEmpty() || core.any { !it.isLetter() && it != '-' }) {
                word
            } else {
                buildString {
                    var isWordStart = true
                    word.forEach { char ->
                        if (char.isLetter()) {
                            append(if (isWordStart) char.uppercaseChar() else char.lowercaseChar())
                            isWordStart = false
                        } else {
                            append(char)
                            isWordStart = char == '-' || char == '('
                        }
                    }
                }
            }
        }
}
