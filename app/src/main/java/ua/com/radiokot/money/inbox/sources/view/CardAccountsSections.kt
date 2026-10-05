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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.ListRowTileDividerInset
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * The card → account rows, then the no-card account of every source.
 * Shared by the Cards and accounts screen and the last step of the setup wizard.
 */
@Composable
fun AccountMappingSection(
    mapping: ViewAccountMapping,
    onRowClicked: (ViewAccountMappingRow) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier,
) {
    SectionHeader(title = stringResource(R.string.cards_section_cards))

    if (mapping.cardRows.isEmpty()) {
        Text(
            text = stringResource(R.string.cards_empty_text),
            style = MoneyTheme.typography.caption,
            color = MoneyTheme.colors.ink3,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        )
    } else {
        MappingGroup(
            rows = mapping.cardRows,
            icon = R.drawable.ic_tabler_credit_card,
            onRowClicked = onRowClicked,
        )
    }

    if (mapping.sourceRows.isNotEmpty()) {
        SectionHeader(
            title = stringResource(R.string.cards_section_without_card),
            modifier = Modifier
                .padding(top = 12.dp)
        )

        MappingGroup(
            rows = mapping.sourceRows,
            icon = R.drawable.ic_tabler_bell,
            onRowClicked = onRowClicked,
        )
    }
}

@Composable
private fun MappingGroup(
    rows: List<ViewAccountMappingRow>,
    icon: Int,
    onRowClicked: (ViewAccountMappingRow) -> Unit,
) = ListGroup {
    rows.forEachIndexed { index, row ->
        if (index > 0) {
            ListDivider(startInset = ListRowTileDividerInset)
        }

        ListRow(
            title = row.title.resolve(),
            subtitle = row.accountTitle
                ?: stringResource(R.string.cards_choose_account),
            leading = { IconTile(icon = icon) },
            trailing = { RowChevron() },
            onClick = { onRowClicked(row) },
        )
    }
}

/**
 * The three global switches. Shared by the Cards and accounts screen
 * and the last step of the setup wizard.
 */
@Composable
fun BehaviourSection(
    behaviour: AutoBookBehaviour,
    onRecordKnownPayeesChanged: (Boolean) -> Unit,
    onAskInNotificationChanged: (Boolean) -> Unit,
    onLearnFromHistoryChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier,
) {
    SectionHeader(title = stringResource(R.string.behaviour_section))

    ListGroup {
        BehaviourRow(
            title = stringResource(R.string.behaviour_record_title),
            text = stringResource(R.string.behaviour_record_text),
            isOn = behaviour.recordKnownPayees,
            onChanged = onRecordKnownPayeesChanged,
        )
        ListDivider()
        BehaviourRow(
            title = stringResource(R.string.behaviour_ask_title),
            text = stringResource(R.string.behaviour_ask_text),
            isOn = behaviour.askInNotification,
            onChanged = onAskInNotificationChanged,
        )
        ListDivider()
        BehaviourRow(
            title = stringResource(R.string.behaviour_learn_title),
            text = stringResource(R.string.behaviour_learn_text),
            isOn = behaviour.learnFromHistory,
            onChanged = onLearnFromHistoryChanged,
        )
    }

    Text(
        text = stringResource(R.string.behaviour_footer),
        style = MoneyTheme.typography.caption,
        color = MoneyTheme.colors.ink3,
        modifier = Modifier
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun BehaviourRow(
    title: String,
    text: String,
    isOn: Boolean,
    onChanged: (Boolean) -> Unit,
) = ListRow(
    title = title,
    subtitle = text,
    trailing = {
        MoneySwitch(
            isOn = isOn,
            onToggled = onChanged,
        )
    },
    onClick = { onChanged(!isOn) },
)
