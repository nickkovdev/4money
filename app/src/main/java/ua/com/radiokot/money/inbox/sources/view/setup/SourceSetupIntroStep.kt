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

package ua.com.radiokot.money.inbox.sources.view.setup

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.ListRowTileDividerInset
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Step 1: what the auto-booking does and the notification access.
 */
@Composable
fun SourceSetupIntroStep(
    isAccessGranted: State<Boolean>,
    onGrantClicked: () -> Unit,
    onNextClicked: () -> Unit,
    onLaterClicked: () -> Unit,
) = SetupStepLayout(
    footer = {
        if (isAccessGranted.value) {
            MoneyButton(
                text = stringResource(R.string.setup_next),
                style = MoneyButtonStyle.Filled,
                onClick = onNextClicked,
                modifier = Modifier
                    .fillMaxWidth()
            )
        } else {
            MoneyButton(
                text = stringResource(R.string.setup_intro_grant),
                style = MoneyButtonStyle.Filled,
                onClick = onGrantClicked,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        MoneyButton(
            text = stringResource(R.string.setup_later),
            style = MoneyButtonStyle.Text,
            onClick = onLaterClicked,
            modifier = Modifier
                .fillMaxWidth()
        )
    },
    body = {
        IconTile(
            icon = R.drawable.ic_tabler_bolt,
            size = 72.dp,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_intro_title),
                style = MoneyTheme.typography.headline,
                color = MoneyTheme.colors.ink,
            )
            Text(
                text = stringResource(R.string.setup_intro_text),
                style = MoneyTheme.typography.body,
                color = MoneyTheme.colors.ink2,
            )
        }

        ListGroup {
            ListRow(
                title = stringResource(R.string.setup_intro_example_1_title),
                subtitle = stringResource(R.string.setup_intro_example_1_text),
                leading = { IconTile(icon = R.drawable.ic_tabler_bell) },
            )
            ListDivider(startInset = ListRowTileDividerInset)
            ListRow(
                title = stringResource(R.string.setup_intro_example_2_title),
                subtitle = stringResource(R.string.setup_intro_example_2_text),
                leading = { IconTile(icon = R.drawable.ic_tabler_adjustments_horizontal) },
            )
            ListDivider(startInset = ListRowTileDividerInset)
            ListRow(
                title = stringResource(R.string.setup_intro_example_3_title),
                subtitle = stringResource(R.string.setup_intro_example_3_text),
                leading = { IconTile(icon = R.drawable.ic_tabler_inbox) },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_shield_check),
                contentDescription = null,
                tint = MoneyTheme.colors.income,
                modifier = Modifier
                    .size(20.dp)
            )
            Text(
                text = stringResource(R.string.setup_intro_privacy),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink2,
            )
        }

        // Android 13+ restricts the access for apps installed outside of a store.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isAccessGranted.value) {
            Text(
                text = stringResource(R.string.setup_intro_restricted_hint),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink3,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            )
        }
    },
)
