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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A pill chip. Selected: accent tint with accent text,
 * or [selectedColor] fill when the chip carries an item color.
 */
@Composable
fun MoneyChip(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    @DrawableRes
    icon: Int? = null,
    selectedColor: Color? = null,
    isMuted: Boolean = false,
) {
    val colors = MoneyTheme.colors
    val background by animateColorAsState(
        targetValue = when {
            isSelected && selectedColor != null -> selectedColor
            isSelected -> colors.accentTint
            else -> colors.surface
        },
        label = "chip-background",
    )
    val content = when {
        isSelected && selectedColor != null -> colors.background
        isSelected -> colors.accent
        isMuted -> colors.ink2
        else -> colors.ink
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = MoneySpacing.chipHeight)
            .clip(MoneyShapes.pill)
            .background(background)
            .then(
                if (onClick != null)
                    Modifier.clickable(
                        role = Role.Button,
                        indication = remember(::ScaleIndication),
                        interactionSource = null,
                        onClick = onClick,
                    )
                else
                    Modifier
            )
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(16.dp)
            )
        }

        Text(
            text = text,
            style = if (isSelected) MoneyTheme.typography.label else MoneyTheme.typography.labelRegular,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChipsPreviewContent() = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .background(MoneyTheme.colors.background)
        .padding(16.dp)
) {
    MoneyChip(text = "Coffee", isSelected = true, onClick = {})
    MoneyChip(text = "Bakery", onClick = {})
    MoneyChip(text = "Lunch", onClick = {})
    MoneyChip(text = "Add", icon = R.drawable.ic_tabler_plus, isMuted = true, onClick = {})
    MoneyChip(text = "Food", isSelected = true, selectedColor = Color(0xFFFF8A70), onClick = {})
}

@Preview
@Composable
private fun ChipsPaperPreview() = MoneyTheme {
    ChipsPreviewContent()
}

@Preview
@Composable
private fun ChipsMidnightPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    ChipsPreviewContent()
}
