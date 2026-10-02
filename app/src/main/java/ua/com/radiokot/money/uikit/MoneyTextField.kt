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

package ua.com.radiokot.money.uikit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A filled text field: surface background, rounded, optional leading icon,
 * the placeholder in tertiary ink. Never outlined.
 */
@Composable
fun MoneyTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    @DrawableRes
    leadingIcon: Int? = null,
    isEnabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = MoneyTheme.typography.body,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MoneyTheme.colors

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = isEnabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle.copy(
            color =
                if (isEnabled)
                    colors.ink
                else
                    colors.ink2,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier,
        decorationBox = { innerTextField ->
            FieldContainer(
                leadingIcon = leadingIcon,
                trailing = trailing,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * A field-looking row that opens a picker: label on top, value, chevron.
 * Read-only fields have no chevron and use secondary ink.
 */
@Composable
fun MoneyPickerField(
    modifier: Modifier = Modifier,
    value: String,
    onClick: (() -> Unit)?,
    @DrawableRes
    leadingIcon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MoneyTheme.colors

    Box(
        modifier = modifier
            .clip(MoneyShapes.medium)
            .then(
                if (onClick != null)
                    Modifier.clickable(onClick = onClick)
                else
                    Modifier
            )
    ) {
        FieldContainer(
            leadingIcon = leadingIcon,
            leading = leading,
            trailing =
                if (onClick != null) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_tabler_chevron_down),
                            contentDescription = null,
                            tint = colors.ink3,
                            modifier = Modifier
                                .size(MoneySpacing.iconSmall)
                        )
                    }
                } else null,
        ) {
            Text(
                text = value,
                style = MoneyTheme.typography.body,
                color =
                    if (onClick != null)
                        colors.ink
                    else
                        colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FieldContainer(
    @DrawableRes
    leadingIcon: Int?,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 50.dp)
        .clip(MoneyShapes.medium)
        .background(MoneyTheme.colors.surface)
        .padding(
            horizontal = 14.dp,
            vertical = 12.dp,
        )
) {
    if (leadingIcon != null) {
        Icon(
            painter = painterResource(leadingIcon),
            contentDescription = null,
            tint = MoneyTheme.colors.ink3,
            modifier = Modifier
                .size(MoneySpacing.iconSmall)
        )
    }
    leading?.invoke()

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .weight(1f)
    ) {
        content()
    }

    trailing?.invoke()
}

/**
 * A small label above a field.
 */
@Composable
fun FieldLabel(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    text = text,
    style = MoneyTheme.typography.caption,
    color = MoneyTheme.colors.ink3,
    modifier = modifier
        .padding(
            start = 4.dp,
            bottom = 6.dp,
        )
)

@Composable
private fun TextFieldPreviewContent() = Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
        .background(MoneyTheme.colors.background)
        .padding(16.dp)
) {
    Column {
        FieldLabel(text = "Title")
        MoneyTextField(value = "Card", onValueChange = {})
    }
    MoneyTextField(
        value = "",
        placeholder = "Note",
        leadingIcon = R.drawable.ic_tabler_notes,
        onValueChange = {},
    )
    MoneyPickerField(value = "Euro (€)", onClick = {})
    MoneyPickerField(value = "EUR", onClick = null)
}

@Preview(widthDp = 320)
@Composable
private fun TextFieldPaperPreview() = MoneyTheme {
    TextFieldPreviewContent()
}

@Preview(widthDp = 320)
@Composable
private fun TextFieldMidnightPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    TextFieldPreviewContent()
}
