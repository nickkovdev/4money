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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

enum class MoneyButtonStyle {
    /**
     * The main action of a screen: accent fill.
     */
    Filled,

    /**
     * Secondary actions: surface fill.
     */
    Tonal,

    /**
     * Destructive actions: expense tint with expense text.
     */
    Danger,

    /**
     * Low-emphasis actions: no fill, accent text.
     */
    Text,
    ;
}

/**
 * A pill button. Disabled buttons fade, they never look like outlines.
 */
@Composable
fun MoneyButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    style: MoneyButtonStyle = MoneyButtonStyle.Tonal,
    @DrawableRes
    icon: Int? = null,
    isEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = 20.dp,
        vertical = 13.dp,
    ),
) {
    val colors = MoneyTheme.colors
    val (background, content) = when (style) {
        MoneyButtonStyle.Filled -> colors.accent to colors.onAccent
        MoneyButtonStyle.Tonal -> colors.surface to colors.ink
        MoneyButtonStyle.Danger -> colors.expenseTint to colors.expense
        MoneyButtonStyle.Text -> Color.Transparent to colors.accent
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = MoneySpacing.buttonHeight)
            .alpha(if (isEnabled) 1f else 0.45f)
            .clip(MoneyShapes.pill)
            .background(background)
            .clickable(
                enabled = isEnabled,
                role = Role.Button,
                indication = remember(::ScaleIndication),
                interactionSource = null,
                onClick = onClick,
            )
            .padding(contentPadding)
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(MoneySpacing.iconSmall)
            )
        }

        Text(
            text = text,
            style = MoneyTheme.typography.label.copy(
                fontSize = MoneyTheme.typography.bodyStrong.fontSize,
            ),
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class MoneyIconButtonStyle {
    /**
     * Surface circle, ink icon: the default for top bars.
     */
    Tonal,

    /**
     * Accent circle, on-accent icon: add, confirm.
     */
    Filled,

    /**
     * No fill: for icons inside rows and fields.
     */
    Plain,
    ;
}

/**
 * A round icon button, 44 dp by default to stay comfortably tappable.
 */
@Composable
fun MoneyIconButton(
    modifier: Modifier = Modifier,
    @DrawableRes
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    style: MoneyIconButtonStyle = MoneyIconButtonStyle.Tonal,
    isEnabled: Boolean = true,
    size: Dp = MoneySpacing.iconButton,
    iconSize: Dp = MoneySpacing.icon,
    tint: Color? = null,
) {
    val colors = MoneyTheme.colors
    val (background, content) = when (style) {
        MoneyIconButtonStyle.Tonal -> colors.surface to colors.ink
        MoneyIconButtonStyle.Filled -> colors.accent to colors.onAccent
        MoneyIconButtonStyle.Plain -> Color.Transparent to colors.ink2
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (isEnabled) 1f else 0.4f)
            .clip(MoneyShapes.circle)
            .background(background)
            .clickable(
                enabled = isEnabled,
                role = Role.Button,
                indication = remember(::ScaleIndication),
                interactionSource = null,
                onClick = onClick,
            )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint ?: content,
            modifier = Modifier
                .size(iconSize)
        )
    }
}

@Composable
private fun ButtonsPreviewContent() = Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
        .background(MoneyTheme.colors.background)
        .padding(16.dp)
) {
    MoneyButton(
        text = "Save",
        style = MoneyButtonStyle.Filled,
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
    MoneyButton(
        text = "Add range",
        icon = R.drawable.ic_tabler_plus,
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
    MoneyButton(
        text = "Sign out",
        style = MoneyButtonStyle.Danger,
        icon = R.drawable.ic_tabler_logout,
        onClick = {},
    )
    MoneyButton(
        text = "Undo",
        style = MoneyButtonStyle.Text,
        onClick = {},
    )
    MoneyButton(
        text = "Disabled",
        style = MoneyButtonStyle.Filled,
        isEnabled = false,
        onClick = {},
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoneyIconButton(
            icon = R.drawable.ic_tabler_user_circle,
            contentDescription = "Profile",
            onClick = {},
        )
        MoneyIconButton(
            icon = R.drawable.ic_tabler_plus,
            contentDescription = "Add",
            style = MoneyIconButtonStyle.Filled,
            onClick = {},
        )
        MoneyIconButton(
            icon = R.drawable.ic_tabler_x,
            contentDescription = "Close",
            style = MoneyIconButtonStyle.Plain,
            onClick = {},
        )
        MoneyIconButton(
            icon = R.drawable.ic_tabler_chevron_right,
            contentDescription = "Next",
            isEnabled = false,
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun ButtonsPaperPreview() = MoneyTheme {
    ButtonsPreviewContent()
}

@Preview
@Composable
private fun ButtonsMidnightPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    ButtonsPreviewContent()
}
