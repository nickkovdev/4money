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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.colors.data.DrawableResItemIconRepository
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.privacy.logic.PrivateAmountDisplay
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import ua.com.radiokot.money.transfers.view.ViewDate
import java.math.BigInteger
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.colors.view.itemAccentColor
import ua.com.radiokot.money.uikit.ActionTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.SheetHandle
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.compose.ui.text.style.TextOverflow
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun CategoryActionSheetRoot(
    modifier: Modifier = Modifier,
    viewModel: CategoryActionSheetViewModel,
) {
    CategoryActionSheet(
        statsPeriod = viewModel.statsPeriod,
        statsAmount = viewModel.statsAmount.collectAsState(),
        subcategoryAmounts = viewModel.subcategoryAmounts.collectAsState(),
        colorScheme = viewModel.colorScheme.collectAsState(),
        title = viewModel.title.collectAsState(),
        icon = viewModel.icon.collectAsState(),
        onEditClicked = remember { viewModel::onEditClicked },
        onActivityClicked = remember { viewModel::onActivityClicked },
        isUnarchiveVisible = viewModel.isUnarchiveVisible.collectAsState(),
        onUnarchiveClicked = remember { viewModel::onUnarchiveClicked },
        modifier = modifier,
    )
}

@Composable
private fun CategoryActionSheet(
    modifier: Modifier = Modifier,
    statsPeriod: ViewHistoryPeriod,
    statsAmount: State<ViewAmount>,
    subcategoryAmounts: State<List<Pair<String?, ViewAmount>>>,
    colorScheme: State<ItemColorScheme>,
    title: State<String>,
    icon: State<ItemIcon?>,
    isUnarchiveVisible: State<Boolean>,
    onEditClicked: () -> Unit,
    onActivityClicked: () -> Unit,
    onUnarchiveClicked: () -> Unit,
) = Column(
    modifier = modifier
        .background(MoneyTheme.colors.background)
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .padding(
            horizontal = 16.dp,
        )
        .padding(
            bottom = 12.dp,
        )
) {
    SheetHandle()

    Header(
        statsPeriod = statsPeriod,
        statsAmount = statsAmount,
        subcategoryAmounts = subcategoryAmounts,
        colorScheme = colorScheme,
        title = title,
        icon = icon,
        modifier = Modifier
            .fillMaxWidth()
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(
                top = 16.dp,
            )
    ) {
        ActionTile(
            icon = R.drawable.ic_tabler_list_details,
            label = "Activity",
            onClick = onActivityClicked,
            modifier = Modifier
                .weight(1f)
        )

        ActionTile(
            icon = R.drawable.ic_tabler_pencil,
            label = "Edit",
            onClick = onEditClicked,
            modifier = Modifier
                .weight(1f)
        )

        if (isUnarchiveVisible.value) {
            ActionTile(
                icon = R.drawable.ic_tabler_arrow_back_up,
                label = "Restore",
                onClick = onUnarchiveClicked,
                modifier = Modifier
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun Header(
    modifier: Modifier = Modifier,
    statsPeriod: ViewHistoryPeriod,
    statsAmount: State<ViewAmount>,
    subcategoryAmounts: State<List<Pair<String?, ViewAmount>>>,
    colorScheme: State<ItemColorScheme>,
    title: State<String>,
    icon: State<ItemIcon?>,
) = Column(
    modifier = modifier
) {
    val amountFormat = rememberViewAmountFormat()
    val accent = itemAccentColor(colorScheme.value)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = 8.dp,
                bottom = 16.dp,
            )
    ) {
        ItemLogo(
            title = title.value,
            colorScheme = colorScheme.value,
            icon = icon.value,
            modifier = Modifier
                .size(52.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = title.value,
                style = MoneyTheme.typography.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = statsPeriod.getText(),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
            )
        }

        Text(
            text = amountFormat.formatOrPrivate(
                amount = statsAmount.value,
                customColor = MoneyTheme.colors.ink,
            ),
            style = MoneyTheme.typography.title,
            maxLines = 1,
        )
    }

    val subcategories = subcategoryAmounts.value
    if (subcategories.isNotEmpty()) {
        val total = statsAmount.value.value

        ListGroup {
            subcategories.forEachIndexed { i, (title, amount) ->
                key(i, title) {
                    if (i != 0) {
                        ListDivider()
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(
                                horizontal = MoneySpacing.rowHorizontal,
                                vertical = 12.dp,
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title ?: "Other",
                                style = MoneyTheme.typography.body,
                                color =
                                    if (title == null)
                                        MoneyTheme.colors.ink2
                                    else
                                        MoneyTheme.colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                            )

                            Text(
                                text = amountFormat.formatOrPrivate(
                                    amount = amount,
                                    customColor = MoneyTheme.colors.ink,
                                    privateAs = PrivateAmountDisplay.ShareOf(total),
                                ),
                                style = MoneyTheme.typography.bodyStrong,
                                maxLines = 1,
                            )
                        }

                        // Share of the category total.
                        val share =
                            if (total.signum() != 0)
                                (amount.value.toDouble() / total.toDouble())
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            else
                                0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    color = MoneyTheme.colors.surface2,
                                    shape = MoneyShapes.pill,
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(share)
                                    .height(4.dp)
                                    .background(
                                        color = accent,
                                        shape = MoneyShapes.pill,
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview(
    apiLevel = 34,
)
private fun Preview(

) = MoneyTheme(colors = MidnightMoneyColors) {
    CategoryActionSheet(
        statsPeriod = ViewHistoryPeriod.Day(
            day = ViewDate.today(),
        ),
        statsAmount = ViewAmount(
            value = BigInteger("15000"),
            currency = ViewCurrency(
                symbol = "$",
                precision = 2,
            )
        ).let(::mutableStateOf),
        subcategoryAmounts = listOf(
            "Pharmacy" to ViewAmount(
                value = BigInteger("10000"),
                currency = ViewCurrency(
                    symbol = "$",
                    precision = 2,
                )
            ),
            null to ViewAmount(
                value = BigInteger("5000"),
                currency = ViewCurrency(
                    symbol = "$",
                    precision = 2,
                )
            ),
        ).let(::mutableStateOf),
        colorScheme = HardcodedItemColorSchemeRepository()
            .getItemColorSchemesByName()
            .getValue("Purple2")
            .let(::mutableStateOf),
        title = "Health".let(::mutableStateOf),
        icon = DrawableResItemIconRepository()
            .getItemIcons()
            .get(22)
            .let(::mutableStateOf),
        isUnarchiveVisible = true.let(::mutableStateOf),
        onEditClicked = {},
        onActivityClicked = {},
        onUnarchiveClicked = {},
    )
}
