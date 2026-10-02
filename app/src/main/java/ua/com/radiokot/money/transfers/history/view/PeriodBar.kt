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

package ua.com.radiokot.money.transfers.history.view

import androidx.compose.ui.res.stringResource
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import ua.com.radiokot.money.uikit.MoneyIconButton
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.transfers.view.ViewDate
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Previous / next round buttons around the period title.
 */
@Composable
fun PeriodBar(
    modifier: Modifier = Modifier,
    period: State<ViewHistoryPeriod>,
    onPeriodClicked: () -> Unit,
    isNextButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    isPreviousButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier,
) {
    MoneyIconButton(
        icon = R.drawable.ic_tabler_chevron_left,
        contentDescription = stringResource(R.string.history_previous_period),
        isEnabled = isPreviousButtonEnabled.value,
        onClick = onPreviousPeriodClicked,
    )

    Text(
        text = period.value.getText(),
        style = MoneyTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .weight(1f)
            .padding(
                horizontal = 8.dp,
            )
    )

    MoneyIconButton(
        icon = R.drawable.ic_tabler_chevron_right,
        contentDescription = stringResource(R.string.history_next_period),
        isEnabled = isNextButtonEnabled.value,
        onClick = onNextPeriodClicked,
    )
}

@Preview(
    widthDp = 160,
)
@Composable
private fun PeriodBarPreview(
) = PeriodBar(
    modifier = Modifier
        .fillMaxWidth(),
    period =
        ViewHistoryPeriod.Day(
            day = ViewDate.today()
        ).let(::mutableStateOf),
    onPeriodClicked = {

    },
    isNextButtonEnabled = false.let(::mutableStateOf),
    onNextPeriodClicked = { },
    isPreviousButtonEnabled = true.let(::mutableStateOf),
    onPreviousPeriodClicked = { }
)
