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

package ua.com.radiokot.money.colors.view

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import ua.com.radiokot.money.colors.data.DrawableResItemIconRepository
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.colors.data.ItemIconCategory

@Composable
fun ItemLogoIconPicker(
    modifier: Modifier = Modifier,
    iconCategories: List<ItemIconCategory>,
    itemTitle: String,
    colorScheme: State<ItemColorScheme>,
    selectedIcon: State<ItemIcon?>,
    onIconClicked: (ItemIcon?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val noIconAndIcons = remember(iconCategories) {
        listOf(listOf<ItemIcon?>(null)) + iconCategories
    }

    val selectionIndicatorEnterTransition = remember {
        scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
            )
        )
    }
    val selectionIndicatorExitTransition = remember {
        scaleOut() + fadeOut(
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
            )
        )
    }

    val columnCount = 6

    val ringColor = MoneyTheme.colors.ink

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        noIconAndIcons.forEachIndexed { categoryIndex, categoryIcons ->
            val groupName = categoryIcons.firstOrNull()?.name?.substringBefore('_')

            item(
                key = "label-$categoryIndex",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = iconGroupLabel(groupName),
                    style = MoneyTheme.typography.overline,
                    color = MoneyTheme.colors.ink3,
                    modifier = Modifier
                        .padding(
                            start = 4.dp,
                            top =
                                if (categoryIndex == 0)
                                    0.dp
                                else
                                    12.dp,
                        )
                )
            }

            itemsIndexed(
                items = categoryIcons,
                key = { _, icon -> icon?.name ?: "noicon" },
            ) { _, icon ->

                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                ) {
                    val tileSize = min(maxWidth, maxHeight)

                    ItemLogo(
                        title = itemTitle,
                        colorScheme = colorScheme.value,
                        icon = icon,
                        modifier = Modifier
                            .size(tileSize)
                            .clip(MoneyShapes.itemTile)
                            .clickable(
                                onClick = {
                                    onIconClicked(icon)
                                },
                            )
                    )

                    AnimatedVisibility(
                        visible = icon == selectedIcon.value,
                        enter = selectionIndicatorEnterTransition,
                        exit = selectionIndicatorExitTransition,
                        label = "selection-indicator",
                    ) {
                        Box(
                            modifier = Modifier
                                .size(tileSize)
                                .border(
                                    width = 2.dp,
                                    color = ringColor,
                                    shape = MoneyShapes.itemTile,
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * The first group is "no icon" (the letter), others are named by the icon name prefix.
 */
private fun iconGroupLabel(groupName: String?): String =
    when (groupName) {
        null -> "LETTER"
        else -> groupName.uppercase()
    }

@Preview
@Composable
private fun Preview(

) {

    val selectedIcon = remember { mutableStateOf<ItemIcon?>(null) }

    ItemLogoIconPicker(
        itemTitle = "My",
        iconCategories =
            DrawableResItemIconRepository()
                .getItemIconCategories(),
        selectedIcon = selectedIcon,
        colorScheme =
            HardcodedItemColorSchemeRepository()
                .getItemColorSchemesByName()
                ["Pink1"]!!
                .let(::mutableStateOf),
        onIconClicked = { selectedIcon.value = it },
    )
}
