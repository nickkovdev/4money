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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A screen header: a round navigation button (close or back), the title, optional actions.
 */
@Composable
fun ScreenTopBar(
    modifier: Modifier = Modifier,
    title: String,
    onNavigationClicked: () -> Unit,
    @DrawableRes
    navigationIcon: Int = R.drawable.ic_tabler_x,
    navigationContentDescription: String = stringResource(R.string.common_close),
    actions: (@Composable RowScope.() -> Unit)? = null,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier
        .fillMaxWidth()
        .padding(
            horizontal = MoneySpacing.screen,
            vertical = 12.dp,
        )
) {
    MoneyIconButton(
        icon = navigationIcon,
        contentDescription = navigationContentDescription,
        onClick = onNavigationClicked,
    )

    Text(
        text = title,
        style = MoneyTheme.typography.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(1f)
    )

    actions?.invoke(this)
}
