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

package ua.com.radiokot.money.currency.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.logic.PrivateAmountDisplay
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode

/**
 * Formats the amount, or its private replacement while the privacy mode is on.
 */
@Composable
fun ViewAmountFormat.formatOrPrivate(
    amount: ViewAmount,
    customColor: Color? = null,
    privateAs: PrivateAmountDisplay = PrivateAmountDisplay.Mask,
): AnnotatedString =
    if (LocalPrivacyMode.current)
        privateText(
            text = PrivacyAmounts.textOf(amount.value, privateAs),
            value = amount.value,
            customColor = customColor,
        )
    else
        invoke(amount, customColor)
