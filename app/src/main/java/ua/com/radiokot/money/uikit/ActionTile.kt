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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A square-ish action in a grid of an action sheet: tinted icon above a label.
 */
@Composable
fun ActionTile(
    modifier: Modifier = Modifier,
    @DrawableRes
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = MoneyTheme.colors.accent,
    tileBackground: Color = MoneyTheme.colors.accentTint,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
        .clip(MoneyShapes.large)
        .background(MoneyTheme.colors.surface)
        .clickable(
            role = Role.Button,
            indication = remember(::ScaleIndication),
            interactionSource = null,
            onClick = onClick,
        )
        .padding(
            horizontal = 8.dp,
            vertical = 14.dp,
        )
) {
    IconTile(
        icon = icon,
        tint = tint,
        background = tileBackground,
        size = 40.dp,
    )

    Text(
        text = label,
        style = MoneyTheme.typography.label,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(widthDp = 340)
@Composable
private fun ActionTilePreview() = MoneyTheme(colors = MidnightMoneyColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(MoneyTheme.colors.background)
            .padding(16.dp)
    ) {
        ActionTile(
            icon = R.drawable.ic_tabler_pencil,
            label = "Edit",
            onClick = {},
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = R.drawable.ic_tabler_arrow_up_right,
            label = "Expense",
            tint = MoneyTheme.colors.expense,
            tileBackground = MoneyTheme.colors.expenseTint,
            onClick = {},
            modifier = Modifier.weight(1f),
        )
    }
}
