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

import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.composeunstyled.Icon
import ua.com.radiokot.money.R
import ua.com.radiokot.money.home.view.HomeTabHeader
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
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
    isExpanded = viewModel.isExpanded.collectAsState(),
    onMoreCategoriesClicked = remember { viewModel::onMoreCategoriesClicked },
    onCategoryClicked = remember { viewModel::onCategoryClicked },
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
    isExpanded: State<Boolean>,
    onMoreCategoriesClicked: () -> Unit,
    onCategoryClicked: (key: String) -> Unit,
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
        onPeriodClicked = {},
        isNextPeriodButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousPeriodButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
    )

    PeriodSlideContainer(
        period = historyPeriod.value,
        modifier = Modifier
            .weight(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MoneySpacing.screen,
                )
                .padding(
                    top = 4.dp,
                    bottom = 32.dp,
                )
        ) {
            when (val currentState = state.value) {
                OverviewScreenState.Loading ->
                    Unit

                OverviewScreenState.NoPrimaryCurrency ->
                    EmptyState(
                        icon = R.drawable.ic_tabler_currency_euro,
                        title = "No primary currency",
                        text = "Set an existing primary currency in Settings to see the overview",
                    )

                is OverviewScreenState.Loaded ->
                    OverviewContent(
                        overview = currentState.overview,
                        onExpensesCardClicked = onExpensesCardClicked,
                        onIncomeCardClicked = onIncomeCardClicked,
                        isExpanded = isExpanded.value,
                        onMoreCategoriesClicked = onMoreCategoriesClicked,
                        onCategoryClicked = onCategoryClicked,
                    )
            }
        }
    }
}

