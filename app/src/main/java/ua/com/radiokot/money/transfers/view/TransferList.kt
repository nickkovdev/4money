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

package ua.com.radiokot.money.transfers.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import ua.com.radiokot.money.uikit.GroupPosition
import ua.com.radiokot.money.uikit.ListRowTileDividerInset
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.listGroupItem
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import ua.com.radiokot.money.uikit.EmptyState
import androidx.paging.LoadState
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewAmountFormat
import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import java.util.Locale

@Composable
fun TransferList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemPagingFlow: Flow<PagingData<ViewTransferListItem>>,
    onTransferItemClicked: (ViewTransferListItem.Transfer) -> Unit,
    onTransferItemLongClicked: (ViewTransferListItem.Transfer) -> Unit,
    onTransferItemEditClicked: ((ViewTransferListItem.Transfer) -> Unit)? = null,
    onTransferItemDeleteClicked: ((ViewTransferListItem.Transfer) -> Unit)? = null,
    privacyTotals: ViewPrivacyTotals? = null,
) {
    val locale = rememberAppLocale()
    val amountFormat = rememberViewAmountFormat()
    val lazyPagingItems = itemPagingFlow.collectAsLazyPagingItems()

    LazyColumn(
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = 96.dp,
        ),
        state = state,
        modifier = modifier,
    ) {
        if (lazyPagingItems.itemCount == 0
            && lazyPagingItems.loadState.refresh is LoadState.NotLoading
        ) {
            item(
                key = "empty",
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillParentMaxHeight(0.7f)
                        .fillMaxWidth(),
                ) {
                    EmptyState(
                        icon = R.drawable.ic_tabler_receipt,
                        title = stringResource(R.string.transfers_empty_title),
                        text = stringResource(R.string.transfers_empty_text),
                    )
                }
            }
        }

        items(
            lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey(ViewTransferListItem::key),
            contentType = lazyPagingItems.itemContentType(ViewTransferListItem::itemType),
        ) { itemIndex ->
            when (val item = lazyPagingItems[itemIndex]) {
                is ViewTransferListItem.Header -> {
                    HeaderItem(
                        item = item,
                        locale = locale,
                        modifier = Modifier
                            .padding(
                                top =
                                    if (itemIndex == 0)
                                        0.dp
                                    else
                                        14.dp,
                            )
                    )
                }

                is ViewTransferListItem.Transfer -> {
                    val clickableModifier = remember(item) {
                        // Long click – experimental 🤡.
                        @OptIn(ExperimentalFoundationApi::class)
                        Modifier.combinedClickable(
                            onClick = {
                                onTransferItemClicked(item)
                            },
                            onLongClick = {
                                onTransferItemLongClicked(item)
                            },
                        )
                    }

                    val groupPosition = GroupPosition.of(
                        index = itemIndex,
                        size = lazyPagingItems.itemCount,
                        isGroupMember = { index ->
                            lazyPagingItems.peek(index) is ViewTransferListItem.Transfer
                        },
                    )

                    SwipeRevealRow(
                        isSwipeEnabled = onTransferItemDeleteClicked != null,
                        actions = { close ->
                            RevealAction(
                                icon = R.drawable.ic_tabler_pencil,
                                contentDescription = stringResource(R.string.common_edit),
                                tint = MoneyTheme.colors.onBackground,
                                background = MoneyTheme.colors.surfaceVariant,
                                onClick = {
                                    close()
                                    onTransferItemEditClicked?.invoke(item)
                                },
                            )
                            RevealAction(
                                icon = R.drawable.ic_tabler_trash,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MoneyTheme.colors.onWarning,
                                background = MoneyTheme.colors.expense,
                                onClick = {
                                    onTransferItemDeleteClicked?.invoke(item)
                                },
                            )
                        },
                        modifier = Modifier
                            .clip(groupPosition.shape)
                    ) {
                        TransferItem(
                            item = item,
                            amountFormat = amountFormat,
                            privacyTotals = privacyTotals,
                            modifier = Modifier
                                .listGroupItem(
                                    position = groupPosition,
                                    dividerStartInset = ListRowTileDividerInset,
                                )
                                .then(clickableModifier)
                        )
                    }
                }

                null ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )
            }
        }
    }
}

