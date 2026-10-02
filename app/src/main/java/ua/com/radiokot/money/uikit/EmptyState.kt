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

package ua.com.radiokot.money.uikit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A centered empty state: an accent icon tile, a title and an explanation.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    @DrawableRes
    icon: Int,
    title: String,
    text: String? = null,
    action: (@Composable () -> Unit)? = null,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier
        .fillMaxWidth()
        .padding(24.dp)
) {
    IconTile(
        icon = icon,
        size = 72.dp,
    )

    Text(
        text = title,
        style = MoneyTheme.typography.title,
        textAlign = TextAlign.Center,
    )

    if (text != null) {
        Text(
            text = text,
            style = MoneyTheme.typography.labelRegular,
            color = MoneyTheme.colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 280.dp)
        )
    }

    action?.invoke()
}

@Preview(widthDp = 340)
@Composable
private fun EmptyStatePreview() = MoneyTheme(colors = MidnightMoneyColors) {
    EmptyState(
        icon = R.drawable.ic_tabler_check,
        title = "All sorted",
        text = "New bank payments will appear here as cards.",
        modifier = Modifier
            .background(MoneyTheme.colors.background)
    )
}
