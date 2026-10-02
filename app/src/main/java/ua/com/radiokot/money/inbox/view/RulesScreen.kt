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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.TextButton

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
        .padding(
            horizontal = 16.dp,
        )
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = 56.dp,
            )
    ) {
        TextButton(
            text = "❌",
            padding = remember { PaddingValues(6.dp) },
            modifier = Modifier
                .clickable(
                    onClick = onCloseClicked,
                )
        )

        Text(
            text = "Payee rules",
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = 16.dp,
                )
        )
    }

    if (ruleItemList.value.isEmpty()) {
        Text(
            text = "No rules yet. Categorize a payment in the inbox " +
                    "with \"Remember\" on to create one.",
            color = Color.Gray,
            modifier = Modifier
                .padding(vertical = 16.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        items(
            items = ruleItemList.value,
            key = ViewPayeeRuleItem::key,
        ) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { onRuleClicked(item) },
                    )
                    .padding(
                        vertical = 10.dp,
                    )
            ) {
                Text(
                    text = "Payee ${item.matchTypeText} “${item.pattern}”",
                    fontSize = 16.sp,
                )
                Text(
                    text = "→ ${item.categoryTitle} · used ${item.hits}×",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
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
