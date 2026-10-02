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

package ua.com.radiokot.money.categories.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemColorSchemeAccents
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.colors.view.itemAccentColor
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun SelectableSubcategoryRow(
    modifier: Modifier = Modifier,
    itemList: State<List<ViewSelectableSubcategoryListItem>>,
    colorScheme: ItemColorScheme,
    icon: ItemIcon? = null,
    onItemClicked: (ViewSelectableSubcategoryListItem) -> Unit,
) {
    val rowState = rememberLazyListState()
    val space = 12.dp

    // Place the selected subcategory more or less at the center
    // if it is not currently visible.
    // Especially useful in the edit mode when the subcategory
    // is already selected
    LaunchedEffect(itemList.value) {
        val selectedSubcategoryIndex = itemList
            .value
            .indexOfFirst(ViewSelectableSubcategoryListItem::isSelected)

        if (selectedSubcategoryIndex < 0) {
            return@LaunchedEffect
        }

        val rowLayoutInfo = rowState.layoutInfo
        val rowStartOffset = rowLayoutInfo.viewportStartOffset
        val rowEndOffset = rowLayoutInfo.viewportEndOffset

        val isSelectedSubcategoryFullyVisible =
            rowLayoutInfo
                .visibleItemsInfo
                .any { visibleItem ->
                    visibleItem.index == selectedSubcategoryIndex
                            && visibleItem.offset >= rowStartOffset
                            && (visibleItem.offset + visibleItem.size) <= rowEndOffset
                }

        if (!isSelectedSubcategoryFullyVisible) {
            rowState.animateScrollToItem(
                index = selectedSubcategoryIndex,
                scrollOffset = -((rowEndOffset - rowStartOffset) / 3),
            )
        }
    }

    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(space, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(
            horizontal = space,
        ),
        modifier = modifier
    ) {

        items(
            items = itemList.value,
            key = ViewSelectableSubcategoryListItem::key,
        ) { item ->
            SelectableSubcategoryListItem(
                item = item,
                colorScheme = colorScheme,
                icon = icon,
                modifier = Modifier
                    .clickable(
                        onClick = {
                            onItemClicked(item)
                        },
                    )
            )
        }
    }
}

@Composable
@Preview(
    widthDp = 300,
)
private fun SelectableSubcategoryRowPreview() {
    val items = ViewSelectableSubcategoryListItemPreviewParameterProvider().values.toList()

    SelectableSubcategoryRow(
        itemList = items.let(::mutableStateOf),
        colorScheme = HardcodedItemColorSchemeRepository()
            .getItemColorSchemesByName()
            .getValue("Purple3"),
        onItemClicked = {},
    )
}

@Composable
private fun SelectableSubcategoryListItem(
    modifier: Modifier = Modifier,
    item: ViewSelectableSubcategoryListItem,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
) {
    val isDark = MoneyTheme.colors.isDark
    val accentColor = itemAccentColor(colorScheme)
    val onAccentColor = remember(colorScheme, isDark) {
        Color(ItemColorSchemeAccents.onAccent(colorScheme, isDark))
    }
    val contentColor =
        if (item.isSelected)
            onAccentColor
        else
            accentColor
    val shape = RoundedCornerShape(percent = 50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .run {
                if (item.isSelected)
                    background(color = accentColor, shape = shape)
                else
                    border(width = 1.dp, color = accentColor, shape = shape)
            }
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp,
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon.resId),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = item.title,
            color = contentColor,
            singleLine = true,
            maxLines = 1,
        )
    }
}
