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

package ua.com.radiokot.money.inbox.view

import androidx.compose.foundation.lazy.itemsIndexed
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.GroupPosition
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.listGroupItem
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
private fun RulesScreen(
    ruleItemList: State<List<ViewPayeeRuleItem>>,
    onRuleClicked: (ViewPayeeRuleItem) -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .add(WindowInsets.statusBars)
        )
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 12.dp,
            )
    ) {
        MoneyIconButton(
            icon = R.drawable.ic_tabler_arrow_left,
            contentDescription = "Back",
            onClick = onCloseClicked,
        )

        Text(
            text = "Payee rules",
            style = MoneyTheme.typography.headline,
            modifier = Modifier
                .weight(1f)
        )
    }

    if (ruleItemList.value.isEmpty()) {
        EmptyState(
            icon = R.drawable.ic_tabler_adjustments_horizontal,
            title = "No rules yet",
            text = "Categorize a payment from the inbox with Remember on to create one.",
        )
    }

    val items = ruleItemList.value

    LazyColumn(
        contentPadding = PaddingValues(
            start = MoneySpacing.screen,
            end = MoneySpacing.screen,
            bottom = 24.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.key },
        ) { index, item ->
            ListRow(
                title = item.displayPattern,
                subtitle = buildString {
                    append(
                        if (item.matchTypeText == "contains")
                            "Contains · "
                        else
                            "Exact · "
                    )
                    append(item.categoryTitle)
                    if (item.hits > 0) {
                        append(" · used ")
                        append(item.hits)
                        append("×")
                    }
                },
                leading = {
                    IconTile(icon = R.drawable.ic_tabler_receipt)
                },
                trailing = { RowChevron() },
                onClick = { onRuleClicked(item) },
                modifier = Modifier
                    .listGroupItem(
                        position = GroupPosition.of(index, items.size) { true },
                        dividerStartInset = 16.dp + 36.dp + 14.dp,
                    ),
            )
        }
    }
}

@Composable
fun RulesScreen(
    viewModel: RulesScreenViewModel,
) = RulesScreen(
    ruleItemList = viewModel.ruleItemList.collectAsState(),
    onRuleClicked = remember { viewModel::onRuleClicked },
    onCloseClicked = remember { viewModel::onCloseClicked },
)

@Preview(
    apiLevel = 34,
)
@Composable
private fun RulesScreenPreview(
) = RulesScreen(
    ruleItemList = listOf(
        ViewPayeeRuleItem(
            pattern = "deepseerwea",
            matchTypeText = "is",
            categoryTitle = "Services / AI",
            hits = 4,
            key = "1",
        ),
        ViewPayeeRuleItem(
            pattern = "foodo",
            matchTypeText = "contains",
            categoryTitle = "Food",
            hits = 12,
            key = "2",
        ),
    ).let(::mutableStateOf),
    onRuleClicked = {},
    onCloseClicked = {},
)
