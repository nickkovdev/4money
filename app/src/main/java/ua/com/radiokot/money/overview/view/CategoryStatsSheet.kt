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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.colors.view.itemAccentColor
import ua.com.radiokot.money.currency.view.AnimatedAmountText
import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.logic.PrivateAmountDisplay
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.SheetHandle
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun CategoryStatsSheetRoot(
    modifier: Modifier = Modifier,
    viewModel: CategoryStatsSheetViewModel,
) = CategoryStatsSheet(
    stats = viewModel.stats.collectAsState(),
    onProceedToTransferClicked = remember { viewModel::onProceedToTransferClicked },
    onTransactionsClicked = remember { viewModel::onTransactionsClicked },
    modifier = modifier,
)

@Composable
private fun CategoryStatsSheet(
    modifier: Modifier = Modifier,
    stats: State<ViewCategoryStats?>,
    onProceedToTransferClicked: () -> Unit,
    onTransactionsClicked: () -> Unit,
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

    val currentStats = stats.value
        ?: return@Column

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
    ) {
        Header(
            stats = currentStats,
        )

        if (currentStats.subcategories.isNotEmpty()) {
            Subcategories(
                stats = currentStats,
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 16.dp)
    ) {
        MoneyButton(
            text =
                if (currentStats.isIncome)
                    "Income"
                else
                    "Expense",
            icon = R.drawable.ic_tabler_plus,
            style = MoneyButtonStyle.Filled,
            onClick = onProceedToTransferClicked,
            modifier = Modifier
                .weight(1f)
        )

        MoneyButton(
            text = "Transactions",
            icon = R.drawable.ic_tabler_list_details,
            style = MoneyButtonStyle.Tonal,
            onClick = onTransactionsClicked,
            modifier = Modifier
                .weight(1f)
        )
    }
}

@Composable
private fun Header(
    stats: ViewCategoryStats,
) = Column(
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
        .fillMaxWidth()
        .clip(MoneyShapes.large)
        .background(
            if (stats.isIncome)
                MoneyTheme.colors.incomeTint
            else
                MoneyTheme.colors.expenseTint
        )
        .padding(16.dp)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ItemLogo(
            title = stats.title,
            colorScheme = stats.colorScheme,
            icon = stats.icon,
            modifier = Modifier
                .size(52.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = stats.title,
                style = MoneyTheme.typography.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    if (stats.transferCount == 1)
                        "1 transaction"
                    else
                        "${stats.transferCount} transactions",
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
            )
        }

        AnimatedAmountText(
            amount = stats.total,
            customColor = MoneyTheme.colors.ink,
            style = MoneyTheme.typography.title,
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stats.period.getText(),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
                modifier = Modifier
                    .weight(1f)
            )

            // The share is shown even while private, it is not an amount.
            Text(
                text = PrivacyAmounts.shareText(stats.periodShare, stats.periodTotal.value),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink2,
            )
        }

        ShareBar(
            fraction = stats.periodShareFraction,
            accent = itemAccentColor(stats.colorScheme),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    if (stats.isIncome)
                        "Total income"
                    else
                        "Total expenses",
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
                modifier = Modifier
                    .weight(1f)
            )

            AnimatedAmountText(
                amount = stats.periodTotal,
                customColor = MoneyTheme.colors.ink2,
                style = MoneyTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun Subcategories(
    stats: ViewCategoryStats,
) {
    val amountFormat = rememberViewAmountFormat()
    val accent = itemAccentColor(stats.colorScheme)

    ListGroup {
        stats.subcategories.forEachIndexed { i, subcategory ->
            key(subcategory.key) {
                if (i != 0) {
                    ListDivider(startInset = MoneySpacing.rowHorizontal + 36.dp + 14.dp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MoneySpacing.rowHorizontal,
                            vertical = 12.dp,
                        )
                ) {
                    ItemLogo(
                        title = subcategory.title,
                        colorScheme = stats.colorScheme,
                        icon =
                            if (subcategory.isUncategorized)
                                stats.icon
                            else
                                null,
                        modifier = Modifier
                            .size(36.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = subcategory.title,
                                style = MoneyTheme.typography.body,
                                color =
                                    if (subcategory.isUncategorized)
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
                                    amount = subcategory.amount,
                                    customColor = MoneyTheme.colors.ink,
                                    privateAs = PrivateAmountDisplay.ShareOf(stats.total.value),
                                ),
                                style = MoneyTheme.typography.bodyStrong,
                                maxLines = 1,
                            )
                        }

                        ShareBar(
                            fraction = subcategory.fraction,
                            accent = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareBar(
    fraction: Float,
    accent: androidx.compose.ui.graphics.Color,
) = Box(
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
            .fillMaxWidth(fraction)
            .height(4.dp)
            .background(
                color = accent,
                shape = MoneyShapes.pill,
            )
    )
}
