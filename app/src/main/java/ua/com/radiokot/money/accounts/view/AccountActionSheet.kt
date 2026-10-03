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

import androidx.compose.ui.res.stringResource
import ua.com.radiokot.money.currency.view.formatOrPrivate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.currency.view.AmountKeyboard
import ua.com.radiokot.money.currency.view.AmountKeyboardMainAction
import ua.com.radiokot.money.currency.view.AnimatedAmountInputText
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.currency.view.rememberAmountInputState
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import java.math.BigInteger
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.uikit.ActionTile
import ua.com.radiokot.money.uikit.SheetHandle
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun AccountActionSheet(
    modifier: Modifier = Modifier,
    viewModel: AccountActionSheetViewModel,
) {
    AccountActionSheet(
        title = viewModel.title,
        balance = viewModel.balance,
        colorScheme = viewModel.colorScheme,
        icon = viewModel.icon,
        mode = viewModel.mode.collectAsState(),
        balanceInputValue = viewModel.balanceInputValue.collectAsState(),
        onBalanceClicked = remember { viewModel::onBalanceClicked },
        onNewBalanceInputValueParsed = remember { viewModel::onNewBalanceInputValueParsed },
        onBalanceInputSubmit = remember { viewModel::onBalanceInputSubmit },
        onTransferClicked = remember { viewModel::onTransferClicked },
        onIncomeClicked = remember { viewModel::onIncomeClicked },
        onExpenseClicked = remember { viewModel::onExpenseClicked },
        onActivityClicked = remember { viewModel::onActivityClicked },
        onEditClicked = remember { viewModel::onEditClicked },
        onUnarchiveClicked = remember { viewModel::onUnarchiveClicked },
        modifier = modifier,
    )
}

@Composable
private fun AccountActionSheet(
    modifier: Modifier = Modifier,
    title: String,
    balance: ViewAmount,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
    mode: State<ViewAccountActionSheetMode>,
    balanceInputValue: State<BigInteger>,
    onBalanceClicked: () -> Unit,
    onNewBalanceInputValueParsed: (BigInteger) -> Unit,
    onBalanceInputSubmit: () -> Unit,
    onTransferClicked: () -> Unit,
    onIncomeClicked: () -> Unit,
    onExpenseClicked: () -> Unit,
    onActivityClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onUnarchiveClicked: () -> Unit,
) = BoxWithConstraints(
    modifier = modifier
        .background(MoneyTheme.colors.background)
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
) {

    val maxSheetHeightDp =
        if (maxHeight < 400.dp)
            maxHeight
        else
            maxHeight * 0.85f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                max = maxSheetHeightDp,
            )
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 16.dp,
            )
    ) {
        val amountFormat = rememberViewAmountFormat()
        val isBalanceMode = mode.value == ViewAccountActionSheetMode.Balance

        SheetHandle()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    end = 4.dp,
                    top = 8.dp,
                    bottom = 20.dp,
                )
        ) {
            ItemLogo(
                title = title,
                colorScheme = colorScheme,
                icon = icon,
                modifier = Modifier
                    .size(52.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MoneyTheme.typography.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text =
                        if (isBalanceMode)
                            AnnotatedString(
                                stringResource(
                                    R.string.accounts_current_balance,
                                    amountFormat.formatOrPrivate(balance).text,
                                )
                            )
                        else
                            amountFormat.formatOrPrivate(
                                amount = balance,
                                customColor = balanceColor(balance.value),
                            ),
                    style =
                        if (isBalanceMode)
                            MoneyTheme.typography.caption
                        else
                            MoneyTheme.typography.bodyStrong,
                    color =
                        if (isBalanceMode)
                            MoneyTheme.colors.ink2
                        else
                            androidx.compose.ui.graphics.Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when (mode.value) {
            ViewAccountActionSheetMode.DefaultActions ->
                DefaultActionsModeContent(
                    onBalanceClicked = onBalanceClicked,
                    onTransferClicked = onTransferClicked,
                    onIncomeClicked = onIncomeClicked,
                    onExpenseClicked = onExpenseClicked,
                    onActivityClicked = onActivityClicked,
                    onEditClicked = onEditClicked,
                )

            ViewAccountActionSheetMode.ArchivedActions -> {
                ArchivedActionsModeContent(
                    onBalanceClicked = onBalanceClicked,
                    onEditClicked = onEditClicked,
                    onUnarchiveClicked = onUnarchiveClicked,
                )
            }

            ViewAccountActionSheetMode.Balance ->
                BalanceModeContent(
                    currency = balance.currency,
                    colorScheme = colorScheme,
                    balanceInputValue = balanceInputValue,
                    onNewBalanceInputValueParsed = onNewBalanceInputValueParsed,
                    onBalanceInputSubmit = onBalanceInputSubmit,
                    keyboardHeight = MoneySpacing.keypadKeyHeight * 4 + 24.dp,
                )
        }
    }
}

@Composable
private fun AccountActionSheetPreviewContent() = Column {
    ViewAccountActionSheetMode.entries.forEach { mode ->
        Text(
            text = mode.name + ": ",
            modifier = Modifier.padding(vertical = 12.dp)
        )

        AccountActionSheet(
            title = "My account",
            balance = ViewAmount(
                value = BigInteger("1050"),
                currency = ViewCurrency(
                    symbol = "$",
                    precision = 2,
                )
            ),
            colorScheme = HardcodedItemColorSchemeRepository()
                .getItemColorSchemes()[20],
            icon = null,
            mode = mode.let(::mutableStateOf),
            balanceInputValue = BigInteger("9856").let(::mutableStateOf),
            onBalanceClicked = {},
            onNewBalanceInputValueParsed = {},
            onBalanceInputSubmit = {},
            onTransferClicked = {},
            onIncomeClicked = {},
            onExpenseClicked = {},
            onActivityClicked = {},
            onEditClicked = {},
            onUnarchiveClicked = {},
        )
    }
}

@Composable
@Preview(
    apiLevel = 34,
    heightDp = 2000,
)
private fun AccountActionSheetPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    AccountActionSheetPreviewContent()
}

