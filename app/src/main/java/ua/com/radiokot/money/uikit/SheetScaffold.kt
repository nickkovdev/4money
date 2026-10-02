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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * The drag handle at the top of a bottom sheet.
 */
@Composable
fun SheetHandle(
    modifier: Modifier = Modifier,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .fillMaxWidth()
        .padding(
            top = 10.dp,
            bottom = 6.dp,
        )
) {
    Box(
        modifier = Modifier
            .size(
                width = 40.dp,
                height = 4.dp,
            )
            .background(
                color = MoneyTheme.colors.line,
                shape = MoneyShapes.pill,
            )
    )
}

/**
 * The common frame of a bottom sheet: background, handle, an optional title row
 * with a close button, the content and the navigation bar inset at the bottom,
 * so the last row never sits under the gesture bar.
 */
@Composable
fun SheetScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClose: (() -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    applyNavigationBarsPadding: Boolean = true,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .background(
            color = MoneyTheme.colors.background,
            shape = MoneyShapes.sheet,
        )
        .then(
            if (applyNavigationBarsPadding)
                Modifier.navigationBarsPadding()
            else
                Modifier
        )
        .padding(
            horizontal = horizontalPadding,
        )
        .padding(
            bottom = 12.dp,
        )
) {
    SheetHandle()

    if (title != null || onClose != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoneySpacing.gap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    bottom = 8.dp,
                )
        ) {
            Text(
                text = title ?: "",
                style = MoneyTheme.typography.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
            )

            titleTrailing?.invoke()

            if (onClose != null) {
                MoneyIconButton(
                    icon = R.drawable.ic_tabler_x,
                    contentDescription = "Close",
                    onClick = onClose,
                    size = 40.dp,
                    iconSize = 20.dp,
                )
            }
        }
    }

    content()
}

@Preview(widthDp = 360)
@Composable
private fun SheetScaffoldPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    SheetScaffold(
        title = "Select account",
        onClose = {},
    ) {
        ListGroup {
            ListRow(title = "Card")
            ListDivider()
            ListRow(title = "Cash")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.width(1.dp)) {}
    }
}
