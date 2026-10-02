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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.com.radiokot.money.transfers.history.view.PeriodBar
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import ua.com.radiokot.money.uikit.theme.MoneySpacing

/**
 * The header of a period-based home tab: the profile button, then the period bar.
 */
@Composable
fun HomeTabHeader(
    modifier: Modifier = Modifier,
    period: State<ViewHistoryPeriod>,
    onPeriodClicked: () -> Unit,
    isNextPeriodButtonEnabled: State<Boolean>,
    onNextPeriodClicked: () -> Unit,
    isPreviousPeriodButtonEnabled: State<Boolean>,
    onPreviousPeriodClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
        .fillMaxWidth()
        .padding(
            horizontal = MoneySpacing.screen,
            vertical = 12.dp,
        )
) {
    HomeProfileButton()

    PeriodBar(
        period = period,
        onPeriodClicked = onPeriodClicked,
        isNextButtonEnabled = isNextPeriodButtonEnabled,
        onNextPeriodClicked = onNextPeriodClicked,
        isPreviousButtonEnabled = isPreviousPeriodButtonEnabled,
        onPreviousPeriodClicked = onPreviousPeriodClicked,
        modifier = Modifier
            .weight(1f)
    )
}
