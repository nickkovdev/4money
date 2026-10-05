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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.colors.view.itemLogoColors
import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.home.view.HomeProfileButton
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.GroupPosition
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.MoneyIconButtonStyle
import ua.com.radiokot.money.uikit.ScaleIndication
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.listGroupItem
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
private fun InboxTabScreen(
    pendingItemList: State<List<ViewInboxTabPending>>,
    doneItemList: State<List<ViewInboxItem>>,
    counts: State<InboxTabItems.Counts>,
    onAcceptClicked: (ViewInboxTabPending) -> Unit,
    onOtherClicked: (ViewInboxTabPending) -> Unit,
    onDismissClicked: (ViewInboxTabPending) -> Unit,
    onUndoClicked: (ViewInboxItem) -> Unit,
    onSortAsCardsClicked: () -> Unit,
    onRulesClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .fillMaxSize()
) {
    val pending = pendingItemList.value
    val done = doneItemList.value

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 12.dp,
            )
    ) {
        HomeProfileButton()

        Spacer(
            modifier = Modifier
                .weight(1f)
        )

        MoneyIconButton(
            icon = R.drawable.ic_tabler_adjustments_horizontal,
            contentDescription = stringResource(R.string.inbox_rules),
            onClick = onRulesClicked,
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MoneySpacing.screen,
                end = MoneySpacing.screen,
                top = 4.dp,
                bottom = 12.dp,
            )
    ) {
        Text(
            text = stringResource(R.string.inbox_title),
            style = MoneyTheme.typography.headline,
        )

        val pendingCount = counts.value.pending
        val doneTodayCount = counts.value.doneToday
        Text(
            text = listOfNotNull(
                if (pendingCount > 0)
                    pluralStringResource(R.plurals.inbox_subtitle_waiting, pendingCount, pendingCount)
                else
                    stringResource(R.string.inbox_nothing_to_sort),
                if (doneTodayCount > 0)
                    pluralStringResource(R.plurals.inbox_subtitle_recorded_today, doneTodayCount, doneTodayCount)
                else
                    null,
            ).joinToString(" · "),
            style = MoneyTheme.typography.caption,
            color = MoneyTheme.colors.ink3,
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
        if (pending.isNotEmpty()) {
            item(key = "sort-cards") {
                SortAsCardsButton(
                    count = pending.size,
                    onClick = onSortAsCardsClicked,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                )
            }

            item(key = "pending-title") {
                SectionHeader(title = stringResource(R.string.inbox_section_waiting))
            }
        } else {
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
            key = { _, item -> item.item.key },
        ) { index, item ->
            PendingRow(
                pending = item,
                onAcceptClicked = { onAcceptClicked(item) },
                onOtherClicked = { onOtherClicked(item) },
                onDismissClicked = { onDismissClicked(item) },
                modifier = Modifier
                    .listGroupItem(
                        position = GroupPosition.of(index, pending.size) { true },
                        dividerStartInset = MoneySpacing.rowHorizontal,
                    ),
            )
        }

        if (done.isNotEmpty()) {
            item(key = "done-title") {
                SectionHeader(
                    title = stringResource(R.string.inbox_section_recorded_today),
                    modifier = Modifier
                        .padding(top = 12.dp)
                )
            }

            itemsIndexed(
                items = done,
                key = { _, item -> "done-${item.key}" },
            ) { index, item ->
                DoneRow(
                    item = item,
                    onUndoClicked = { onUndoClicked(item) },
                    modifier = Modifier
                        .listGroupItem(
                            position = GroupPosition.of(index, done.size) { true },
                            dividerStartInset = MoneySpacing.rowHorizontal,
                        ),
                )
            }
        }
    }
}

/**
 * The primary action: sort all the waiting payments as cards.
 */
