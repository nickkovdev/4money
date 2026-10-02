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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * An on/off switch: accent track when on, raised track when off.
 *
 * @param onToggled null when the whole row handles the toggle
 */
@Composable
fun MoneySwitch(
    modifier: Modifier = Modifier,
    isOn: Boolean,
    onToggled: ((Boolean) -> Unit)?,
    isEnabled: Boolean = true,
) {
    val colors = MoneyTheme.colors
    val track by animateColorAsState(
        targetValue =
            if (isOn)
                colors.accent
            else
                colors.surface2,
        label = "switch-track",
    )
    val thumb by animateColorAsState(
        targetValue =
            if (isOn)
                colors.onAccent
            else
                colors.ink3,
        label = "switch-thumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue =
            if (isOn)
                20.dp
            else
                0.dp,
        animationSpec = spring(),
        label = "switch-thumb-offset",
    )

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .alpha(if (isEnabled) 1f else 0.45f)
            .size(
                width = 52.dp,
                height = 32.dp,
            )
            .clip(MoneyShapes.pill)
            .background(track)
            .then(
                if (onToggled != null)
                    Modifier.toggleable(
                        value = isOn,
                        enabled = isEnabled,
                        role = Role.Switch,
                        onValueChange = onToggled,
                    )
                else
                    Modifier
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .background(
                    color = thumb,
                    shape = MoneyShapes.circle,
                )
        )
    }
}

/**
 * A selection mark for single-choice lists: an accent disc with a check when selected,
 * an empty ring otherwise.
 */
@Composable
fun SelectionMark(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
) {
    val colors = MoneyTheme.colors
    val fill by animateColorAsState(
        targetValue =
            if (isSelected)
                colors.accent
            else
                Color.Transparent,
        label = "selection-mark",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .clip(MoneyShapes.circle)
            .background(fill)
            .then(
                if (!isSelected)
                    Modifier.border(
                        width = 2.dp,
                        color = colors.line,
                        shape = MoneyShapes.circle,
                    )
                else
                    Modifier
            )
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_check),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier
                    .size(16.dp)
            )
        }
    }
}

@Preview
@Composable
private fun SwitchPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MoneyTheme.colors.background)
            .padding(16.dp)
    ) {
        MoneySwitch(isOn = true, onToggled = {})
        MoneySwitch(isOn = false, onToggled = {})
        SelectionMark(isSelected = true)
        SelectionMark(isSelected = false)
    }
}