@Composable
private fun OverviewContent(
    overview: ViewOverview,
    onExpensesCardClicked: () -> Unit,
    onIncomeCardClicked: () -> Unit,
    isExpanded: Boolean,
    onMoreCategoriesClicked: () -> Unit,
    onCategoryClicked: (key: String) -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    val colors = MoneyTheme.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(
                horizontal = 4.dp,
            )
    ) {
        Text(
            text = "Income minus expenses",
            style = MoneyTheme.typography.labelRegular,
            color = colors.ink2,
        )
        AnimatedAmountText(
            amount = overview.balance,
            customColor =
                if (overview.balance.value.signum() < 0)
                    colors.expense
                else
                    colors.ink,
            style = MoneyTheme.typography.display,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TotalCard(
            title = "Expenses",
            icon = R.drawable.ic_tabler_arrow_up_right,
            amount = overview.expenseTotal,
            color = colors.expense,
            tint = colors.expenseTint,
            isSelected = !overview.isIncome,
            onClick = onExpensesCardClicked,
            modifier = Modifier.weight(1f),
        )
        TotalCard(
            title = "Income",
            icon = R.drawable.ic_tabler_arrow_down_left,
            amount = overview.incomeTotal,
            color = colors.income,
            tint = colors.incomeTint,
            isSelected = overview.isIncome,
            onClick = onIncomeCardClicked,
            modifier = Modifier.weight(1f),
        )
    }

    if (overview.bars.isNotEmpty()) {
        val isDark = colors.isDark
        val chartOther = colors.chartOther
        val bars = remember(overview, isDark) {
            overview.bars.map { bar ->
                bar.segments.map { segment ->
                    (segment.colorScheme
                        ?.let { Color(ItemColorSchemeAccents.themedAccent(it, isDark)) }
                        ?: chartOther) to segment.value
                }
            }
        }
        val isPrivate = LocalPrivacyMode.current
        val labels = remember(overview, isPrivate) {
            overview.bars.map { bar ->
                bar.dayOfMonth
                    .takeIf { !isPrivate && (it == 1 || it % 5 == 0 || overview.bars.size <= 7) }
                    ?.toString()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MoneyShapes.large)
                .background(colors.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "By day",
                style = MoneyTheme.typography.caption,
                color = colors.ink3,
            )

            Spacer(modifier = Modifier.height(10.dp))

            StackedBarChart(
                bars = bars,
                labels = labels,
                labelColor = colors.ink3,
                animationKey = overview.animationKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
        }
    }

    ListGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            StatColumn("Day avg", overview.dayAverage, Modifier.weight(1f))
            StatDivider()
            StatColumn("Week avg", overview.weekAverage, Modifier.weight(1f))
            StatDivider()
            StatColumn(
                if (overview.isMonth) "Month total" else "Total",
                overview.periodTotal,
                Modifier.weight(1f),
            )
        }
    }

    if (overview.topCategories.isNotEmpty()) {
        val isPrivate = LocalPrivacyMode.current

        SectionHeader(
            title = "Top categories",
            modifier = Modifier
                .padding(top = 8.dp)
        )

        ListGroup {
            val categories =
                if (isExpanded)
                    overview.allCategories
                else
                    overview.topCategories

            categories.forEachIndexed { index, category ->
                if (index > 0) {
                    ListDivider(startInset = 16.dp + 36.dp + 14.dp)
                }

                ListRow(
                    title = category.title,
                    subtitle = "${category.percent}%",
                    leading = {
                        ItemLogo(
                            title = category.title,
                            colorScheme = category.colorScheme,
                            icon = category.icon,
                            modifier = Modifier.size(36.dp),
                        )
                    },
                    onClick = { onCategoryClicked(category.key) },
                    trailing =
                        if (isPrivate)
                            null
                        else
                            ({
                                AnimatedAmountText(
                                    amount = category.amount,
                                    customColor = colors.ink,
                                    style = MoneyTheme.typography.bodyStrong,
                                )
                            }),
                )
            }

            if (overview.hasMoreCategories) {
                ListDivider()
                ListRow(
                    title =
                        if (isExpanded)
                            "Show less"
                        else
                            "All categories",
                    titleColor = colors.accent,
                    trailing = {
                        Icon(
                            painter = painterResource(
                                if (isExpanded)
                                    R.drawable.ic_tabler_chevron_up
                                else
                                    R.drawable.ic_tabler_chevron_down
                            ),
                            contentDescription = null,
                            tint = colors.ink3,
                            modifier = Modifier
                                .size(MoneySpacing.iconSmall)
                        )
                    },
                    onClick = onMoreCategoriesClicked,
                )
            }
        }
    }
}

@Composable
private fun TotalCard(
    modifier: Modifier = Modifier,
    title: String,
    @DrawableRes
    icon: Int,
    amount: ViewAmount,
    color: Color,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue =
            if (isSelected)
                tint
            else
                MoneyTheme.colors.surface,
        label = "total-card",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(MoneyShapes.large)
            .background(background)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(16.dp)
            )
            Text(
                text = title,
                style = MoneyTheme.typography.label,
                color =
                    if (isSelected)
                        color
                    else
                        MoneyTheme.colors.ink2,
            )
        }
        AnimatedAmountText(
            amount = amount,
            customColor = color,
            style = MoneyTheme.typography.title.copy(
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

@Composable
private fun StatDivider() = Box(
    modifier = Modifier
        .padding(vertical = 12.dp)
        .width(1.dp)
        .fillMaxHeight()
        .background(MoneyTheme.colors.line)
)

@Composable
private fun StatColumn(
    title: String,
    amount: ViewAmount,
    modifier: Modifier = Modifier,
) = Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier
        .padding(
            horizontal = 12.dp,
            vertical = 12.dp,
        )
) {
    Text(
        text = title,
        style = MoneyTheme.typography.small,
        color = MoneyTheme.colors.ink3,
    )
    AnimatedAmountText(
        amount = amount,
        customColor = MoneyTheme.colors.ink,
        style = MoneyTheme.typography.label.copy(
            fontFeatureSettings = "tnum",
        ),
    )
}
