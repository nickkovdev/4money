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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemColorSchemeAccents
import ua.com.radiokot.money.uikit.chart.DonutRing
import ua.com.radiokot.money.uikit.chart.DonutSegment
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

const val CATEGORY_RING_GRID_COLUMNS = 4

private sealed interface RingGridCell {
    val key: Any

    class Category(val item: ViewCategoryListItem) : RingGridCell {
        override val key: Any get() = item.key
    }

    data object Add : RingGridCell {
        override val key: Any = "add"
    }
}

/**
 * The ring on top, the expense/income switch under it,
 * then the categories in 4 even columns, so any number of them lines up.
 */
@Composable
fun CategoryRingGrid(
    modifier: Modifier = Modifier,
    itemList: State<List<ViewCategoryListItem>>,
    ringSegments: State<List<DonutSegment<ItemColorScheme>>>,
    onItemClicked: (ViewCategoryListItem) -> Unit,
    onItemLongClicked: (ViewCategoryListItem) -> Unit,
    onAddClicked: () -> Unit,
    onRingClicked: () -> Unit,
    modeSwitch: @Composable () -> Unit,
    ringCenter: @Composable BoxScope.() -> Unit,
) {
    val rowGap = 14.dp
    val columnGap = 6.dp
    val rows = remember {
        derivedStateOf {
            (itemList.value
                .fastFilter(ViewCategoryListItem::isNotArchived)
                .map<ViewCategoryListItem, RingGridCell>(RingGridCell::Category)
                    + RingGridCell.Add)
                .chunked(CATEGORY_RING_GRID_COLUMNS)
        }
    }
    val archivedRows = remember {
        derivedStateOf {
            itemList.value
                .fastFilter(ViewCategoryListItem::isArchived)
                .map(RingGridCell::Category)
                .chunked(CATEGORY_RING_GRID_COLUMNS)
        }
    }
    val isArchiveExpanded = remember { mutableStateOf(false) }
    val isDark = MoneyTheme.colors.isDark
    val coloredSegments = remember(ringSegments.value, isDark) {
        ringSegments.value.map { segment ->
            DonutSegment(
                key = Color(ItemColorSchemeAccents.themedAccent(segment.key, isDark)),
                startAngle = segment.startAngle,
                sweepAngle = segment.sweepAngle,
            )
        }
    }

    @Composable
    fun Cell(cell: RingGridCell, cellModifier: Modifier) = when (cell) {
        is RingGridCell.Category ->
            CategoryListItem(
                item = cell.item,
                modifier = cellModifier
                    .combinedClickable(
                        onClick = { onItemClicked(cell.item) },
                        onLongClick = { onItemLongClicked(cell.item) },
                    )
            )

        RingGridCell.Add ->
            AddItem(
                modifier = cellModifier
                    .clickable(onClick = onAddClicked)
            )
    }

    @Composable
    fun CellRow(cells: List<RingGridCell>) = Row(
        horizontalArrangement = Arrangement.spacedBy(columnGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        cells.forEach { cell ->
            Box(modifier = Modifier.weight(1f)) {
                Cell(cell, Modifier.fillMaxWidth())
            }
        }
        repeat(CATEGORY_RING_GRID_COLUMNS - cells.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = MoneySpacing.screen - columnGap,
            end = MoneySpacing.screen - columnGap,
            top = 4.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(rowGap),
        modifier = modifier,
    ) {
        item(key = "ring") {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                DonutRing(
                    segments = coloredSegments,
                    trackColor = MoneyTheme.colors.surface,
                    strokeWidth = 18.dp,
                    content = ringCenter,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRingClicked)
                        .padding(2.dp),
                )
            }
        }

        item(key = "mode") {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 4.dp,
                    )
            ) {
                modeSwitch()
            }
        }

        items(
            count = rows.value.size,
            key = { index -> "row-$index" },
        ) { index ->
            CellRow(rows.value[index])
        }

        if (archivedRows.value.isNotEmpty()) {
            item(key = "archive") {
                ArchiveHeader(isArchiveExpanded = isArchiveExpanded)
            }

            if (isArchiveExpanded.value) {
                items(
                    count = archivedRows.value.size,
                    key = { index -> "archive-row-$index" },
                ) { index ->
                    CellRow(archivedRows.value[index])
                }
            }
        }
    }
}
