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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.view.AppIcon
import ua.com.radiokot.money.inbox.sources.view.rememberLastReceivedFormatter
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Step 3: the notification to learn from.
 * The texts are shown as they are, they are what the user is about to mark.
 */
@Composable
fun SourceSetupSampleStep(
    packageName: String?,
    appLabel: State<String?>,
    samples: List<RecentNotification>,
    selectedSample: RecentNotification?,
    onSampleSelected: (RecentNotification) -> Unit,
    onUseClicked: () -> Unit,
) = SetupStepLayout(
    footer = {
        MoneyButton(
            text = stringResource(R.string.setup_sample_use),
            style = MoneyButtonStyle.Filled,
            isEnabled = selectedSample != null,
            onClick = onUseClicked,
            modifier = Modifier
                .fillMaxWidth()
        )
    },
    body = {
        Text(
            text = stringResource(R.string.setup_sample_text),
            style = MoneyTheme.typography.body,
            color = MoneyTheme.colors.ink2,
        )

        val formatTime = rememberLastReceivedFormatter()
        val label = appLabel.value ?: packageName.orEmpty()

        samples.forEach { sample ->
            val time = remember(sample, formatTime) {
                formatTime(
                    Instant.fromEpochMilliseconds(sample.postTimeMillis)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
            }

            SampleCard(
                packageName = sample.packageName,
                header = stringResource(R.string.setup_sample_app_time, label, time),
                label = label,
                notification = sample,
                isSelected = sample == selectedSample,
                onClick = { onSampleSelected(sample) },
            )
        }

        if (samples.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_tabler_bell,
                title = stringResource(R.string.setup_sample_empty_title),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_info_circle),
                contentDescription = null,
                tint = MoneyTheme.colors.ink3,
                modifier = Modifier
                    .size(18.dp)
            )
            Text(
                text = stringResource(R.string.setup_sample_hint),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink2,
            )
        }
    },
)

@Composable
private fun SampleCard(
    packageName: String,
    header: String,
    label: String,
    notification: RecentNotification,
    isSelected: Boolean,
    onClick: () -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
        .fillMaxWidth()
        .clip(MoneyShapes.medium)
        .background(MoneyTheme.colors.surface)
        .border(
            width = 2.dp,
            color =
                if (isSelected)
                    MoneyTheme.colors.accent
                else
                    Color.Transparent,
            shape = MoneyShapes.medium,
        )
        .clickable(onClick = onClick)
        .padding(14.dp)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(
            packageName = packageName,
            label = label,
            size = 22.dp,
        )

        Text(
            text = header,
            style = MoneyTheme.typography.caption,
            color = MoneyTheme.colors.ink3,
            maxLines = 1,
        )
    }

    if (!notification.title.isNullOrBlank()) {
        Text(
            text = notification.title,
            style = MoneyTheme.typography.bodyStrong,
            color = MoneyTheme.colors.ink,
        )
    }

    Text(
        text = notification.text,
        style = MoneyTheme.typography.body,
        color = MoneyTheme.colors.ink,
    )
}
