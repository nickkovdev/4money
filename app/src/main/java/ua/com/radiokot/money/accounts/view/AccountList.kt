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

package ua.com.radiokot.money.accounts.view

import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import ua.com.radiokot.money.colors.data.DrawableResItemIconRepository
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.currency.view.animateAmountValueAsState
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.uikit.GroupPosition
import ua.com.radiokot.money.uikit.ListRowTileDividerInset
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.ViewAmountPreviewParameterProvider
import ua.com.radiokot.money.uikit.listGroupItem
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import java.math.BigInteger

@Composable
fun AccountList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        vertical = 8.dp,
    ),
    itemList: State<List<ViewAccountListItem>>,
    onAccountItemClicked: (ViewAccountListItem.Account) -> Unit,
) = LazyColumn(
    contentPadding = contentPadding,
    modifier = modifier,
) {
    val items = itemList.value
    itemsIndexed(
        items = items,
        key = { _, item -> item.key },
        contentType = { _, item -> item.type },
    ) { index, item ->
        when (item) {
            is ViewAccountListItem.Header -> {
                HeaderItem(
                    title = item.title,
                    amount = item.amount,
                    isFirst = index == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            is ViewAccountListItem.Account -> {
                AccountItem(
                    item = item,
                    modifier = Modifier
                        .listGroupItem(
                            position = accountGroupPosition(items, index),
                            dividerStartInset = ListRowTileDividerInset,
                        )
                        .clickable(
                            onClick = {
                                onAccountItemClicked(item)
                            },
                        )
                        .fillMaxWidth()
                )
            }
        }
    }
}

private fun accountGroupPosition(
    items: List<ViewAccountListItem>,
    index: Int,
): GroupPosition = GroupPosition.of(
    index = index,
    size = items.size,
    isGroupMember = { items[it] is ViewAccountListItem.Account },
)

@Composable
fun MovableAccountList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        vertical = 8.dp,
    ),
    itemList: State<List<ViewAccountListItem>>,
    onAccountItemClicked: (ViewAccountListItem.Account) -> Unit,
    onAccountItemMoved: (
        itemToMove: ViewAccountListItem.Account,
        itemToPlaceBefore: ViewAccountListItem.Account?,
        itemToPlaceAfter: ViewAccountListItem.Account?,
    ) -> Unit,
    bottomContent: (LazyListScope.() -> Unit)? = null,
) {
    val movableItemList = remember {
        mutableStateListOf<ViewAccountListItem>()
    }
    var itemToMove by remember {
        mutableStateOf<ViewAccountListItem.Account?>(null)
    }
    var itemToPlaceBefore by remember {
        mutableStateOf<ViewAccountListItem.Account?>(null)
    }
    var itemToPlaceAfter by remember {
        mutableStateOf<ViewAccountListItem.Account?>(null)
    }
    var skipOriginalListUpdates by remember {
        mutableIntStateOf(0)
    }
    val currentItemList = remember {
        derivedStateOf {
            itemList.value
                .takeUnless { skipOriginalListUpdates-- > 0 }
                ?: movableItemList
        }
    }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (itemToMove == null) {
                itemToMove = movableItemList[from.index]
                        as? ViewAccountListItem.Account
            }
            movableItemList.add(
                to.index,
                movableItemList.removeAt(from.index),
            )
            itemToPlaceBefore = movableItemList.getOrNull(to.index + 1)
                    as? ViewAccountListItem.Account
            itemToPlaceAfter = movableItemList.getOrNull(to.index - 1)
                    as? ViewAccountListItem.Account

            hapticFeedback.performHapticFeedback(
                HapticFeedbackType.GestureEnd
            )
        },
    )

    LazyColumn(
        contentPadding = contentPadding,
        state = listState,
        modifier = modifier,
    ) {
        val items = currentItemList.value
        itemsIndexed(
            items = items,
            key = { _, item -> item.key },
            contentType = { _, item -> item.type },
        ) { index, item ->
            when (item) {
                is ViewAccountListItem.Header -> {
                    HeaderItem(
                        title = item.title,
                        amount = item.amount,
                        isFirst = index == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }

                is ViewAccountListItem.Account -> {
                    ReorderableItem(
                        state = reorderableState,
                        key = item.key,
                    ) { isDragging ->
                        AccountItem(
                            item = item,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha =
                                        if (isDragging)
                                            0.8f
                                        else
                                            1f
                                }
                                .listGroupItem(
                                    position =
                                        if (isDragging)
                                            GroupPosition.Single
                                        else
                                            accountGroupPosition(items, index),
                                    dividerStartInset = ListRowTileDividerInset,
                                )
                                .clickable(
                                    onClick = {
                                        onAccountItemClicked(item)
                                    },
                                )
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        movableItemList.clear()
                                        movableItemList.addAll(itemList.value)
                                        skipOriginalListUpdates = Int.MAX_VALUE
                                        itemToMove = null
                                    },
                                    onDragStopped = {
                                        if (itemToMove != null) {
                                            skipOriginalListUpdates = 1
                                            onAccountItemMoved(
                                                itemToMove!!,
                                                itemToPlaceBefore,
                                                itemToPlaceAfter,
                                            )
                                        } else {
                                            skipOriginalListUpdates = 0
                                        }
                                    },
                                )
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

        bottomContent?.invoke(this)
    }
}

@Composable
@Preview(
    widthDp = 200
)
private fun AccountListPreview() {
    val colorSchemesByName = HardcodedItemColorSchemeRepository()
        .getItemColorSchemesByName()
    val iconsByName = DrawableResItemIconRepository()
        .getItemIconsByName()

    AccountList(
        itemList = listOf(
            ViewAccountListItem.Header(
                title = "Accounts",
                amount = ViewAmount(
                    value = BigInteger("10000"),
                    currency = ViewCurrency(
                        symbol = "$",
                        precision = 2,
                    ),
                ),
                key = "header1",
            ),
            ViewAccountListItem.Account(
                title = "Account #1",
                balance = ViewAmount(
                    value = BigInteger("7500"),
                    currency = ViewCurrency(
                        symbol = "$",
                        precision = 2,
                    ),
                ),
                isIncognito = false,
                colorScheme = colorSchemesByName.getValue("Purple1"),
                icon = null,
                key = "acc1",
            ),
            ViewAccountListItem.Account(
                title = "Account #2",
                balance = ViewAmount(
                    value = BigInteger("2500"),
                    currency = ViewCurrency(
                        symbol = "$",
                        precision = 2,
                    ),
                ),
                isIncognito = false,
                colorScheme = colorSchemesByName.getValue("Red4"),
                icon = null,
                key = "acc2",
            ),
            ViewAccountListItem.Header(
                title = "Savings",
                amount = ViewAmount(
                    value = BigInteger("9900000"),
                    currency = ViewCurrency(
                        symbol = "$",
                        precision = 2,
                    ),
                ),
                key = "header2",
            ),
            ViewAccountListItem.Account(
                title = "Account #3",
                balance = ViewAmount(
                    value = BigInteger("100000000"),
                    currency = ViewCurrency(
                        symbol = "₿",
                        precision = 8,
                    ),
                ),
                isIncognito = true,
                colorScheme = colorSchemesByName.getValue("Green3"),
                icon = iconsByName["finances_34"],
                key = "acc3",
            ),
        ).let(::mutableStateOf),
        onAccountItemClicked = {},
    )
}

@Composable
private fun HeaderItem(
    modifier: Modifier = Modifier,
    title: String,
    amount: ViewAmount?,
    isFirst: Boolean = false,
) {
    val amountFormat = rememberViewAmountFormat()
    val isPrivate = LocalPrivacyMode.current
    val animatedAmount = amount?.let {
        animateAmountValueAsState(
            targetAmount = amount
        )
    }

    SectionHeader(
        title = title,
        trailing =
            if (amount != null && animatedAmount != null)
                if (isPrivate)
                    PrivacyAmounts.MASK
                else
                    amountFormat(
                        value = animatedAmount.value,
                        currency = amount.currency,
                    ).text
            else
                null,
        modifier = modifier
            .padding(
                top =
                    if (isFirst)
                        0.dp
                    else
                        12.dp,
            )
    )
}

@Composable
@Preview(
    widthDp = 140,
)
private fun HeaderItemPreview(
    @PreviewParameter(ViewAmountPreviewParameterProvider::class) amount: ViewAmount,
) {
    HeaderItem(
        title = "Savings",
        amount = amount,
    )
}

@Composable
private fun AccountItem(
    modifier: Modifier = Modifier,
    item: ViewAccountListItem.Account,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier
        .padding(
            horizontal = MoneySpacing.rowHorizontal,
            vertical = 12.dp,
        ),
) {
    ItemLogo(
        title = item.title,
        colorScheme = item.colorScheme,
        icon = item.icon,
        modifier = Modifier
            .size(MoneySpacing.itemTile)
    )

    Text(
        text = item.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MoneyTheme.typography.bodyStrong,
        modifier = Modifier
            .weight(1f)
    )

    if (!item.isIncognito) {
        val amountFormat = rememberViewAmountFormat()
        val animatedAmount = animateAmountValueAsState(
            targetAmount = item.balance,
        )

        val isPrivate = LocalPrivacyMode.current

        Text(
            text =
                if (isPrivate)
                    amountFormat.privateText(
                        text = PrivacyAmounts.MASK,
                        value = animatedAmount.value,
                        customColor = balanceColor(animatedAmount.value),
                    )
                else
                    amountFormat(
                        value = animatedAmount.value,
                        currency = item.balance.currency,
                        customColor = balanceColor(animatedAmount.value),
                    ),
            maxLines = 1,
            style = MoneyTheme.typography.bodyStrong,
        )
    } else {
        Text(
            text = item.balance.currency.symbol,
            maxLines = 1,
            style = MoneyTheme.typography.bodyStrong,
            color = MoneyTheme.colors.ink2,
        )
    }
}

/**
 * Balances are neutral, only a debt stands out.
 */
@Composable
fun balanceColor(value: BigInteger) =
    if (value.signum() < 0)
        MoneyTheme.colors.expense
    else
        MoneyTheme.colors.ink
