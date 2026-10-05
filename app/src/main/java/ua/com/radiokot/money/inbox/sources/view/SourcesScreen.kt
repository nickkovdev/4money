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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.transfers.view.ViewDateFormats
import ua.com.radiokot.money.transfers.view.rememberAppLocale
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.ListRowTileDividerInset
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import kotlin.time.Clock

@Composable
fun SourcesScreen(
    viewModel: SourcesScreenViewModel,
) = SourcesScreen(
    sourceList = viewModel.sourceList.collectAsState(),
    isAccessGranted = viewModel.isAccessGranted.collectAsState(),
    rulesSubtitle = viewModel.rulesSubtitle.collectAsState(),
    onBackClicked = viewModel::onBackClicked,
    onAccessClicked = viewModel::onAccessClicked,
    onSourceSwitched = viewModel::onSourceSwitched,
    onTeachAnotherKindClicked = viewModel::onTeachAnotherKindClicked,
    onAddAppClicked = viewModel::onAddAppClicked,
    onCardsClicked = viewModel::onCardsClicked,
    onRulesClicked = viewModel::onRulesClicked,
    onTestTextClicked = viewModel::onTestTextClicked,
)

@Composable
private fun SourcesScreen(
    sourceList: State<List<SourcesScreenViewModel.ViewSource>>,
    isAccessGranted: State<Boolean>,
    rulesSubtitle: State<ViewText>,
    onBackClicked: () -> Unit,
    onAccessClicked: () -> Unit,
    onSourceSwitched: (SourcesScreenViewModel.ViewSource, Boolean) -> Unit,
    onTeachAnotherKindClicked: (SourcesScreenViewModel.ViewSource) -> Unit,
    onAddAppClicked: () -> Unit,
    onCardsClicked: () -> Unit,
    onRulesClicked: () -> Unit,
    onTestTextClicked: () -> Unit,
) = Column {

    ScreenTopBar(
        title = stringResource(R.string.sources_title),
        navigationIcon = R.drawable.ic_tabler_arrow_left,
        navigationContentDescription = stringResource(R.string.common_back),
        onNavigationClicked = onBackClicked,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = MoneySpacing.screen,
                end = MoneySpacing.screen,
                bottom = 24.dp,
            )
    ) {
        AccessRow(
            isGranted = isAccessGranted.value,
            onClick = onAccessClicked,
        )

        SectionHeader(
            title = stringResource(R.string.sources_section_sources),
            modifier = Modifier
                .padding(top = 8.dp)
        )

        val formatLastReceived = rememberLastReceivedFormatter()
        sourceList.value.forEach { item ->
            SourceCard(
                item = item,
                formatLastReceived = formatLastReceived,
                onSwitched = { onSourceSwitched(item, it) },
                onTeachAnotherKindClicked = { onTeachAnotherKindClicked(item) },
            )
        }

        MoneyButton(
            text = stringResource(R.string.sources_add_app),
            icon = R.drawable.ic_tabler_plus,
            style = MoneyButtonStyle.Tonal,
            onClick = onAddAppClicked,
            modifier = Modifier
                .fillMaxWidth()
        )

        SectionHeader(
            title = stringResource(R.string.sources_section_more),
            modifier = Modifier
                .padding(top = 8.dp)
        )

        ListGroup {
            ListRow(
                title = stringResource(R.string.sources_cards_title),
                subtitle = stringResource(R.string.sources_cards_subtitle),
                leading = { IconTile(icon = R.drawable.ic_tabler_credit_card) },
                trailing = { RowChevron() },
                onClick = onCardsClicked,
            )
            ListDivider(startInset = ListRowTileDividerInset)
            ListRow(
                title = stringResource(R.string.rules_title),
                subtitle = rulesSubtitle.value.resolve(),
                leading = { IconTile(icon = R.drawable.ic_tabler_adjustments_horizontal) },
                trailing = { RowChevron() },
                onClick = onRulesClicked,
            )
            ListDivider(startInset = ListRowTileDividerInset)
            ListRow(
                title = stringResource(R.string.sources_test_title),
                subtitle = stringResource(R.string.sources_test_subtitle),
                leading = { IconTile(icon = R.drawable.ic_tabler_notes) },
                trailing = { RowChevron() },
                onClick = onTestTextClicked,
            )
        }
    }
}

@Composable
private fun AccessRow(
    isGranted: Boolean,
    onClick: () -> Unit,
) = ListGroup {
    ListRow(
        title = stringResource(R.string.settings_notification_access),
        subtitle = stringResource(
            if (isGranted)
                R.string.sources_access_granted
            else
                R.string.sources_access_missing
        ),
        leading = { IconTile(icon = R.drawable.ic_tabler_bell) },
        trailing =
            if (isGranted) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_circle_check),
                        contentDescription = stringResource(R.string.settings_granted),
                        tint = MoneyTheme.colors.income,
                        modifier = Modifier
                            .size(22.dp)
                    )
                }
            } else {
                {
                    MoneyButton(
                        text = stringResource(R.string.settings_allow),
                        style = MoneyButtonStyle.Filled,
                        onClick = onClick,
                        contentPadding = PaddingValues(
                            horizontal = 14.dp,
                            vertical = 8.dp,
                        ),
                        modifier = Modifier
                            .height(36.dp)
                    )
                }
            },
        onClick =
            if (isGranted)
                null
            else
                onClick,
    )
}

@Composable
private fun SourceCard(
    item: SourcesScreenViewModel.ViewSource,
    formatLastReceived: (LocalDateTime) -> String,
    onSwitched: (Boolean) -> Unit,
    onTeachAnotherKindClicked: () -> Unit,
) = ListGroup {
    val texts = remember(item, formatLastReceived) {
        SourceCardTexts.of(
            source = item.source,
            stats = item.stats,
            formatLastReceived = formatLastReceived,
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(
                packageName = item.source.packageName,
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
                Text(
                    text = texts.meta.resolve(),
                    style = MoneyTheme.typography.caption,
                    color = MoneyTheme.colors.ink3,
                )
            }

            MoneySwitch(
                isOn = item.source.isEnabled,
                onToggled = onSwitched,
            )
        }

        if (texts.kindChips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                texts.kindChips.forEach { chip ->
                    KindChip(text = chip.resolve())
                }
            }
        }

        if (texts.recognized != null) {
            Text(
                text = texts.recognized.resolve(),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.income,
            )
        }

        MoneyButton(
            text = texts.teachActionText.resolve(),
            style = MoneyButtonStyle.Text,
            onClick = onTeachAnotherKindClicked,
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 10.dp,
            ),
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

@Composable
private fun KindChip(
    text: String,
) = Box(
    modifier = Modifier
        .clip(MoneyShapes.pill)
        .background(MoneyTheme.colors.surface2)
        .padding(
            horizontal = 12.dp,
            vertical = 5.dp,
        )
) {
    Text(
        text = text,
        style = MoneyTheme.typography.labelRegular,
        color = MoneyTheme.colors.ink2,
        maxLines = 1,
    )
}

/**
 * "Today 09:12" or "3 October 09:12" in the app locale.
 */
@Composable
private fun rememberLastReceivedFormatter(): (LocalDateTime) -> String {
    val resources = LocalResources.current
    val locale = rememberAppLocale()

    return remember(resources, locale) {
        { dateTime: LocalDateTime ->
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            val time = ViewDateFormats.time(dateTime, locale)

            if (dateTime.date == today)
                resources.getString(R.string.date_today_at, time)
            else
                resources.getString(
                    R.string.date_at_time,
                    ViewDateFormats.dayMonth(dateTime.date, locale, today),
                    time,
                )
        }
    }
}
