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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.TextButton
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
private fun InboxScreen(
    pendingItemList: State<List<ViewInboxItem>>,
    doneItemList: State<List<ViewInboxItem>>,
    cardItemList: State<List<ViewCardAccountItem>>,
    onPendingItemClicked: (ViewInboxItem) -> Unit,
    onDismissClicked: (ViewInboxItem) -> Unit,
    onUndoClicked: (ViewInboxItem) -> Unit,
    onCardItemClicked: (ViewCardAccountItem) -> Unit,
    onRulesClicked: () -> Unit,
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
    val buttonPadding = remember { PaddingValues(6.dp) }

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
            padding = buttonPadding,
            modifier = Modifier
                .clickable(
                    onClick = onCloseClicked,
                )
        )

        Text(
            text = "Inbox",
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = 16.dp,
                )
        )

        TextButton(
            text = "Rules",
            padding = buttonPadding,
            modifier = Modifier
                .clickable(
                    onClick = onRulesClicked,
                )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        if (cardItemList.value.isNotEmpty()) {
            item(key = "cards-title") {
                SectionTitle("Cards")
            }

            items(
                items = cardItemList.value,
                key = { "card-${it.cardLast4}" },
            ) { card ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { onCardItemClicked(card) },
                        )
                        .padding(
                            vertical = 10.dp,
                        )
                ) {
                    Text(
                        text = "Card •${card.cardLast4}",
                        modifier = Modifier
                            .weight(1f)
                    )
                    Text(
                        text = card.accountTitle ?: "Most used account",
                        color = MoneyTheme.colors.onBackgroundSecondary,
                    )
                }
            }
        }

        item(key = "pending-title") {
            SectionTitle("To categorize")
        }

        if (pendingItemList.value.isEmpty()) {
            item(key = "pending-empty") {
                Text(
                    text = "Nothing to categorize",
                    color = MoneyTheme.colors.onBackgroundSecondary,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                )
            }
        }

        items(
            items = pendingItemList.value,
            key = ViewInboxItem::key,
        ) { item ->
            InboxItemRow(
                item = item,
                actionText = "✕",
                onClick = { onPendingItemClicked(item) },
                onActionClicked = { onDismissClicked(item) },
            )
        }

        if (doneItemList.value.isNotEmpty()) {
            item(key = "done-title") {
                SectionTitle("Recorded")
            }

            items(
                items = doneItemList.value,
                key = { "done-${it.key}" },
            ) { item ->
                InboxItemRow(
                    item = item,
                    actionText = "Undo",
                    onClick = null,
                    onActionClicked = { onUndoClicked(item) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
) = Text(
    text = text,
    fontSize = 16.sp,
    fontWeight = FontWeight(500),
    modifier = Modifier
        .padding(
            top = 20.dp,
            bottom = 6.dp,
        )
)

@Composable
private fun InboxItemRow(
    item: ViewInboxItem,
    actionText: String,
    onClick: (() -> Unit)?,
    onActionClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null)
                Modifier.clickable(onClick = onClick)
            else
                Modifier
        )
        .padding(
            vertical = 10.dp,
        )
) {
    Column(
        modifier = Modifier
            .weight(1f)
    ) {
        Text(
            text = item.title,
            fontSize = 16.sp,
        )
        Text(
            text = item.dateText,
            fontSize = 12.sp,
            color = MoneyTheme.colors.onBackgroundSecondary,
        )
    }

    if (item.amountText != null) {
        Text(
            text =
                if (item.isForeignCurrency)
                    "${item.amountText} ⚠"
                else
                    item.amountText,
            modifier = Modifier
                .padding(
                    horizontal = 12.dp,
                )
        )
    }

    TextButton(
        text = actionText,
        padding = remember { PaddingValues(6.dp) },
        modifier = Modifier
            .clickable(
                onClick = onActionClicked,
            )
    )
}

@Composable
fun InboxScreen(
    viewModel: InboxScreenViewModel,
) = InboxScreen(
    pendingItemList = viewModel.pendingItemList.collectAsState(),
    doneItemList = viewModel.doneItemList.collectAsState(),
    cardItemList = viewModel.cardItemList.collectAsState(),
    onPendingItemClicked = remember { viewModel::onPendingItemClicked },
    onDismissClicked = remember { viewModel::onDismissClicked },
    onUndoClicked = remember { viewModel::onUndoClicked },
    onCardItemClicked = remember { viewModel::onCardItemClicked },
    onRulesClicked = remember { viewModel::onRulesClicked },
    onCloseClicked = remember { viewModel::onCloseClicked },
)

@Preview(
    apiLevel = 34,
)
@Composable
private fun InboxScreenPreview(
) = InboxScreen(
    pendingItemList = listOf(
        ViewInboxItem(
            title = "DEEPSEERWEA",
            amountText = "2,12 USD",
            dateText = "2026-10-02 08:06",
            isForeignCurrency = true,
            key = "1",
        ),
        ViewInboxItem(
            title = "Jums ir jauns ziņojums internetbankā",
            amountText = null,
            dateText = "2026-10-02 09:00",
            isForeignCurrency = false,
            key = "2",
        ),
    ).let(::mutableStateOf),
    doneItemList = listOf(
        ViewInboxItem(
            title = "CAFE EXAMPLE",
            amountText = "4,50 EUR",
            dateText = "2026-10-01 13:10",
            isForeignCurrency = false,
            key = "3",
        ),
    ).let(::mutableStateOf),
    cardItemList = listOf(
        ViewCardAccountItem(
            cardLast4 = "0000",
            accountTitle = "Main",
        ),
    ).let(::mutableStateOf),
    onPendingItemClicked = {},
    onDismissClicked = {},
    onUndoClicked = {},
    onCardItemClicked = {},
    onRulesClicked = {},
    onCloseClicked = {},
)