@Composable
private fun DefaultActionsModeContent(
    onBalanceClicked: () -> Unit,
    onTransferClicked: () -> Unit,
    onIncomeClicked: () -> Unit,
    onExpenseClicked: () -> Unit,
    onActivityClicked: () -> Unit,
    onEditClicked: () -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .padding(
            bottom = 12.dp,
        )
) {
    val colors = MoneyTheme.colors

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionTile(
            icon = R.drawable.ic_tabler_arrow_up_right,
            label = stringResource(R.string.accounts_action_expense),
            tint = colors.expense,
            tileBackground = colors.expenseTint,
            onClick = onExpenseClicked,
            modifier = Modifier
                .weight(1f)
        )

        ActionTile(
            icon = R.drawable.ic_tabler_arrow_down_left,
            label = stringResource(R.string.accounts_action_income),
            tint = colors.income,
            tileBackground = colors.incomeTint,
            onClick = onIncomeClicked,
            modifier = Modifier
                .weight(1f)
        )

        ActionTile(
            icon = R.drawable.ic_tabler_arrows_exchange,
            label = stringResource(R.string.accounts_action_transfer),
            tint = colors.ink,
            tileBackground = colors.surface2,
            onClick = onTransferClicked,
            modifier = Modifier
                .weight(1f)
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionTile(
            icon = R.drawable.ic_tabler_list_details,
            label = stringResource(R.string.accounts_action_activity),
            onClick = onActivityClicked,
            modifier = Modifier
                .weight(1f)
        )

        ActionTile(
            icon = R.drawable.ic_tabler_scale,
            label = stringResource(R.string.accounts_action_balance),
            onClick = onBalanceClicked,
            modifier = Modifier
                .weight(1f)
        )

        ActionTile(
            icon = R.drawable.ic_tabler_pencil,
            label = stringResource(R.string.common_edit),
            onClick = onEditClicked,
            modifier = Modifier
                .weight(1f)
        )
    }
}

@Composable
private fun ArchivedActionsModeContent(
    onBalanceClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onUnarchiveClicked: () -> Unit,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .padding(
            bottom = 12.dp,
        )
) {
    ActionTile(
        icon = R.drawable.ic_tabler_arrow_back_up,
        label = stringResource(R.string.accounts_action_restore),
        onClick = onUnarchiveClicked,
        modifier = Modifier
            .weight(1f)
    )

    ActionTile(
        icon = R.drawable.ic_tabler_scale,
        label = stringResource(R.string.accounts_action_balance),
        onClick = onBalanceClicked,
        modifier = Modifier
            .weight(1f)
    )

    ActionTile(
        icon = R.drawable.ic_tabler_pencil,
        label = stringResource(R.string.common_edit),
        onClick = onEditClicked,
        modifier = Modifier
            .weight(1f)
    )
}

@Composable
private fun BalanceModeContent(
    currency: ViewCurrency,
    colorScheme: ItemColorScheme,
    balanceInputValue: State<BigInteger>,
    onNewBalanceInputValueParsed: (BigInteger) -> Unit,
    onBalanceInputSubmit: () -> Unit,
    keyboardHeight: Dp,
) = Column(
    modifier = Modifier
        .padding(
            bottom = 12.dp,
        )
) {

    val balanceInputState = rememberAmountInputState(
        currency = currency,
        initialValue = balanceInputValue.value,
    )

    LaunchedEffect(balanceInputState) {
        balanceInputState
            .valueFlow
            .collect(onNewBalanceInputValueParsed)
    }

    Text(
        text = stringResource(R.string.accounts_new_balance),
        style = MoneyTheme.typography.labelRegular,
        color = MoneyTheme.colors.ink2,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(4.dp))

    AnimatedAmountInputText(
        amountInputState = balanceInputState,
        modifier = Modifier
            .fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(20.dp))

    AmountKeyboard(
        inputState = balanceInputState,
        colorScheme = colorScheme,
        mainAction = AmountKeyboardMainAction.Done,
        onMainActionClicked = {
            onBalanceInputSubmit()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(keyboardHeight)
    )
}
