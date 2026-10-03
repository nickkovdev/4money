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

package ua.com.radiokot.money.transfers.history.view

import androidx.compose.ui.res.stringResource
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.composeunstyled.Icon
import ua.com.radiokot.money.R
import ua.com.radiokot.money.home.view.HomeTabHeader
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingData
import com.composeunstyled.Text
import kotlinx.coroutines.flow.Flow
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.view.TransferList
import ua.com.radiokot.money.transfers.view.ViewPrivacyTotals
import ua.com.radiokot.money.transfers.view.ViewTransferCounterparty
import ua.com.radiokot.money.transfers.view.ViewTransferListItem
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun ActivityScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel,
) = ActivityScreen(
    totalIncomeAndExpense = viewModel.totalIncomeAndExpense.collectAsState(),
    privacyTotals = viewModel.privacyTotals.collectAsState(),
    itemPagingFlow = viewModel.transferItemPagingFlow,
    onTransferItemClicked = remember { viewModel::onTransferItemClicked },
    onTransferItemLongClicked = remember { viewModel::onTransferItemLongClicked },
    onTransferItemDeleteClicked = remember { viewModel::onTransferItemDeleteClicked },
    isUndoDeletionVisible = viewModel.isUndoDeletionVisible.collectAsState(),
    onUndoDeletionClicked = remember { viewModel::onUndoDeletionClicked },
    period = viewModel.viewHistoryStatsPeriod.collectAsState(),
    historyPeriod = viewModel.historyStatsPeriod.collectAsState(),
    onPeriodClicked = {},
    isPreviousPeriodButtonEnabled = viewModel.isPreviousHistoryStatsPeriodButtonEnabled.collectAsState(),
    onPreviousPeriodClicked = remember { viewModel::onPreviousHistoryStatsPeriodClicked },
    isNextPeriodButtonEnabled = viewModel.isNextHistoryStatsPeriodButtonEnabled.collectAsState(),
    onNextPeriodClicked = remember { viewModel::onNextHistoryStatsPeriodClicked },
    isBackHandlerEnabled = viewModel.isBackHandlerEnabled.collectAsState(),
    onBack = remember { viewModel::onBack },
    counterparties = viewModel.activityFilterCounterparties.collectAsState(),
    modifier = modifier,
)

