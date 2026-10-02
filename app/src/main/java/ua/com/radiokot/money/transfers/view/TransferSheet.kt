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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Icon
import com.composeunstyled.Text
import java.math.BigInteger
import ua.com.radiokot.money.R
import ua.com.radiokot.money.categories.view.SelectableSubcategoryRow
import ua.com.radiokot.money.categories.view.ViewSelectableSubcategoryListItem
import ua.com.radiokot.money.categories.view.ViewSelectableSubcategoryListItemPreviewParameterProvider
import ua.com.radiokot.money.colors.data.DrawableResItemIconRepository
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.colors.view.itemAccentColor
import ua.com.radiokot.money.colors.view.itemLogoColors
import ua.com.radiokot.money.currency.view.AmountKeyboard
import ua.com.radiokot.money.currency.view.AmountKeyboardMainAction
import ua.com.radiokot.money.currency.view.AnimatedAmountInputText
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.currency.view.rememberAmountInputState
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun TransferSheetRoot(
    modifier: Modifier = Modifier,
    viewModel: TransferSheetViewModel,
) {
    TransferSheet(
        modifier = modifier,
        isSourceInputShown = viewModel.isSourceInputShown.collectAsState().value,
        isSwapCounterpartiesShown = viewModel.isSwapCounterpartiesShown,
        source = viewModel.source.collectAsState().value,
        sourceAmountValue = viewModel.sourceAmountValue.collectAsState(),
        onNewSourceAmountValueParsed = remember { viewModel::onNewSourceAmountValueParsed },
        destination = viewModel.destination.collectAsState().value,
        destinationAmountValue = viewModel.destinationAmountValue.collectAsState(),
        onNewDestinationAmountValueParsed = remember { viewModel::onNewDestinationAmountValueParsed },
        memo = viewModel.memo.collectAsStateWithLifecycle(),
        date = viewModel.date.collectAsStateWithLifecycle(),
        onMemoUpdated = remember { viewModel::onMemoUpdated },
        subcategoryItemList = viewModel.subcategoryItemList.collectAsState(),
        subcategoriesColorScheme = viewModel.subcategoriesColorScheme.collectAsState(),
        subcategoriesIcon = viewModel.subcategoriesIcon.collectAsState(),
        onSubcategoryItemClicked = remember { viewModel::onSubcategoryItemClicked },
        onSaveClicked = remember { viewModel::onSaveClicked },
        onDateClicked = remember { viewModel::onDateClicked },
        onSourceClicked = remember { viewModel::onSourceClicked },
        onDestinationClicked = remember { viewModel::onDestinationClicked },
        onSwapCounterpartiesClicked = remember { viewModel::onSwapCounterpartiesClicked },
        rememberPayeeDisplayName = viewModel.rememberPayeeDisplayName,
        isRememberPayeeEnabled = viewModel.isRememberPayeeEnabled.collectAsState(),
        onRememberPayeeToggled = remember { viewModel::onRememberPayeeToggled },
    )
}

