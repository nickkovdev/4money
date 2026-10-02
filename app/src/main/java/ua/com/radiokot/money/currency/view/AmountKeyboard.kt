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

package ua.com.radiokot.money.currency.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlinx.coroutines.launch
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.view.itemAccentColor
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.ScaleIndication
import java.math.BigInteger
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun AmountKeyboard(
    modifier: Modifier = Modifier,
    inputState: AmountInputState,
    colorScheme: ItemColorScheme,
    mainAction: AmountKeyboardMainAction = AmountKeyboardMainAction.Done,
    onMainActionClicked: ((AmountKeyboardMainAction) -> Unit)? = null,
    onCurrencyClicked: (() -> Unit)? = null,
    onDateClicked: (() -> Unit)? = null,
) = BoxWithConstraints(
    modifier = modifier,
) {
    val buttonGap = 8.dp
    val buttonWidth = (maxWidth - buttonGap * 4) / 5
    val buttonHeight = (maxHeight - buttonGap * 3) / 4
    val colors = MoneyTheme.colors
    val actionBackground = Modifier.background(colors.surfaceVariant)
    val keySize = Modifier.size(width = buttonWidth, height = buttonHeight)
    val confirmBackground =
        if (colors.isDark)
            itemAccentColor(colorScheme)
        else
            Color(colorScheme.primary)
    val confirmContentColor =
        if (colors.isDark)
            colors.background
        else
            Color(colorScheme.onPrimary)

    val hapticFeedback = LocalHapticFeedback.current
    val onSymbolClicked = remember(inputState) {
        { symbol: Char ->
            hapticFeedback.performHapticFeedback(
                HapticFeedbackType.KeyboardTap
            )
            inputState.acceptInput(symbol)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val animateClear = remember(inputState) {
        {
            coroutineScope.launch {
                inputState.animateClear()
            }
            Unit
        }
    }

    CompositionLocalProvider(
        LocalIndication provides remember(::ScaleIndication),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(buttonGap),
                modifier = Modifier
                    .graphicsLayer()
            ) {
                listOf(
                    listOf(AmountInputState.Operator.Divide.symbol, '7', '8', '9'),
                    listOf(AmountInputState.Operator.Multiply.symbol, '4', '5', '6'),
                    listOf(AmountInputState.Operator.Minus.symbol, '1', '2', '3'),
                ).forEach { rowSymbols ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(buttonGap),
                    ) {
                        rowSymbols.forEachIndexed { index, symbol ->
                            KeyButton(
                                text = symbol.toString(),
                                onClick = { onSymbolClicked(symbol) },
                                modifier = keySize
                                    .then(if (index == 0) actionBackground else Modifier)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(buttonGap),
                ) {
                    KeyButton(
                        text = AmountInputState.Operator.Plus.symbol.toString(),
                        onClick = { onSymbolClicked(AmountInputState.Operator.Plus.symbol) },
                        modifier = keySize.then(actionBackground)
                    )
                    KeyButton(
                        text = inputState.currency.symbol,
                        contentDescription = "Switch currency",
                        fontSize = 20.sp,
                        isEnabled = onCurrencyClicked != null,
                        onClick = { onCurrencyClicked?.invoke() },
                        modifier = keySize.then(actionBackground)
                    )
                    KeyButton(
                        text = "0",
                        onClick = { onSymbolClicked('0') },
                        modifier = keySize
                    )
                    KeyButton(
                        text = inputState.decimalSeparator.toString(),
                        onClick = { onSymbolClicked(inputState.decimalSeparator) },
                        modifier = keySize
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(buttonGap),
            ) {
                KeyButton(
                    icon = R.drawable.ic_tabler_backspace,
                    contentDescription = "Erase",
                    onClick = { onSymbolClicked('⌫') },
                    onLongClick = animateClear,
                    modifier = keySize.then(actionBackground)
                )
                KeyButton(
                    icon = R.drawable.ic_tabler_calendar,
                    contentDescription = "Date",
                    isEnabled = onDateClicked != null,
                    onClick = { onDateClicked?.invoke() },
                    modifier = keySize.then(actionBackground)
                )
                KeyButton(
                    text =
                        if (inputState.isEvaluationNeeded)
                            "="
                        else
                            null,
                    icon =
                        if (inputState.isEvaluationNeeded)
                            null
                        else when (mainAction) {
                            AmountKeyboardMainAction.Done -> R.drawable.ic_tabler_check
                            AmountKeyboardMainAction.Next -> R.drawable.ic_tabler_chevron_right
                        },
                    contentDescription = "Confirm",
                    contentColor = confirmContentColor,
                    onClick = {
                        if (inputState.isEvaluationNeeded) {
                            onSymbolClicked('=')
                        } else {
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.Confirm
                            )
                            onMainActionClicked?.invoke(mainAction)
                        }
                    },
                    modifier = Modifier
                        .size(
                            width = buttonWidth,
                            height = buttonHeight * 2 + buttonGap,
                        )
                        .background(confirmBackground)
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    @DrawableRes icon: Int? = null,
    contentDescription: String? = text,
    contentColor: Color = Color.Unspecified,
    fontSize: TextUnit = 28.sp,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MoneyTheme.colors
    val resolvedContentColor = when {
        !isEnabled -> colors.outlineDisabled
        contentColor != Color.Unspecified -> contentColor
        else -> colors.onBackground
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .then(modifier)
            .then(
                if (onLongClick != null)
                    Modifier.combinedClickable(
                        enabled = isEnabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                else
                    Modifier.clickable(
                        enabled = isEnabled,
                        onClick = onClick,
                    )
            )
            .border(
                width = 1.dp,
                color = colors.outline,
                shape = shape,
            )
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = resolvedContentColor,
                modifier = Modifier.size(26.dp),
            )
        }
        if (text != null) {
            Text(
                text = text,
                fontSize = fontSize,
                color = resolvedContentColor,
            )
        }
    }
}

enum class AmountKeyboardMainAction {
    Done,
    Next,
    ;
}

@Preview
@Composable
private fun Preview(

) {
    AmountKeyboard(
        inputState = rememberAmountInputState(
            currency = ViewCurrency(
                symbol = "$",
                precision = 2,
            ),
            initialValue = BigInteger.ZERO,
        ),
        colorScheme = HardcodedItemColorSchemeRepository()
            .getItemColorSchemes()[22],
        modifier = Modifier
            .size(
                width = 250.dp,
                height = 200.dp,
            )
    )
}
