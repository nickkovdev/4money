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

import ua.com.radiokot.money.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import ua.com.radiokot.money.currency.view.AnimatedAmountText
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.view.PeriodBar
import ua.com.radiokot.money.transfers.history.view.PeriodSlideContainer
import ua.com.radiokot.money.transfers.history.view.periodSwipe
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import ua.com.radiokot.money.uikit.chart.DonutSegment
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import ua.com.radiokot.money.uikit.SegmentedControl
import ua.com.radiokot.money.home.view.HomeTabHeader
import androidx.compose.foundation.layout.Arrangement
import java.math.BigInteger

@Composable
fun CategoriesScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: CategoriesScreenViewModel,
) = CategoriesScreen(
    isIncome = viewModel.isIncome.collectAsState(),
    period = viewModel.viewHistoryStatsPeriod.collectAsState(),
    historyPeriod = viewModel.historyStatsPeriod.collectAsState(),
    expenseTotal = viewModel.expenseTotalAmount.collectAsState(),
    incomeTotal = viewModel.incomeTotalAmount.collectAsState(),
    ringSegments = viewModel.ringSegments.collectAsState(),
    categoryItemList = viewModel.categoryItemList.collectAsState(),
    currentModeTotal = viewModel.currentModeTotal.collectAsState(),
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
    historyPeriod: State<HistoryPeriod>,
    expenseTotal: State<ViewAmount?>,
    incomeTotal: State<ViewAmount?>,
    ringSegments: State<List<DonutSegment<ItemColorScheme>>>,
    categoryItemList: State<List<ViewCategoryListItem>>,
    currentModeTotal: State<BigInteger?>,
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
        .periodSwipe(
            isPreviousEnabled = isPreviousPeriodButtonEnabled,
            isNextEnabled = isNextPeriodButtonEnabled,
            onPrevious = onPreviousPeriodClicked,
            onNext = onNextPeriodClicked,
        )
) {
    HomeTabHeader(
        period = period,
        onPeriodClicked = onPeriodClicked,
        isNextPeriodButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousPeriodButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
    )

    PeriodSlideContainer(
        period = historyPeriod.value,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        CategoryRingGrid(
            itemList = categoryItemList,
            ringSegments = ringSegments,
            onItemClicked = onCategoryItemClicked,
            onItemLongClicked = onCategoryItemLongClicked,
            onAddClicked = onAddClicked,
            onRingClicked = onTitleClicked,
            currentModeTotal = currentModeTotal.value,
            modeSwitch = {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.categories_expenses),
                        stringResource(R.string.categories_income),
                    ),
                    selectedIndex =
                        if (isIncome.value)
                            1
                        else
                            0,
                    onSelected = { index ->
                        if ((index == 1) != isIncome.value) {
                            onTitleClicked()
                        }
                    },
                )
            },
            ringCenter = {
                RingCenter(
                    isIncome = isIncome,
                    expenseTotal = expenseTotal,
                    incomeTotal = incomeTotal,
                )
            },
            modifier = Modifier
                .fillMaxSize()
        )
    }
}

@Composable
private fun RingCenter(
    isIncome: State<Boolean>,
    expenseTotal: State<ViewAmount?>,
    incomeTotal: State<ViewAmount?>,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    val colors = MoneyTheme.colors
    val (current, other) =
        if (isIncome.value)
            incomeTotal.value to expenseTotal.value
        else
            expenseTotal.value to incomeTotal.value

    Text(
        text =
            stringResource(
                if (isIncome.value)
                    R.string.categories_income
                else
                    R.string.categories_expenses
            ),
        style = MoneyTheme.typography.caption,
        color = colors.ink2,
    )

    if (current != null) {
        AnimatedAmountText(
            amount = current,
            customColor = colors.ink,
            style = MoneyTheme.typography.headline.copy(
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum",
            ),
        )
    }

    if (other != null) {
        AnimatedAmountText(
            amount = other,
            customColor =
                if (isIncome.value)
                    colors.expense
                else
                    colors.income,
            style = MoneyTheme.typography.caption.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
