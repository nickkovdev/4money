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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

enum class KeypadKeyStyle {
    /** Digits and the decimal separator: surface fill, ink. */
    Digit,

    /** Operators, delete, date: raised fill, accent glyph. */
    Operator,

    /** The confirm key: accent (or a custom) fill. */
    Confirm,
    ;
}

/**
 * A filled keypad key. Shows either [text] or [icon].
 *
 * @param confirmColor fill of the [KeypadKeyStyle.Confirm] key, the accent by default
 */
@Composable
fun KeypadKey(
    modifier: Modifier = Modifier,
    text: String? = null,
    @DrawableRes
    icon: Int? = null,
    contentDescription: String? = text,
    style: KeypadKeyStyle = KeypadKeyStyle.Digit,
    isEnabled: Boolean = true,
    confirmColor: Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MoneyTheme.colors
    val targetBackground = when (style) {
        KeypadKeyStyle.Digit -> colors.surface
        KeypadKeyStyle.Operator -> colors.surface2
        KeypadKeyStyle.Confirm -> confirmColor ?: colors.accent
    }
    val background by animateColorAsState(
        targetValue = targetBackground,
        label = "key-background",
    )
    val content = when (style) {
        KeypadKeyStyle.Digit -> colors.ink
        KeypadKeyStyle.Operator -> colors.accent
        KeypadKeyStyle.Confirm -> colors.onAccent
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(MoneySpacing.keypadKeyHeight)
            .alpha(if (isEnabled) 1f else 0.4f)
            .clip(MoneyShapes.key)
            .background(background)
            .then(
                if (onLongClick != null)
                    Modifier.combinedClickable(
                        enabled = isEnabled,
                        role = Role.Button,
                        indication = remember(::ScaleIndication),
                        interactionSource = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                else
                    Modifier.clickable(
                        enabled = isEnabled,
                        role = Role.Button,
                        indication = remember(::ScaleIndication),
                        interactionSource = null,
                        onClick = onClick,
                    )
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = content,
                modifier = Modifier
                    .size(24.dp)
            )
        } else if (text != null) {
            Text(
                text = text,
                style = MoneyTheme.typography.title.copy(
                    fontSize = 22.sp,
                ),
                fontWeight = FontWeight.Medium,
                color = content,
            )
        }
    }
}

@Preview(widthDp = 340)
@Composable
private fun KeypadKeyPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(MoneyTheme.colors.background)
            .padding(16.dp)
    ) {
        KeypadKey(text = "7", onClick = {}, modifier = Modifier.weight(1f))
        KeypadKey(text = "−", style = KeypadKeyStyle.Operator, onClick = {}, modifier = Modifier.weight(1f))
        KeypadKey(
            icon = R.drawable.ic_tabler_backspace,
            contentDescription = "Delete",
            style = KeypadKeyStyle.Operator,
            onClick = {},
            modifier = Modifier.weight(1f)
        )
        KeypadKey(
            icon = R.drawable.ic_tabler_check,
            contentDescription = "Save",
            style = KeypadKeyStyle.Confirm,
            onClick = {},
            modifier = Modifier.weight(1f)
        )
    }
}
