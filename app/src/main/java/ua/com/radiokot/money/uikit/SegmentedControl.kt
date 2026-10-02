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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A pill segmented control: tabs of a header, modes of a picker.
 *
 * @param isFillWidth whether segments share the full width equally,
 * otherwise the control wraps its content.
 */
@Composable
fun SegmentedControl(
    modifier: Modifier = Modifier,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (index: Int) -> Unit,
    isFillWidth: Boolean = false,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier
        .clip(MoneyShapes.pill)
        .background(MoneyTheme.colors.surface)
        .padding(4.dp)
        .selectableGroup()
) {
    options.forEachIndexed { index, option ->
        val isSelected = index == selectedIndex
        val background by animateColorAsState(
            targetValue =
                if (isSelected)
                    MoneyTheme.colors.surface2
                else
                    Color.Transparent,
            label = "segment-background",
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .then(
                    if (isFillWidth)
                        Modifier.weight(1f)
                    else
                        Modifier
                )
                .clip(MoneyShapes.pill)
                .background(background)
                .semantics { selected = isSelected }
                .clickable(
                    role = Role.Tab,
                    onClick = { onSelected(index) },
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                )
        ) {
            Text(
                text = option,
                style =
                    if (isSelected)
                        MoneyTheme.typography.label
                    else
                        MoneyTheme.typography.labelRegular,
                color =
                    if (isSelected)
                        MoneyTheme.colors.ink
                    else
                        MoneyTheme.colors.ink2,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SegmentedPreviewContent() = Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
        .background(MoneyTheme.colors.background)
        .padding(16.dp)
) {
    SegmentedControl(
        options = listOf("Accounts", "Total"),
        selectedIndex = 0,
        onSelected = {},
    )
    SegmentedControl(
        options = listOf("System", "Light", "Dark"),
        selectedIndex = 2,
        onSelected = {},
        isFillWidth = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(widthDp = 320)
@Composable
private fun SegmentedPaperPreview() = MoneyTheme {
    SegmentedPreviewContent()
}

@Preview(widthDp = 320)
@Composable
private fun SegmentedMidnightPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    SegmentedPreviewContent()
}
