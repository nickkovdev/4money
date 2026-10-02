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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import ua.com.radiokot.money.uikit.SegmentedControl
import ua.com.radiokot.money.uikit.SheetHandle
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import kotlinx.coroutines.launch
import ua.com.radiokot.money.accounts.view.AccountList
import ua.com.radiokot.money.accounts.view.ViewAccountListItem
import ua.com.radiokot.money.categories.view.CategoryGrid
import ua.com.radiokot.money.categories.view.ViewCategoryListItem
import ua.com.radiokot.money.plus

@Composable
fun TransferCounterpartySelector(
    modifier: Modifier = Modifier,
    isForSource: Boolean?,
    accountItemList: State<List<ViewAccountListItem>>?,
    incomeCategoryItemList: State<List<ViewCategoryListItem>>?,
    expenseCategoryItemList: State<List<ViewCategoryListItem>>?,
    onAccountItemClicked: (ViewAccountListItem.Account) -> Unit,
    onCategoryItemClicked: (ViewCategoryListItem) -> Unit,
) = Column(
    modifier = modifier
) {
    val pages: List<Page> = remember(
        accountItemList,
        incomeCategoryItemList,
        expenseCategoryItemList,
    ) {
        buildList {
            if (incomeCategoryItemList != null) {
                add(Page.Income)
            }
            if (expenseCategoryItemList != null) {
                add(Page.Expense)
            }
            if (accountItemList != null) {
                add(Page.Account)
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(Page.Expense).coerceAtLeast(0),
        pageCount = pages::size,
    )
    val coroutineScope = rememberCoroutineScope()

    SheetHandle()

    val pageTitles = pages.map { page ->
        when (page) {
            Page.Income -> "Income"
            Page.Expense -> "Expense"
            Page.Account ->
                if (isForSource == false)
                    "To account"
                else
                    "From account"
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 4.dp,
            )
    ) {
        if (pages.size > 1) {
            SegmentedControl(
                options = pageTitles,
                selectedIndex = pagerState.currentPage,
                onSelected = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
            )
        } else {
            Text(
                text = when (pages.firstOrNull()) {
                    Page.Income -> "Income category"
                    Page.Expense -> "Expense category"
                    Page.Account, null -> pageTitles.firstOrNull() ?: ""
                },
                style = MoneyTheme.typography.title,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val navigationBarsPadding =
        WindowInsets
            .navigationBars
            .asPaddingValues()

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = pages.size - 1,
        verticalAlignment = Alignment.Top,
        key = Int::unaryPlus,
        modifier = Modifier
            .fillMaxWidth()
    ) { pageIndex ->
        when (pages[pageIndex]) {
            Page.Income -> {
                CategoryGrid(
                    itemList = incomeCategoryItemList!!,
                    onItemClicked = onCategoryItemClicked,
                    isAddShown = false,
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = 6.dp,
                        bottom = 24.dp,
                    ) + navigationBarsPadding,
                )
            }

            Page.Expense -> {
                CategoryGrid(
                    itemList = expenseCategoryItemList!!,
                    onItemClicked = onCategoryItemClicked,
                    isAddShown = false,
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = 6.dp,
                        bottom = 24.dp,
                    ) + navigationBarsPadding,
                )
            }

            Page.Account -> {
                AccountList(
                    itemList = accountItemList!!,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ) + navigationBarsPadding,
                    onAccountItemClicked = onAccountItemClicked,
                )
            }
        }
    }
}

private enum class Page {
    Income,
    Expense,
    Account,
}

@Composable
@Preview
private fun TransferCounterpartySelectorPreview(
) = TransferCounterpartySelector(
    isForSource = null,
    accountItemList = emptyList<ViewAccountListItem>().let(::mutableStateOf),
    incomeCategoryItemList = emptyList<ViewCategoryListItem>().let(::mutableStateOf),
    expenseCategoryItemList = emptyList<ViewCategoryListItem>().let(::mutableStateOf),
    onAccountItemClicked = {},
    onCategoryItemClicked = {},
)