@Composable
private fun ActivityScreen(
    modifier: Modifier = Modifier,
    totalIncomeAndExpense: State<ViewTotalIncomeAndExpense?>,
    privacyTotals: State<ViewPrivacyTotals?>,
    itemPagingFlow: Flow<PagingData<ViewTransferListItem>>,
    onTransferItemClicked: (ViewTransferListItem.Transfer) -> Unit,
    onTransferItemLongClicked: (ViewTransferListItem.Transfer) -> Unit,
    onTransferItemDeleteClicked: (ViewTransferListItem.Transfer) -> Unit,
    isUndoDeletionVisible: State<Boolean>,
    onUndoDeletionClicked: () -> Unit,
    counterparties: State<List<ViewTransferCounterparty>>,
    period: State<ViewHistoryPeriod>,
    historyPeriod: State<HistoryPeriod>,
    onPeriodClicked: () -> Unit,
    isNextPeriodButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    isPreviousPeriodButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
    isBackHandlerEnabled: State<Boolean>,
    onBack: () -> Unit,
) = Box(
    modifier = modifier,
) {
Column(
    modifier = Modifier
        .fillMaxSize()
        .periodSwipe(
            isPreviousEnabled = isPreviousPeriodButtonEnabled,
            isNextEnabled = isNextPeriodButtonEnabled,
            onPrevious = onPreviousPeriodClicked,
            onNext = onNextPeriodClicked,
        ),
) {
    HomeTabHeader(
        period = period,
        onPeriodClicked = onPeriodClicked,
        isNextPeriodButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousPeriodButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
    )

    val areCounterpartiesShown by remember {
        derivedStateOf {
            counterparties.value.isNotEmpty()
        }
    }

    if (areCounterpartiesShown) {
        // The active filter, clearable here and not only with the system BACK.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MoneySpacing.screen,
                )
                .padding(
                    bottom = 10.dp,
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(MoneyShapes.pill)
                    .background(MoneyTheme.colors.accentTint)
                    .clickable(onClick = onBack)
                    .padding(
                        start = 14.dp,
                        end = 10.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_filter),
                    contentDescription = null,
                    tint = MoneyTheme.colors.accent,
                    modifier = Modifier
                        .size(16.dp)
                )
                val titles = mutableListOf<String>()
                counterparties.value.forEach { counterparty ->
                    titles += counterparty.title
                }
                Text(
                    text = titles.joinToString(", "),
                    style = MoneyTheme.typography.label,
                    color = MoneyTheme.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_x),
                    contentDescription = stringResource(R.string.history_clear_filter),
                    tint = MoneyTheme.colors.accent,
                    modifier = Modifier
                        .size(16.dp)
                )
            }
        }
    }

    val totalIncomeAndExpense = totalIncomeAndExpense.value
    if (totalIncomeAndExpense != null
        && (totalIncomeAndExpense.income.signum() > 0 || totalIncomeAndExpense.expense.signum() > 0)
    ) {
        val amountFormat = rememberViewAmountFormat()
        val isPrivate = LocalPrivacyMode.current

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MoneySpacing.screen,
                )
                .padding(
                    bottom = 12.dp,
                )
        ) {
            listOf(
                Triple(
                    stringResource(R.string.history_in),
                    totalIncomeAndExpense.income,
                    MoneyTheme.colors.income,
                ),
                Triple(
                    stringResource(R.string.history_out),
                    totalIncomeAndExpense.expense,
                    MoneyTheme.colors.expense,
                ),
            ).forEach { (label, value, color) ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(MoneyShapes.medium)
                        .background(MoneyTheme.colors.surface)
                        .padding(
                            horizontal = 14.dp,
                            vertical = 10.dp,
                        )
                ) {
                    Text(
                        text = label,
                        style = MoneyTheme.typography.caption,
                        color = MoneyTheme.colors.ink3,
                    )
                    Text(
                        text =
                            if (isPrivate)
                                amountFormat.privateText(
                                    text = PrivacyAmounts.MASK,
                                    value = value,
                                    customColor = color,
                                )
                            else
                                amountFormat(
                                    value = value,
                                    currency = totalIncomeAndExpense.currency,
                                    customColor = color,
                                ),
                        style = MoneyTheme.typography.bodyStrong,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    val transferListState = remember(period.value) {
        LazyListState()
    }

    PeriodSlideContainer(
        period = historyPeriod.value,
        modifier = Modifier
            .weight(1f)
    ) {
        TransferList(
            itemPagingFlow = itemPagingFlow,
            onTransferItemClicked = onTransferItemClicked,
            onTransferItemLongClicked = onTransferItemLongClicked,
            onTransferItemEditClicked = onTransferItemClicked,
            onTransferItemDeleteClicked = onTransferItemDeleteClicked,
            state = transferListState,
            privacyTotals = privacyTotals.value,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = MoneySpacing.screen,
                )
        )
    }

    BackHandler(
        enabled = isBackHandlerEnabled.value,
        onBack = onBack,
    )
}

AnimatedVisibility(
    visible = isUndoDeletionVisible.value,
    enter = fadeIn() + slideInVertically { it },
    exit = fadeOut() + slideOutVertically { it },
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(16.dp),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MoneyTheme.colors.surface2,
                shape = MoneyShapes.medium,
            )
            .padding(
                start = 16.dp,
                end = 6.dp,
                top = 4.dp,
                bottom = 4.dp,
            )
    ) {
        Text(
            text = stringResource(R.string.history_deleted),
            style = MoneyTheme.typography.labelRegular,
            modifier = Modifier.weight(1f),
        )
        MoneyButton(
            text = stringResource(R.string.common_undo),
            style = MoneyButtonStyle.Text,
            icon = R.drawable.ic_tabler_arrow_back_up,
            onClick = onUndoDeletionClicked,
        )
    }
}
}
