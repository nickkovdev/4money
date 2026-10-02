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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import ua.com.radiokot.money.uikit.theme.MoneyTheme

private sealed interface RingGridCell {
    val key: Any

    class Category(val item: ViewCategoryListItem) : RingGridCell {
        override val key: Any get() = item.key
    }

    data object Add : RingGridCell {
        override val key: Any = "add"
    }
}

@Composable
fun CategoryRingGrid(
    modifier: Modifier = Modifier,
    itemList: State<List<ViewCategoryListItem>>,
    ringSegments: State<List<DonutSegment<ItemColorScheme>>>,
    onItemClicked: (ViewCategoryListItem) -> Unit,
    onItemLongClicked: (ViewCategoryListItem) -> Unit,
    onAddClicked: () -> Unit,
    onRingClicked: () -> Unit,
    ringCenter: @Composable BoxScope.() -> Unit,
) {
    val spaceBy = 6.dp
    val layout = remember {
        derivedStateOf {
            layoutAroundRing(
                itemList.value
                    .fastFilter(ViewCategoryListItem::isNotArchived)
                    .map<ViewCategoryListItem, RingGridCell>(RingGridCell::Category)
                        + RingGridCell.Add
            )
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
        horizontalArrangement = Arrangement.spacedBy(spaceBy),
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
        contentPadding = PaddingValues(spaceBy),
        verticalArrangement = Arrangement.spacedBy(spaceBy),
        modifier = modifier,
    ) {
        item(key = "top") {
            CellRow(layout.value.topRow)
        }

        item(key = "ring") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spaceBy),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spaceBy),
                    modifier = Modifier.weight(1f),
                ) {
                    layout.value.ringLeft.forEach { Cell(it, Modifier.fillMaxWidth()) }
                }

                DonutRing(
                    segments = coloredSegments,
                    trackColor = MoneyTheme.colors.chartOther,
                    content = ringCenter,
                    modifier = Modifier
                        .weight(2f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onRingClicked)
                        .padding(4.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(spaceBy),
                    modifier = Modifier.weight(1f),
                ) {
                    layout.value.ringRight.forEach { Cell(it, Modifier.fillMaxWidth()) }
                }
            }
        }

        items(
            count = layout.value.rows.size,
            key = { index -> "row-$index" },
        ) { index ->
            CellRow(layout.value.rows[index])
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
