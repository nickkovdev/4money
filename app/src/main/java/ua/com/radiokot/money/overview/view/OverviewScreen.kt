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

package ua.com.radiokot.money.overview.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.colors.data.ItemColorSchemeAccents
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.AnimatedAmountText
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.view.PeriodBar
import ua.com.radiokot.money.transfers.history.view.PeriodSlideContainer
import ua.com.radiokot.money.transfers.history.view.periodSwipe
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import ua.com.radiokot.money.uikit.chart.StackedBarChart
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun OverviewScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: OverviewScreenViewModel,
) = OverviewScreen(
    state = viewModel.state.collectAsState(),
    period = viewModel.viewHistoryStatsPeriod.collectAsState(),
    historyPeriod = viewModel.historyStatsPeriod.collectAsState(),
    isPreviousPeriodButtonEnabled = viewModel.isPreviousHistoryStatsPeriodButtonEnabled.collectAsState(),
    onPreviousPeriodClicked = remember { viewModel::onPreviousHistoryStatsPeriodClicked },
    isNextPeriodButtonEnabled = viewModel.isNextHistoryStatsPeriodButtonEnabled.collectAsState(),
    onNextPeriodClicked = remember { viewModel::onNextHistoryStatsPeriodClicked },
    onExpensesCardClicked = remember { viewModel::onExpensesCardClicked },
    onIncomeCardClicked = remember { viewModel::onIncomeCardClicked },
    onMoreCategoriesClicked = remember { viewModel::onMoreCategoriesClicked },
    modifier = modifier,
)

@Composable
private fun OverviewScreen(
    modifier: Modifier = Modifier,
    state: State<OverviewScreenState>,
    period: State<ViewHistoryPeriod>,
    historyPeriod: State<HistoryPeriod>,
    isPreviousPeriodButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
    isNextPeriodButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    onExpensesCardClicked: () -> Unit,
    onIncomeCardClicked: () -> Unit,
    onMoreCategoriesClicked: () -> Unit,
) = Column(
    modifier = modifier
        .periodSwipe(
            isPreviousEnabled = isPreviousPeriodButtonEnabled,
            isNextEnabled = isNextPeriodButtonEnabled,
            onPrevious = onPreviousPeriodClicked,
            onNext = onNextPeriodClicked,
        )
        .verticalScroll(rememberScrollState())
        .padding(
            horizontal = 16.dp,
            vertical = 16.dp,
        )
) {
    PeriodBar(
        period = period,
        onPeriodClicked = {},
        isNextButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    PeriodSlideContainer(period = historyPeriod.value) {
        when (val currentState = state.value) {
            OverviewScreenState.Loading ->
                Unit

            OverviewScreenState.NoPrimaryCurrency ->
                Text(
                    text = "Set an existing primary currency in the profile menu to see the overview",
                    color = MoneyTheme.colors.onBackgroundSecondary,
                )

            is OverviewScreenState.Loaded ->
                OverviewContent(
                    overview = currentState.overview,
                    onExpensesCardClicked = onExpensesCardClicked,
                    onIncomeCardClicked = onIncomeCardClicked,
                    onMoreCategoriesClicked = onMoreCategoriesClicked,
                )
        }
    }
}

@Composable
private fun OverviewContent(
    overview: ViewOverview,
    onExpensesCardClicked: () -> Unit,
    onIncomeCardClicked: () -> Unit,
    onMoreCategoriesClicked: () -> Unit,
) = Column {
    val colors = MoneyTheme.colors

    Text(
        text = "Balance",
        color = colors.onBackgroundSecondary,
        fontSize = 13.sp,
    )
    AnimatedAmountText(
        amount = overview.balance,
        style = TextStyle(
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalCard(
            title = "Expenses",
            amount = overview.expenseTotal,
            color = colors.expense,
            isSelected = !overview.isIncome,
            onClick = onExpensesCardClicked,
            modifier = Modifier.weight(1f),
        )
        TotalCard(
            title = "Income",
            amount = overview.incomeTotal,
            color = colors.income,
            isSelected = overview.isIncome,
            onClick = onIncomeCardClicked,
            modifier = Modifier.weight(1f),
        )
    }

    if (overview.bars.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))

        val isDark = colors.isDark
        val chartOther = colors.chartOther
        val bars = remember(overview, isDark) {
            overview.bars.map { bar ->
                bar.segments.map { segment ->
                    (segment.colorScheme
                        ?.let { Color(ItemColorSchemeAccents.accent(it, isDark)) }
                        ?: chartOther) to segment.value
                }
            }
        }
        val labels = remember(overview) {
            overview.bars.map { bar ->
                bar.dayOfMonth
                    .takeIf { it == 1 || it % 5 == 0 || overview.bars.size <= 7 }
                    ?.toString()
            }
        }

        StackedBarChart(
            bars = bars,
            labels = labels,
            labelColor = colors.onBackgroundSecondary,
            animationKey = overview.animationKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatColumn("Day avg", overview.dayAverage, Modifier.weight(1f))
        StatColumn("Week avg", overview.weekAverage, Modifier.weight(1f))
        StatColumn(
            if (overview.isMonth) "Month total" else "Total",
            overview.periodTotal,
            Modifier.weight(1f),
        )
    }

    if (overview.topCategories.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))

        overview.topCategories.forEach { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                ItemLogo(
                    title = category.title,
                    colorScheme = category.colorScheme,
                    icon = category.icon,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${category.percent}%",
                    color = colors.onBackgroundSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                AnimatedAmountText(
                    amount = category.amount,
                    customColor = Color.Unspecified,
                )
            }
        }

        if (overview.hasMoreCategories) {
            Text(
                text = "More…",
                color = colors.onBackgroundSecondary,
                modifier = Modifier
                    .clickable(onClick = onMoreCategoriesClicked)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TotalCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: ViewAmount,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .background(MoneyTheme.colors.surfaceVariant, shape)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) color else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = MoneyTheme.colors.onBackgroundSecondary,
            fontSize = 13.sp,
        )
        AnimatedAmountText(
            amount = amount,
            customColor = color,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun StatColumn(
    title: String,
    amount: ViewAmount,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    Text(
        text = title,
        color = MoneyTheme.colors.onBackgroundSecondary,
        fontSize = 12.sp,
    )
    AnimatedAmountText(
        amount = amount,
        customColor = Color.Unspecified,
    )
}
