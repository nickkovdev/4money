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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.view.AccountMappingSection
import ua.com.radiokot.money.inbox.sources.view.BehaviourSection
import ua.com.radiokot.money.inbox.sources.view.ViewAccountMapping
import ua.com.radiokot.money.inbox.sources.view.ViewAccountMappingRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Step 6: the account of every card and of the payments without a card, the behaviour switches.
 * Nothing is saved until Done.
 */
@Composable
fun SourceSetupAccountsStep(
    mapping: ViewAccountMapping,
    behaviour: AutoBookBehaviour,
    isSaving: Boolean,
    isSaveFailed: Boolean,
    onRowClicked: (ViewAccountMappingRow) -> Unit,
    onRecordKnownPayeesChanged: (Boolean) -> Unit,
    onAskInNotificationChanged: (Boolean) -> Unit,
    onLearnFromHistoryChanged: (Boolean) -> Unit,
    onDoneClicked: () -> Unit,
) = SetupStepLayout(
    footer = {
        if (isSaveFailed) {
            Text(
                text = stringResource(R.string.setup_accounts_save_failed),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.expense,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            )
        }

        MoneyButton(
            text = stringResource(R.string.common_done),
            style = MoneyButtonStyle.Filled,
            isEnabled = !isSaving,
            onClick = onDoneClicked,
            modifier = Modifier
                .fillMaxWidth()
        )
    },
    body = {
        AccountMappingSection(
            mapping = mapping,
            onRowClicked = onRowClicked,
        )

        BehaviourSection(
            behaviour = behaviour,
            onRecordKnownPayeesChanged = onRecordKnownPayeesChanged,
            onAskInNotificationChanged = onAskInNotificationChanged,
            onLearnFromHistoryChanged = onLearnFromHistoryChanged,
        )
    },
)