@Composable
private fun TransferSheet(
    modifier: Modifier = Modifier,
    isSourceInputShown: Boolean,
    isSwapCounterpartiesShown: Boolean,
    source: ViewTransferCounterparty,
    sourceAmountValue: State<BigInteger>,
    onNewSourceAmountValueParsed: (BigInteger) -> Unit,
    destination: ViewTransferCounterparty,
    destinationAmountValue: State<BigInteger>,
    onNewDestinationAmountValueParsed: (BigInteger) -> Unit,
    memo: State<String>,
    date: State<ViewDate>,
    onMemoUpdated: (String) -> Unit,
    subcategoryItemList: State<List<ViewSelectableSubcategoryListItem>>,
    subcategoriesColorScheme: State<ItemColorScheme?>,
    subcategoriesIcon: State<ItemIcon?>,
    onSubcategoryItemClicked: (ViewSelectableSubcategoryListItem) -> Unit,
    onSaveClicked: () -> Unit,
    onDateClicked: () -> Unit,
    onSourceClicked: () -> Unit,
    onDestinationClicked: () -> Unit,
    onSwapCounterpartiesClicked: () -> Unit,
    rememberPayeeDisplayName: String? = null,
    isRememberPayeeEnabled: State<Boolean> = remember { mutableStateOf(false) },
    onRememberPayeeToggled: (Boolean) -> Unit = {},
) = BoxWithConstraints(
    modifier = modifier
        .background(MoneyTheme.colors.surface)
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
) {

    val maxSheetHeightDp =
        if (maxHeight < 400.dp)
            maxHeight
        else
            maxHeight * 0.85f
    val colors = MoneyTheme.colors
    val kind = remember(source, destination) {
        transferKindOf(source, destination)
    }
    val accentScheme = remember(source, destination) {
        if (source is ViewTransferCounterparty.Category)
            source.colorScheme
        else
            destination.colorScheme
    }
    val accentColor = itemAccentColor(accentScheme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                max = maxSheetHeightDp,
            )
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
            ) {
                CounterpartyHalf(
                    label = counterpartyHalfLabel(isSource = true, counterparty = source),
                    title = (source as? ViewTransferCounterparty.Category)?.categoryTitle
                        ?: source.title,
                    colorScheme = source.colorScheme,
                    icon = source.icon,
                    onClick = onSourceClicked,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                CounterpartyHalf(
                    label = counterpartyHalfLabel(isSource = false, counterparty = destination),
                    title = (destination as? ViewTransferCounterparty.Category)?.categoryTitle
                        ?: destination.title,
                    colorScheme = destination.colorScheme,
                    icon = destination.icon,
                    onClick = onDestinationClicked,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            if (isSwapCounterpartiesShown) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .clickable(onClick = onSwapCounterpartiesClicked)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_arrows_exchange),
                        contentDescription = "Swap",
                        tint = colors.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (subcategoryItemList.value.isNotEmpty()
            && subcategoriesColorScheme.value != null
        ) {
            SelectableSubcategoryRow(
                itemList = subcategoryItemList,
                colorScheme = subcategoriesColorScheme.value!!,
                icon = subcategoriesIcon.value,
                onItemClicked = onSubcategoryItemClicked,
                modifier = Modifier
                    .padding(
                        vertical = 12.dp,
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        val sourceAmountInputState = rememberAmountInputState(
            currency = source.currency,
            initialValue = sourceAmountValue.value,
        )
        LaunchedEffect(sourceAmountInputState) {
            sourceAmountInputState
                .valueFlow
                .collect(onNewSourceAmountValueParsed)
        }
        val destinationAmountInputState = rememberAmountInputState(
            currency = destination.currency,
            initialValue = destinationAmountValue.value,
        )
        LaunchedEffect(destinationAmountInputState) {
            destinationAmountInputState
                .valueFlow
                .collect(onNewDestinationAmountValueParsed)
        }
        var isEnteringSourceAmount by remember(isSourceInputShown) {
            mutableStateOf(isSourceInputShown)
        }

        Text(
            text = kind.label,
            color = accentColor,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isSourceInputShown) {
            // Different currencies: both amounts, the keypad edits the highlighted one.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                listOf(
                    Triple(true, sourceAmountInputState, source),
                    Triple(false, destinationAmountInputState, destination),
                ).forEach { (isSourceAmount, inputState, counterparty) ->
                    val isCurrent = isEnteringSourceAmount == isSourceAmount
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) accentColor else Color.Transparent,
                            )
                            .clickable { isEnteringSourceAmount = isSourceAmount }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = counterparty.title,
                            color = colors.onBackgroundSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        AnimatedAmountInputText(
                            amountInputState = inputState,
                            color = accentColor,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                        )
                    }
                }
            }
        } else {
            AnimatedAmountInputText(
                amountInputState = destinationAmountInputState,
                color = accentColor,
                fontSize = 32.sp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
        ) {
            var emptyMemoFieldOffset by remember {
                mutableStateOf(IntOffset.Zero)
            }

            if (memo.value.isEmpty()) {
                Text(
                    text = "Notes",
                    style = TextStyle(
                        fontStyle = FontStyle.Italic,
                        color = colors.onBackgroundSecondary,
                    ),
                    modifier = Modifier
                        .onSizeChanged { (width, _) ->
                            emptyMemoFieldOffset = IntOffset(
                                x = -width / 2,
                                y = 0,
                            )
                        }
                )
            }

            BasicTextField(
                value = memo.value,
                onValueChange = onMemoUpdated,
                textStyle = TextStyle(
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    color = colors.onBackground,
                ),
                cursorBrush = SolidColor(colors.onBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .offset {
                        if (memo.value.isEmpty())
                            emptyMemoFieldOffset
                        else
                            IntOffset.Zero
                    }
            )
        }

        if (rememberPayeeDisplayName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { onRememberPayeeToggled(!isRememberPayeeEnabled.value) },
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    )
            ) {
                Text(
                    text = "Remember for \u201C$rememberPayeeDisplayName\u201D",
                    color = colors.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                )

                MoneySwitch(
                    isOn = isRememberPayeeEnabled.value,
                    onToggled = onRememberPayeeToggled,
                )
            }
        }

        AmountKeyboard(
            inputState =
                if (isEnteringSourceAmount)
                    sourceAmountInputState
                else
                    destinationAmountInputState,
            colorScheme = accentScheme,
            mainAction =
                if (isEnteringSourceAmount)
                    AmountKeyboardMainAction.Next
                else
                    AmountKeyboardMainAction.Done,
            onMainActionClicked = { action ->
                when (action) {

                    AmountKeyboardMainAction.Done ->
                        onSaveClicked()

                    AmountKeyboardMainAction.Next ->
                        isEnteringSourceAmount = false
                }
            },
            onCurrencyClicked =
                if (isSourceInputShown)
                    { { isEnteringSourceAmount = !isEnteringSourceAmount } }
                else
                    null,
            onDateClicked = onDateClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                )
                .height(maxSheetHeightDp / 2.4f)
        )

        Text(
            text = date.value.getText(),
            color = colors.onBackgroundSecondary,
            modifier = Modifier
                .clickable(onClick = onDateClicked)
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                )
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
@Preview(
    heightDp = 2000,
    apiLevel = 34,
)
private fun TransferSheetPreview(
) = Column {
    val isSourceInputShownOptions = listOf(true, false)
    val colorSchemesByName = HardcodedItemColorSchemeRepository()
        .getItemColorSchemesByName()
    val icons = DrawableResItemIconRepository()
        .getItemIcons()
    val categoryColorScheme = colorSchemesByName.getValue("Green2")
    val categoryIcon = icons[45]
    val accountColorScheme = colorSchemesByName.getValue("Yellow4")
    val accountIcon = icons[66]

    isSourceInputShownOptions.forEach { isSourceInputShown ->
        Text(
            text = "Source input shown: $isSourceInputShown",
            modifier = Modifier.padding(vertical = 16.dp)
        )
        TransferSheet(
            isSourceInputShown = isSourceInputShown,
            isSwapCounterpartiesShown = true,
            source = ViewTransferCounterparty.Account(
                accountTitle = "Source he he he he",
                currency = ViewCurrency(
                    symbol = "A",
                    precision = 2,
                ),
                colorScheme = accountColorScheme,
                icon = accountIcon,
            ),
            sourceAmountValue = BigInteger("133").let(::mutableStateOf),
            onNewSourceAmountValueParsed = {},
            destination = ViewTransferCounterparty.Category(
                categoryTitle = "Destination",
                subcategoryTitle = null,
                currency = ViewCurrency(
                    symbol = "B",
                    precision = 2,
                ),
                colorScheme = categoryColorScheme,
                icon = categoryIcon,
            ),
            destinationAmountValue = BigInteger("331").let(::mutableStateOf),
            onNewDestinationAmountValueParsed = {},
            memo = "".let(::mutableStateOf),
            date = ViewDate.today().let(::mutableStateOf),
            onMemoUpdated = {},
            subcategoryItemList =
                ViewSelectableSubcategoryListItemPreviewParameterProvider()
                    .values
                    .toList()
                    .let(::mutableStateOf),
            subcategoriesColorScheme = categoryColorScheme.let(::mutableStateOf),
            subcategoriesIcon = categoryIcon.let(::mutableStateOf),
            onSubcategoryItemClicked = {},
            onSaveClicked = {},
            onDateClicked = {},
            onSourceClicked = { },
            onDestinationClicked = { },
            onSwapCounterpartiesClicked = { },
            rememberPayeeDisplayName = "DEEPSEERWEA",
            modifier = Modifier
                .heightIn(
                    max = 600.dp,
                )
        )
    }
}

@Composable
private fun CounterpartyHalf(
    modifier: Modifier = Modifier,
    label: String,
    title: String,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
    onClick: () -> Unit,
) {
    val (backgroundColor, contentColor) = itemLogoColors(colorScheme)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp,
            )
    ) {
        ItemLogo(
            title = title,
            colorScheme = colorScheme,
            icon = icon,
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .border(
                    width = 1.dp,
                    color = contentColor.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                color = contentColor.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
