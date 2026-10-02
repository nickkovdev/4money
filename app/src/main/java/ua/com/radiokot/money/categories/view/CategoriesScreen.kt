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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.transfers.history.view.PeriodBar
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import ua.com.radiokot.money.uikit.chart.DonutSegment
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun CategoriesScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: CategoriesScreenViewModel,
) = CategoriesScreen(
    isIncome = viewModel.isIncome.collectAsState(),
    period = viewModel.viewHistoryStatsPeriod.collectAsState(),
    expenseTotal = viewModel.expenseTotalAmount.collectAsState(),
    incomeTotal = viewModel.incomeTotalAmount.collectAsState(),
    ringSegments = viewModel.ringSegments.collectAsState(),
    categoryItemList = viewModel.categoryItemList.collectAsState(),
    onTitleClicked = remember { viewModel::onTitleClicked },
    onCategoryItemClicked = remember { viewModel::onCategoryItemClicked },
    onCategoryItemLongClicked = remember { viewModel::onCategoryItemLongClicked },
    onPeriodClicked = {},
    isPreviousPeriodButtonEnabled = viewModel.isPreviousHistoryStatsPeriodButtonEnabled.collectAsState(),
    onPreviousPeriodClicked = remember { viewModel::onPreviousHistoryStatsPeriodClicked },
    isNextPeriodButtonEnabled = viewModel.isNextHistoryStatsPeriodButtonEnabled.collectAsState(),
    onNextPeriodClicked = remember { viewModel::onNextHistoryStatsPeriodClicked },
    onAddClicked = remember { viewModel::onAddClicked },
    modifier = modifier,
)

@Composable
private fun CategoriesScreen(
    modifier: Modifier = Modifier,
    isIncome: State<Boolean>,
    period: State<ViewHistoryPeriod>,
    expenseTotal: State<ViewAmount?>,
    incomeTotal: State<ViewAmount?>,
    ringSegments: State<List<DonutSegment<ItemColorScheme>>>,
    categoryItemList: State<List<ViewCategoryListItem>>,
    onTitleClicked: () -> Unit,
    onCategoryItemClicked: (ViewCategoryListItem) -> Unit,
    onCategoryItemLongClicked: (ViewCategoryListItem) -> Unit,
    onPeriodClicked: () -> Unit,
    isNextPeriodButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    isPreviousPeriodButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
    onAddClicked: () -> Unit,
) = Column(
    modifier = modifier
        .padding(
            vertical = 16.dp,
        )
) {
    PeriodBar(
        period = period,
        onPeriodClicked = onPeriodClicked,
        isNextButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 22.dp,
            )
    )

    Spacer(modifier = Modifier.height(8.dp))

    CategoryRingGrid(
        itemList = categoryItemList,
        ringSegments = ringSegments,
        onItemClicked = onCategoryItemClicked,
        onItemLongClicked = onCategoryItemLongClicked,
        onAddClicked = onAddClicked,
        onRingClicked = onTitleClicked,
        ringCenter = {
            RingCenter(
                isIncome = isIncome,
                expenseTotal = expenseTotal,
                incomeTotal = incomeTotal,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )
}

@Composable
private fun RingCenter(
    isIncome: State<Boolean>,
    expenseTotal: State<ViewAmount?>,
    incomeTotal: State<ViewAmount?>,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    val colors = MoneyTheme.colors
    val amountFormat = rememberViewAmountFormat()

    Text(
        text =
            if (isIncome.value)
                "Income"
            else
                "Expenses",
        style = TextStyle(
            fontSize = 13.sp,
            color = colors.onBackgroundSecondary,
        ),
    )

    listOf(
        Triple(expenseTotal.value, colors.expense, !isIncome.value),
        Triple(incomeTotal.value, colors.income, isIncome.value),
    ).forEach { (amount, color, isCurrent) ->
        if (amount != null) {
            Text(
                text = amountFormat(
                    amount = amount,
                    customColor = color,
                ),
                maxLines = 1,
                style = TextStyle(
                    textAlign = TextAlign.Center,
                    fontSize = if (isCurrent) 18.sp else 13.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}
