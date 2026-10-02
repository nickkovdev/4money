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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A rounded surface group holding list rows, separated by [ListDivider].
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .clip(MoneyShapes.large)
        .background(MoneyTheme.colors.surface),
    content = content,
)

/**
 * A hairline between rows of a [ListGroup].
 *
 * @param startInset to align with the row text instead of the leading tile
 */
@Composable
fun ListDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
) = Box(
    modifier = modifier
        .padding(start = startInset)
        .fillMaxWidth()
        .height(1.dp)
        .background(MoneyTheme.colors.line)
)

/**
 * The inset for [ListDivider] matching a row with a [MoneySpacing.itemTile] leading tile.
 */
val ListRowTileDividerInset: Dp = MoneySpacing.rowHorizontal + MoneySpacing.itemTile + 14.dp

/**
 * A list row: leading tile, title with an optional subtitle, trailing content.
 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = MoneyTheme.colors.ink,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null)
                Modifier.clickable(onClick = onClick)
            else
                Modifier
        )
        .padding(
            horizontal = MoneySpacing.rowHorizontal,
            vertical = MoneySpacing.rowVertical,
        )
) {
    leading?.invoke()

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
    ) {
        Text(
            text = title,
            style = MoneyTheme.typography.bodyStrong,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    trailing?.invoke(this)
}

/**
 * A tinted square tile with an icon, for rows without an item logo (settings, actions).
 */
@Composable
fun IconTile(
    modifier: Modifier = Modifier,
    @DrawableRes
    icon: Int,
    tint: Color = MoneyTheme.colors.accent,
    background: Color = MoneyTheme.colors.accentTint,
    size: Dp = 36.dp,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .size(size)
        .background(
            color = background,
            shape = MoneyShapes.itemTile,
        )
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(size * 0.55f)
    )
}

/**
 * A chevron for navigating rows.
 */
@Composable
fun RowChevron() = Icon(
    painter = painterResource(R.drawable.ic_tabler_chevron_right),
    contentDescription = null,
    tint = MoneyTheme.colors.ink3,
    modifier = Modifier
        .size(MoneySpacing.iconSmall)
)

/**
 * An upper-cased section header with an optional trailing value (a total).
 */
@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    trailing: String? = null,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .fillMaxWidth()
        .padding(
            horizontal = 4.dp,
            vertical = 8.dp,
        )
) {
    Text(
        text = title.uppercase(),
        style = MoneyTheme.typography.overline,
        color = MoneyTheme.colors.ink3,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(1f)
    )

    if (trailing != null) {
        Text(
            text = trailing,
            style = MoneyTheme.typography.overline,
            color = MoneyTheme.colors.ink3,
            maxLines = 1,
        )
    }
}

@Composable
private fun ListPreviewContent() = Column(
    modifier = Modifier
        .background(MoneyTheme.colors.background)
        .padding(16.dp)
) {
    SectionHeader(
        title = "Regular",
        trailing = "2,710.40 €",
    )
    ListGroup {
        ListRow(
            title = "Card",
            subtitle = "SEB",
            leading = { IconTile(icon = R.drawable.ic_tabler_wallet, size = MoneySpacing.itemTile) },
            onClick = {},
            trailing = {
                Text(
                    text = "1,820.40 €",
                    style = MoneyTheme.typography.bodyStrong,
                )
            },
        )
        ListDivider(startInset = ListRowTileDividerInset)
        ListRow(
            title = "Appearance",
            leading = { IconTile(icon = R.drawable.ic_tabler_palette) },
            onClick = {},
            trailing = { RowChevron() },
        )
    }
}

@Preview(widthDp = 340)
@Composable
private fun ListPaperPreview() = MoneyTheme {
    ListPreviewContent()
}

@Preview(widthDp = 340)
@Composable
private fun ListMidnightPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    ListPreviewContent()
}
