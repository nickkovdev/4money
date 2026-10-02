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

package ua.com.radiokot.money.colors.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A large item logo with a pencil badge and a hint, opening the logo picker.
 */
@Composable
fun EditableItemLogo(
    modifier: Modifier = Modifier,
    title: String,
    colorScheme: ItemColorScheme,
    icon: ItemIcon?,
    onClick: () -> Unit,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
        .clip(MoneyShapes.large)
        .clickable(onClick = onClick)
        .padding(8.dp)
) {
    Box {
        ItemLogo(
            title = title,
            colorScheme = colorScheme,
            icon = icon,
            modifier = Modifier
                .size(76.dp)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp, y = 6.dp)
                .size(28.dp)
                .background(
                    color = MoneyTheme.colors.background,
                    shape = MoneyShapes.circle,
                )
                .padding(3.dp)
                .background(
                    color = MoneyTheme.colors.accent,
                    shape = MoneyShapes.circle,
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_pencil),
                contentDescription = null,
                tint = MoneyTheme.colors.onAccent,
                modifier = Modifier
                    .size(13.dp)
            )
        }
    }

    Text(
        text = "Icon and color",
        style = MoneyTheme.typography.caption,
        color = MoneyTheme.colors.ink3,
    )
}