@Composable
@Preview(
    widthDp = 180,
)
private fun TransferListPreview(
    @PreviewParameter(ViewTransferItemListPreviewParameterProvider::class) itemList: List<ViewTransferListItem>,
) = TransferList(
    itemPagingFlow = flowOf(PagingData.from(itemList)),
    onTransferItemClicked = {},
    onTransferItemLongClicked = {},
)

@Composable
private fun HeaderItem(
    modifier: Modifier = Modifier,
    item: ViewTransferListItem.Header,
    locale: Locale,
) = SectionHeader(
    // The period bar already says the month, so the header names only the day.
    title =
        when (item.date.specificType) {
            ViewDate.SpecificType.Today ->
                stringResource(R.string.date_today)

            ViewDate.SpecificType.Yesterday ->
                stringResource(R.string.date_yesterday)

            null ->
                remember(item.date.localDate, locale) {
                    ViewDateFormats.weekdayDay(item.date.localDate, locale)
                }
        },
    modifier = modifier,
)

/**
 * Category on top, then subcategory and the other side (account),
 * the note under it in tertiary ink; the amount on the right.
 */
@Composable
private fun TransferItem(
    modifier: Modifier = Modifier,
    item: ViewTransferListItem.Transfer,
    amountFormat: ViewAmountFormat,
    privacyTotals: ViewPrivacyTotals?,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier
        .fillMaxWidth()
        .padding(
            horizontal = MoneySpacing.rowHorizontal,
            vertical = 12.dp,
        ),
) {
    val primary = item.primaryCounterparty
    val secondary = item.secondaryCounterparty

    ItemLogo(
        title = primary.title,
        colorScheme = primary.colorScheme,
        icon = primary.icon,
        modifier = Modifier
            .size(MoneySpacing.itemTile)
    )

    val colors = MoneyTheme.colors
    val amountColor = remember(item.type, colors) {
        when (item.type) {
            ViewTransferListItem.Transfer.Type.Income ->
                colors.income

            ViewTransferListItem.Transfer.Type.Expense ->
                colors.expense

            ViewTransferListItem.Transfer.Type.Other ->
                colors.ink
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
    ) {
        Text(
            text =
                if (primary is ViewTransferCounterparty.Category)
                    primary.categoryTitle
                else
                    primary.title,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MoneyTheme.typography.bodyStrong,
        )

        val subtitle = buildList {
            if (primary is ViewTransferCounterparty.Category && primary.subcategoryTitle != null) {
                add(primary.subcategoryTitle)
            }
            add(
                if (secondary is ViewTransferCounterparty.Category)
                    secondary.categoryTitle
                else
                    secondary.title
            )
        }.joinToString(" · ")

        Text(
            text = subtitle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MoneyTheme.typography.caption,
            color = colors.ink2,
        )

        if (item.memo != null) {
            Text(
                text = remember(item.memo) { softenAllCaps(item.memo) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MoneyTheme.typography.caption,
                color = colors.ink3,
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = amountFormat.formatOrPrivate(
                amount = ViewAmount(
                    value = item.primaryAmount,
                    currency = primary.currency,
                ),
                customColor = amountColor,
                privateAs = PrivacyAmounts.forTransfer(
                    isExpense = item.type == ViewTransferListItem.Transfer.Type.Expense,
                    isIncome = item.type == ViewTransferListItem.Transfer.Type.Income,
                    isInTotalsCurrency = privacyTotals != null
                            && primary.currency == privacyTotals.currency,
                    expenseTotal = privacyTotals?.expense,
                    incomeTotal = privacyTotals?.income,
                ),
            ),
            maxLines = 1,
            style = MoneyTheme.typography.bodyStrong,
        )

        if (primary.currency != secondary.currency && !LocalPrivacyMode.current) {
            Text(
                text = amountFormat(
                    amount = ViewAmount(
                        value = item.secondaryAmount,
                        currency = secondary.currency,
                    ),
                    customColor = colors.ink3,
                ),
                maxLines = 1,
                style = MoneyTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun RevealAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .fillMaxHeight()
        .width(64.dp)
        .background(background)
        .clickable(onClick = onClick)
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}