@Composable
private fun SortAsCardsButton(
    modifier: Modifier = Modifier,
    count: Int,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.accent)
            .clickable(
                role = Role.Button,
                indication = remember(::ScaleIndication),
                interactionSource = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_cards),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier
                    .size(24.dp)
            )
            Text(
                text = stringResource(R.string.inbox_sort_as_cards),
                style = MoneyTheme.typography.bodyStrong,
                color = colors.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = stringResource(R.string.inbox_sort_as_cards_count, count),
            style = MoneyTheme.typography.bodyStrong,
            color = colors.onAccent.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

@Composable
private fun PendingRow(
    modifier: Modifier = Modifier,
    pending: ViewInboxTabPending,
    onAcceptClicked: () -> Unit,
    onOtherClicked: () -> Unit,
    onDismissClicked: () -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier
        .fillMaxWidth()
        .padding(
            horizontal = MoneySpacing.rowHorizontal,
            vertical = 12.dp,
        )
) {
    val item = pending.item
    val suggestion = pending.suggestion

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (suggestion != null) {
            ItemLogo(
                title = suggestion.title,
                colorScheme = suggestion.colorScheme,
                icon = suggestion.icon,
                modifier = Modifier
                    .size(MoneySpacing.itemTile)
            )
        } else {
            IconTile(
                icon = R.drawable.ic_tabler_inbox,
                size = MoneySpacing.itemTile,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
        ) {
            if (pending.isRecognized) {
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
                val metaText =
                    if (pending.sourceText.isNotEmpty())
                        "$dateText · ${pending.sourceText}"
                    else
                        dateText
                Text(
                    text =
                        if (item.isForeignCurrency)
                            stringResource(R.string.inbox_item_foreign_currency, metaText)
                        else
                            metaText,
                    style = MoneyTheme.typography.caption,
                    color =
                        if (item.isForeignCurrency)
                            MoneyTheme.colors.warning
                        else
                            MoneyTheme.colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // Nothing is parsed: the raw text is all there is.
                Text(
                    text =
                        if (LocalPrivacyMode.current)
                            stringResource(R.string.inbox_private_title)
                        else
                            pending.rawText,
                    style = MoneyTheme.typography.bodyStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = receivedAtText(item.receivedAt),
                    style = MoneyTheme.typography.caption,
                    color = MoneyTheme.colors.ink3,
                    maxLines = 1,
                )
            }
        }

        InboxItemAmount(item = item)
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MoneySpacing.itemTile + 14.dp)
    ) {
        if (suggestion != null) {
            val (tint, accent) = itemLogoColors(suggestion.colorScheme)
            TabChip(
                text = suggestion.fullTitle,
                background = tint,
                content = accent,
                icon = R.drawable.ic_tabler_check,
                onClick = onAcceptClicked,
            )
        }

        TabChip(
            text = stringResource(R.string.inbox_other),
            background = MoneyTheme.colors.surface2,
            content = MoneyTheme.colors.ink2,
            onClick = onOtherClicked,
        )

        MoneyIconButton(
            icon = R.drawable.ic_tabler_x,
            contentDescription = stringResource(R.string.inbox_dismiss),
            style = MoneyIconButtonStyle.Plain,
            size = 36.dp,
            iconSize = 18.dp,
            onClick = onDismissClicked,
        )
    }
}

@Composable
private fun TabChip(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    @androidx.annotation.DrawableRes
    icon: Int? = null,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
        .defaultMinSize(minHeight = MoneySpacing.chipHeight)
        .clip(MoneyShapes.pill)
        .background(background)
        .clickable(
            role = Role.Button,
            indication = remember(::ScaleIndication),
            interactionSource = null,
            onClick = onClick,
        )
        .padding(
            horizontal = 14.dp,
            vertical = 8.dp,
        )
) {
    if (icon != null) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(16.dp)
        )
    }

    Text(
        text = text,
        style = MoneyTheme.typography.label,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DoneRow(
    modifier: Modifier = Modifier,
    item: ViewInboxItem,
    onUndoClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier
        .fillMaxWidth()
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
        Text(
            text = receivedAtText(item.receivedAt),
            style = MoneyTheme.typography.caption,
            color = MoneyTheme.colors.ink3,
            maxLines = 1,
        )
    }

    InboxItemAmount(item = item)

    MoneyIconButton(
        icon = R.drawable.ic_tabler_arrow_back_up,
        contentDescription = stringResource(R.string.common_undo),
        style = MoneyIconButtonStyle.Plain,
        size = 36.dp,
        iconSize = 18.dp,
        onClick = onUndoClicked,
    )
}

/**
 * The amount of an item, masked in the privacy mode.
 */
@Composable
private fun InboxItemAmount(
    item: ViewInboxItem,
) {
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
}

@Composable
fun InboxTabScreen(
    viewModel: InboxScreenViewModel,
) = InboxTabScreen(
    pendingItemList = viewModel.pendingItemList.collectAsState(),
    doneItemList = viewModel.doneItemList.collectAsState(),
    counts = viewModel.counts.collectAsState(),
    onAcceptClicked = remember { viewModel::onAcceptClicked },
    onOtherClicked = remember { viewModel::onOtherClicked },
    onDismissClicked = remember { viewModel::onDismissClicked },
    onUndoClicked = remember { viewModel::onUndoClicked },
    onSortAsCardsClicked = remember { viewModel::onSortAsCardsClicked },
    onRulesClicked = remember { viewModel::onRulesClicked },
)

@Preview(
    apiLevel = 34,
)
@Composable
private fun InboxTabScreenPreview() = MoneyTheme {
    val schemes = remember { HardcodedItemColorSchemeRepository().getItemColorSchemesByName() }
    InboxTabScreen(
        pendingItemList = listOf(
            ViewInboxTabPending(
                item = ViewInboxItem(
                    title = "Coffee Point",
                    amountText = ViewText.Plain("3,40 EUR"),
                    receivedAt = LocalDateTime(2026, 10, 2, 9, 12),
                    isForeignCurrency = false,
                    key = "1",
                ),
                sourceText = "Card",
                rawText = "",
                isRecognized = true,
                suggestion = ViewInboxCardCategory(
                    key = InboxCardSuggester.CategoryKey("food", null),
                    title = "Food",
                    subcategoryTitle = "Cafe",
                    colorScheme = schemes.getValue("Orange3"),
                    icon = null,
                ),
                isRememberOn = true,
            ),
            ViewInboxTabPending(
                item = ViewInboxItem(
                    title = "Jums ir jauns ziņojums internetbankā",
                    amountText = null,
                    receivedAt = LocalDateTime(2026, 10, 2, 9, 0),
                    isForeignCurrency = false,
                    key = "2",
                ),
                sourceText = "",
                rawText = "Jums ir jauns ziņojums internetbankā",
                isRecognized = false,
                suggestion = null,
                isRememberOn = false,
            ),
        ).let(::mutableStateOf),
        doneItemList = listOf(
            ViewInboxItem(
                title = "Example Shop",
                amountText = ViewText.Plain("4,50 EUR"),
                receivedAt = LocalDateTime(2026, 10, 2, 12, 30),
                isForeignCurrency = false,
                key = "3",
            ),
        ).let(::mutableStateOf),
        counts = InboxTabItems.Counts(pending = 2, doneToday = 1).let(::mutableStateOf),
        onAcceptClicked = {},
        onOtherClicked = {},
        onDismissClicked = {},
        onUndoClicked = {},
        onSortAsCardsClicked = {},
        onRulesClicked = {},
    )
}
