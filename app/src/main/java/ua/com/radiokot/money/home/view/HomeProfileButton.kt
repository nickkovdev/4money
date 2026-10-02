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

package ua.com.radiokot.money.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * The profile button each home tab puts at the start of its header,
 * so there is no separate row just for it. Empty outside the home screen.
 */
val LocalHomeProfileButton = staticCompositionLocalOf<@Composable () -> Unit> { {} }

@Composable
fun HomeProfileButton() = LocalHomeProfileButton.current()

@Composable
internal fun ProfileButton(
    hasNotice: Boolean,
    onClick: () -> Unit,
) = Box {
    MoneyIconButton(
        icon = R.drawable.ic_tabler_user_circle,
        contentDescription = "Profile and settings",
        onClick = onClick,
        tint = MoneyTheme.colors.ink2,
    )

    if (hasNotice) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .size(10.dp)
                .background(
                    color = MoneyTheme.colors.expense,
                    shape = MoneyShapes.circle,
                )
        )
    }
}
