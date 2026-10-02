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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.composeunstyled.Text
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.logic.PrivateAmountDisplay
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode

/**
 * An amount that counts to the new value when it changes.
 */
@Composable
fun AnimatedAmountText(
    amount: ViewAmount,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    customColor: Color? = null,
    maxLines: Int = 1,
    privateAs: PrivateAmountDisplay = PrivateAmountDisplay.Mask,
) {
    val amountFormat = rememberViewAmountFormat()

    if (LocalPrivacyMode.current) {
        Text(
            text = amountFormat.privateText(
                text = PrivacyAmounts.textOf(amount.value, privateAs),
                value = amount.value,
                customColor = customColor,
            ),
            style = style,
            maxLines = maxLines,
            modifier = modifier,
        )
        return
    }

    // animateAmountValueAsState remembers the precision of the first currency.
    key(amount.currency) {
        val animatedValue = animateAmountValueAsState(
            targetAmount = amount,
        )

        Text(
            text = amountFormat(
                value = animatedValue.value,
                currency = amount.currency,
                customColor = customColor,
            ),
            style = style,
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}
