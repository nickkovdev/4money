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

import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import kotlinx.datetime.LocalDateTime
import androidx.compose.ui.text.style.TextOverflow
import ua.com.radiokot.money.R
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.GroupPosition
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.MoneyIconButtonStyle
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.listGroupItem
import ua.com.radiokot.money.uikit.theme.MoneyShapes
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
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
    onSortAsCardsClicked: (() -> Unit)? = null,
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
            icon = R.drawable.ic_tabler_x,
            contentDescription = stringResource(R.string.common_close),
            onClick = onCloseClicked,
        )

        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = stringResource(R.string.inbox_title),
                style = MoneyTheme.typography.headline,
            )
            Text(
                text =
                    if (pendingItemList.value.isEmpty())
                        stringResource(R.string.inbox_nothing_to_sort)
                    else
                        stringResource(R.string.inbox_to_sort, pendingItemList.value.size),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
            )
        }

        MoneyIconButton(
            icon = R.drawable.ic_tabler_adjustments_horizontal,
            contentDescription = stringResource(R.string.inbox_rules),
            onClick = onRulesClicked,
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = MoneySpacing.screen,
            end = MoneySpacing.screen,
            bottom = 24.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        val pending = pendingItemList.value

        if (pending.isNotEmpty() && onSortAsCardsClicked != null) {
            item(key = "sort-cards") {
                MoneyButton(
                    text = stringResource(R.string.inbox_sort_as_cards),
                    icon = R.drawable.ic_tabler_cards,
                    style = MoneyButtonStyle.Filled,
                    onClick = onSortAsCardsClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }

        item(key = "pending-title") {
            SectionHeader(title = stringResource(R.string.inbox_section_pending))
        }

        if (pending.isEmpty()) {
            item(key = "pending-empty") {
                EmptyState(
                    icon = R.drawable.ic_tabler_circle_check,
                    title = stringResource(R.string.inbox_all_sorted),
                    text = stringResource(R.string.inbox_empty_text),
                    modifier = Modifier
                        .clip(MoneyShapes.large)
                        .background(MoneyTheme.colors.surface)
                )
            }
        }

        itemsIndexed(
            items = pending,
            key = { _, item -> item.key },
        ) { index, item ->
            InboxItemRow(
                item = item,
                onClick = { onPendingItemClicked(item) },
                modifier = Modifier
                    .listGroupItem(
                        position = GroupPosition.of(index, pending.size) { true },
                    ),
                trailing = {
                    MoneyIconButton(
                        icon = R.drawable.ic_tabler_x,
                        contentDescription = stringResource(R.string.inbox_dismiss),
                        style = MoneyIconButtonStyle.Plain,
                        size = 36.dp,
                        iconSize = 18.dp,
                        onClick = { onDismissClicked(item) },
                    )
                },
            )
        }

        val cards = cardItemList.value
        if (cards.isNotEmpty()) {
            item(key = "cards-title") {
                SectionHeader(
                    title = stringResource(R.string.inbox_section_cards),
                    modifier = Modifier
                        .padding(top = 12.dp)
                )
            }

            itemsIndexed(
                items = cards,
                key = { _, card -> "card-${card.cardLast4}" },
            ) { index, card ->
                ListRow(
                    title = stringResource(R.string.inbox_card_title, card.cardLast4),
                    subtitle = stringResource(
                        R.string.inbox_card_subtitle,
                        card.accountTitle ?: stringResource(R.string.inbox_most_used_account),
                    ),
                    leading = {
                        IconTile(icon = R.drawable.ic_tabler_credit_card)
                    },
                    trailing = { RowChevron() },
                    onClick = { onCardItemClicked(card) },
                    modifier = Modifier
                        .listGroupItem(
                            position = GroupPosition.of(index, cards.size) { true },
                            dividerStartInset = 16.dp + 36.dp + 14.dp,
                        ),
                )
            }
        }

        val done = doneItemList.value
        if (done.isNotEmpty()) {
            item(key = "done-title") {
                SectionHeader(
                    title = stringResource(R.string.inbox_section_recorded),
                    modifier = Modifier
                        .padding(top = 12.dp)
                )
            }

            itemsIndexed(
                items = done,
                key = { _, item -> "done-${item.key}" },
            ) { index, item ->
                InboxItemRow(
                    item = item,
                    onClick = null,
                    modifier = Modifier
                        .listGroupItem(
                            position = GroupPosition.of(index, done.size) { true },
                        ),
                    trailing = {
                        MoneyIconButton(
                            icon = R.drawable.ic_tabler_arrow_back_up,
                            contentDescription = stringResource(R.string.common_undo),
                            style = MoneyIconButtonStyle.Plain,
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = { onUndoClicked(item) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun InboxItemRow(
    modifier: Modifier = Modifier,
    item: ViewInboxItem,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null)
                Modifier.clickable(onClick = onClick)
            else
                Modifier
        )
        .padding(
            start = MoneySpacing.rowHorizontal,
            end = 6.dp,
            top = 10.dp,
            bottom = 10.dp,
        )
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
    ) {
        Text(
            text =
                if (item.isTitleRawText && LocalPrivacyMode.current)
                    stringResource(R.string.inbox_private_title)
                else
                    item.title,
            style = MoneyTheme.typography.bodyStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val dateText = receivedAtText(item.receivedAt)
        Text(
            text =
                if (item.isForeignCurrency)
                    stringResource(R.string.inbox_item_foreign_currency, dateText)
                else
                    dateText,
            style = MoneyTheme.typography.caption,
            color =
                if (item.isForeignCurrency)
                    MoneyTheme.colors.warning
                else
                    MoneyTheme.colors.ink3,
        )
    }

    val amount = item.amount
    if (amount != null) {
        val amountFormat = rememberViewAmountFormat()
        Text(
            text = amountFormat.formatOrPrivate(
                amount = amount,
                customColor =
                    if (item.isIncoming)
                        MoneyTheme.colors.income
                    else
                        MoneyTheme.colors.expense,
            ),
            style = MoneyTheme.typography.bodyStrong,
            maxLines = 1,
        )
    } else if (item.amountText != null) {
        Text(
            text =
                if (LocalPrivacyMode.current)
                    PrivacyAmounts.MASK
                else
                    item.amountText.resolve(),
            style = MoneyTheme.typography.bodyStrong,
            maxLines = 1,
        )
    }

    trailing()
}

@Composable
fun InboxScreen(
    viewModel: InboxScreenViewModel,
    onSortAsCardsClicked: () -> Unit,
) = InboxScreen(
    onSortAsCardsClicked = onSortAsCardsClicked,
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
            amountText = ViewText.Plain("2,12 USD"),
            receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
            isForeignCurrency = true,
            key = "1",
        ),
        ViewInboxItem(
            title = "Jums ir jauns ziņojums internetbankā",
            amountText = null,
            receivedAt = LocalDateTime(2026, 10, 2, 9, 0),
            isForeignCurrency = false,
            key = "2",
        ),
    ).let(::mutableStateOf),
    doneItemList = listOf(
        ViewInboxItem(
            title = "CAFE EXAMPLE",
            amountText = ViewText.Plain("4,50 EUR"),
            receivedAt = LocalDateTime(2026, 10, 1, 13, 10),
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
