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

package ua.com.radiokot.money.inbox.sources.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.theme.MoneySpacing

@Composable
fun CardAccountsScreen(
    viewModel: CardAccountsScreenViewModel,
) = Column {

    ScreenTopBar(
        title = stringResource(R.string.sources_cards_title),
        navigationIcon = R.drawable.ic_tabler_arrow_left,
        navigationContentDescription = stringResource(R.string.common_back),
        onNavigationClicked = viewModel::onBackClicked,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = MoneySpacing.screen,
                end = MoneySpacing.screen,
                bottom = 24.dp,
            )
    ) {
        AccountMappingSection(
            mapping = viewModel.mapping.collectAsState().value,
            onRowClicked = viewModel::onRowClicked,
        )

        BehaviourSection(
            behaviour = viewModel.behaviour.collectAsState().value,
            onRecordKnownPayeesChanged = viewModel::onRecordKnownPayeesChanged,
            onAskInNotificationChanged = viewModel::onAskInNotificationChanged,
            onLearnFromHistoryChanged = viewModel::onLearnFromHistoryChanged,
        )
    }
}
