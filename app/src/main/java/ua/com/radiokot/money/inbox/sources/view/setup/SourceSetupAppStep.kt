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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.view.AppIcon
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Step 2: the app to read the notifications of.
 */
@Composable
fun SourceSetupAppStep(
    detectedApps: State<List<SourceSetupViewModel.ViewApp>>,
    selectedPackage: String?,
    chosenAppLabel: State<String?>,
    onAppSelected: (packageName: String) -> Unit,
    onPickFromAllClicked: () -> Unit,
    onNextClicked: () -> Unit,
) = SetupStepLayout(
    footer = {
        MoneyButton(
            text = stringResource(R.string.setup_next),
            style = MoneyButtonStyle.Filled,
            isEnabled = selectedPackage != null,
            onClick = onNextClicked,
            modifier = Modifier
                .fillMaxWidth()
        )
    },
    body = {
        val apps = detectedApps.value

        Text(
            text = stringResource(
                if (apps.isEmpty())
                    R.string.setup_app_none_found
                else
                    R.string.setup_app_text
            ),
            style = MoneyTheme.typography.body,
            color = MoneyTheme.colors.ink2,
        )

        if (apps.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.setup_app_looks_like_bank),
            )

            ListGroup {
                apps.forEachIndexed { index, item ->
                    if (index != 0) {
                        ListDivider()
                    }

                    DetectedAppRow(
                        item = item,
                        isSelected = item.app.packageName == selectedPackage,
                        onClick = { onAppSelected(item.app.packageName) },
                    )
                }
            }
        }

        SectionHeader(
            title = stringResource(R.string.setup_app_not_listed),
        )

        // An app chosen from the full list is not among the detected ones.
        val isChosenFromAll = selectedPackage != null &&
                apps.none { it.app.packageName == selectedPackage }

        ListGroup {
            ListRow(
                title =
                    if (isChosenFromAll)
                        chosenAppLabel.value ?: selectedPackage.orEmpty()
                    else
                        stringResource(R.string.setup_app_pick_all),
                subtitle =
                    if (isChosenFromAll)
                        stringResource(R.string.setup_app_pick_all)
                    else
                        stringResource(R.string.setup_app_pick_all_hint),
                leading =
                    if (isChosenFromAll)
                        ({ AppIcon(selectedPackage.orEmpty(), chosenAppLabel.value.orEmpty()) })
                    else
                        ({ IconTile(icon = R.drawable.ic_tabler_apps) }),
                trailing = {
                    if (isChosenFromAll)
                        SelectionMark(isSelected = true)
                    else
                        RowChevron()
                },
                onClick = onPickFromAllClicked,
            )
        }
    },
)

@Composable
private fun DetectedAppRow(
    item: SourceSetupViewModel.ViewApp,
    isSelected: Boolean,
    onClick: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(
            horizontal = MoneySpacing.rowHorizontal,
            vertical = MoneySpacing.rowVertical,
        )
) {
    AppIcon(
        packageName = item.app.packageName,
        label = item.label,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
    ) {
        Text(
            text = item.label,
            style = MoneyTheme.typography.bodyStrong,
            color = MoneyTheme.colors.ink,
            maxLines = 1,
        )

        // When everything came today, the week would only be confusing.
        val isAllToday = item.app.count == item.app.todayCount
        Text(
            text =
                if (isAllToday)
                    pluralStringResource(R.plurals.setup_app_count_today, item.app.count, item.app.count)
                else
                    pluralStringResource(R.plurals.setup_app_count_week, item.app.count, item.app.count),
            style = MoneyTheme.typography.caption,
            color = MoneyTheme.colors.ink3,
        )

        if (item.app.isDuplicateWallet) {
            Text(
                text = stringResource(R.string.setup_app_wallet_warning),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.warning,
            )
        }
    }

    SelectionMark(isSelected = isSelected)
}

/**
 * A radio mark: a ring, or an accent disc with a check.
 */
@Composable
internal fun SelectionMark(
    isSelected: Boolean,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .size(24.dp)
        .clip(MoneyShapes.circle)
        .then(
            if (isSelected)
                Modifier.background(MoneyTheme.colors.accent)
            else
                Modifier.border(2.dp, MoneyTheme.colors.line, MoneyShapes.circle)
        )
) {
    if (isSelected) {
        Icon(
            painter = painterResource(R.drawable.ic_tabler_check),
            contentDescription = null,
            tint = MoneyTheme.colors.onAccent,
            modifier = Modifier
                .size(16.dp)
        )
    }
}
