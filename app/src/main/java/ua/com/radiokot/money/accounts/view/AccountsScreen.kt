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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import kotlinx.coroutines.launch
import ua.com.radiokot.money.R
import ua.com.radiokot.money.currency.view.AnimatedAmountText
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.home.view.HomeProfileButton
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.MoneyIconButtonStyle
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.SegmentedControl
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun AccountsScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel,
) = AccountsScreen(
    modifier = modifier,
    accountItemList = viewModel.accountListItems.collectAsState(),
    onAccountItemClicked = remember { viewModel::onAccountItemClicked },
    onAccountItemMoved = remember { viewModel::onAccountItemMoved },
    totalAmountPerCurrencyList = viewModel.totalAmountsPerCurrency.collectAsState(),
    totalAmount = viewModel.totalAmount.collectAsState(),
    onAddClicked = remember { viewModel::onAddClicked },
    isArchiveVisible = viewModel.isArchiveVisible.collectAsState(),
    onArchiveClicked = remember { viewModel::onArchiveClicked },
)

@Composable
private fun AccountsScreen(
    modifier: Modifier = Modifier,
    accountItemList: State<List<ViewAccountListItem>>,
    onAccountItemClicked: (ViewAccountListItem.Account) -> Unit,
    onAccountItemMoved: (
        itemToMove: ViewAccountListItem.Account,
        itemToPlaceBefore: ViewAccountListItem.Account?,
        itemToPlaceAfter: ViewAccountListItem.Account?,
    ) -> Unit,
    totalAmountPerCurrencyList: State<List<Pair<ViewAmount, ViewAmount?>>>,
    totalAmount: State<ViewAmount?>,
    onAddClicked: () -> Unit,
    isArchiveVisible: State<Boolean>,
    onArchiveClicked: () -> Unit,
) = Column(
    modifier = modifier
) {
    val pages: List<Page> = remember {
        listOf(
            Page.All,
            Page.Total,
        )
    }
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(Page.All).coerceAtLeast(0),
        pageCount = pages::size,
    )
    val coroutineScope = rememberCoroutineScope()

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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
        ) {
            SegmentedControl(
                options = pages.map { page ->
                    when (page) {
                        Page.All -> "Accounts"
                        Page.Total -> "Total"
                    }
                },
                selectedIndex = pagerState.currentPage,
                onSelected = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
            )
        }

        MoneyIconButton(
            icon = R.drawable.ic_tabler_plus,
            contentDescription = "Add account",
            style = MoneyIconButtonStyle.Filled,
            onClick = onAddClicked,
        )
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = pages.size - 1,
        verticalAlignment = Alignment.Top,
        key = Int::unaryPlus,
        modifier = Modifier
            .fillMaxSize()
    ) { pageIndex ->
        when (pages[pageIndex]) {
            Page.All ->
                Column {
                    TotalBalanceHeader(
                        totalAmount = totalAmount,
                        modifier = Modifier
                            .padding(
                                horizontal = MoneySpacing.screen + 4.dp,
                            )
                            .padding(
                                top = 4.dp,
                                bottom = 12.dp,
                            )
                    )

                    MovableAccountList(
                        contentPadding = PaddingValues(
                            start = MoneySpacing.screen,
                            end = MoneySpacing.screen,
                            top = 4.dp,
                            bottom = 24.dp,
                        ),
                        itemList = accountItemList,
                        onAccountItemClicked = onAccountItemClicked,
                        onAccountItemMoved = onAccountItemMoved,
                        bottomContent =
                            if (isArchiveVisible.value) {
                                {
                                    item(key = "archive") {
                                        ArchiveLink(
                                            onClick = onArchiveClicked,
                                            modifier = Modifier
                                                .padding(
                                                    top = 20.dp,
                                                )
                                        )
                                    }
                                }
                            } else {
                                null
                            }
                    )
                }

            Page.Total ->
                TotalPage(
                    amountPerCurrencyList = totalAmountPerCurrencyList,
                    totalAmount = totalAmount,
                )
        }
    }
}

@Composable
private fun TotalBalanceHeader(
    modifier: Modifier = Modifier,
    totalAmount: State<ViewAmount?>,
) {
    val amount = totalAmount.value
        ?: return

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        Text(
            text = "Total balance",
            style = MoneyTheme.typography.labelRegular,
            color = MoneyTheme.colors.ink2,
        )

        AnimatedAmountText(
            amount = amount,
            customColor = balanceColor(amount.value),
            style = MoneyTheme.typography.display,
        )
    }
}

@Composable
private fun ArchiveLink(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = ListGroup(
    modifier = modifier,
) {
    ListRow(
        title = "Archived accounts",
        leading = {
            IconTile(
                icon = R.drawable.ic_tabler_archive,
                tint = MoneyTheme.colors.ink2,
                background = MoneyTheme.colors.surface2,
            )
        },
        trailing = { RowChevron() },
        onClick = onClick,
    )
}

@Composable
private fun TotalPage(
    modifier: Modifier = Modifier,
    amountPerCurrencyList: State<List<Pair<ViewAmount, ViewAmount?>>>,
    totalAmount: State<ViewAmount?>,
) = Column(
    modifier = modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(
            horizontal = MoneySpacing.screen,
        )
        .padding(
            top = 4.dp,
            bottom = 24.dp,
        )
) {
    val amountFormat = rememberViewAmountFormat()

    TotalBalanceHeader(
        totalAmount = totalAmount,
        modifier = Modifier
            .padding(
                horizontal = 4.dp,
            )
            .padding(
                bottom = 12.dp,
            )
    )

    SectionHeader(
        title = "By currency",
    )

    ListGroup {
        val list = amountPerCurrencyList.value
        list.forEachIndexed { index, (amount, amountInPrimaryCurrency) ->
            key(amount.currency) {
                if (index > 0) {
                    ListDivider()
                }

                ListRow(
                    title = amount.currency.symbol,
                    subtitle =
                        if (amountInPrimaryCurrency != null)
                            "≈ " + amountFormat(amountInPrimaryCurrency).text
                        else
                            null,
                    trailing = {
                        Text(
                            text = amountFormat(
                                amount = amount,
                                customColor = balanceColor(amount.value),
                            ),
                            style = MoneyTheme.typography.bodyStrong,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
}

private enum class Page {
    All,
    Total,
}
