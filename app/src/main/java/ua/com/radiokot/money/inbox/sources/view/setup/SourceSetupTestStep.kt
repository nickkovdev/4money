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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.logic.TestRunResult
import ua.com.radiokot.money.inbox.sources.view.rememberLastReceivedFormatter
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Step 5: how many of the recent notifications of the app the preset and the taught kinds understand.
 * The amounts are masked while the privacy mode is on.
 */
@Composable
fun SourceSetupTestStep(
    packageName: String?,
    appLabel: State<String?>,
    result: TestRunResult?,
    canFinish: Boolean,
    onTeachAnotherClicked: () -> Unit,
    onNextClicked: () -> Unit,
) = SetupStepLayout(
    footer = {
        if (!canFinish) {
            Text(
                text = stringResource(R.string.setup_test_teach_first),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink2,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            )
        }

        MoneyButton(
            text = stringResource(R.string.setup_next),
            style = MoneyButtonStyle.Filled,
            isEnabled = canFinish,
            onClick = onNextClicked,
            modifier = Modifier
                .fillMaxWidth()
        )
    },
    body = {
        val label = appLabel.value ?: packageName.orEmpty()
        val total = (result?.matched?.size ?: 0) + (result?.unmatched?.size ?: 0)

        if (result == null || total == 0) {
            EmptyState(
                icon = R.drawable.ic_tabler_bell,
                title = stringResource(R.string.setup_test_empty_title),
                text = stringResource(R.string.setup_test_empty_text, label),
            )
        } else {
            Score(
                matchedCount = result.matched.size,
                total = total,
                label = label,
            )

            Results(result = result)
        }

        MoneyButton(
            text = stringResource(R.string.sources_teach_another),
            style = MoneyButtonStyle.Tonal,
            icon = R.drawable.ic_tabler_plus,
            onClick = onTeachAnotherClicked,
            modifier = Modifier
                .fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.setup_test_kinds_hint),
            style = MoneyTheme.typography.caption,
            color = MoneyTheme.colors.ink2,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        )
    },
)

@Composable
private fun Score(
    matchedCount: Int,
    total: Int,
    label: String,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(
        text = stringResource(R.string.setup_test_score, matchedCount, total),
        style = MoneyTheme.typography.display,
        color =
            if (matchedCount == total)
                MoneyTheme.colors.income
            else
                MoneyTheme.colors.ink,
    )

    Text(
        text = pluralStringResource(R.plurals.setup_test_score_text, total, label),
        style = MoneyTheme.typography.body,
        color = MoneyTheme.colors.ink2,
        modifier = Modifier
            .weight(1f)
    )
}

@Composable
private fun Results(
    result: TestRunResult,
) {
    val formatTime = rememberLastReceivedFormatter()
    val amountFormat = rememberViewAmountFormat()
    val isPrivate = LocalPrivacyMode.current
    val understood = stringResource(R.string.setup_test_understood)
    val notUnderstood = stringResource(R.string.setup_test_not_understood)
    val otherKind = stringResource(R.string.setup_test_other_kind)

    fun timeOf(notification: RecentNotification): String =
        formatTime(
            Instant.fromEpochMilliseconds(notification.postTimeMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        )

    ListGroup {
        result.matched.forEachIndexed { index, (notification, payment) ->
            if (index > 0) {
                ListDivider()
            }

            val time = timeOf(notification)
            val amountColor =
                if (payment.isIncoming)
                    MoneyTheme.colors.income
                else
                    MoneyTheme.colors.expense

            ListRow(
                title = PayeeNormalizer.displayName(payment.payee),
                subtitle =
                    if (payment.cardLast4 != null)
                        stringResource(R.string.setup_test_time_card, time, payment.cardLast4)
                    else
                        time,
                leading = {
                    ResultMark(
                        icon = R.drawable.ic_tabler_check,
                        tint = MoneyTheme.colors.income,
                        background = MoneyTheme.colors.incomeTint,
                        description = understood,
                    )
                },
                trailing = {
                    Text(
                        text = amountFormat
                            .formatOrPrivate(viewAmountOfPayment(payment), customColor = amountColor)
                            .text,
                        style = MoneyTheme.typography.label,
                        color = amountColor,
                        maxLines = 1,
                    )
                },
            )
        }

        result.unmatched.forEachIndexed { index, notification ->
            if (index > 0 || result.matched.isNotEmpty()) {
                ListDivider()
            }

            ListRow(
                title = unmatchedTitle(notification, isPrivate),
                subtitle = otherKind,
                leading = {
                    ResultMark(
                        icon = R.drawable.ic_tabler_x,
                        tint = MoneyTheme.colors.warning,
                        background = MoneyTheme.colors.warning.copy(alpha = 0.16f),
                        description = notUnderstood,
                    )
                },
            )
        }
    }
}

/**
 * The title, or else the first line of the text. The amounts that may be in it
 * are not shown while the privacy mode is on.
 */
private fun unmatchedTitle(
    notification: RecentNotification,
    isPrivate: Boolean,
): String {
    val title = notification.title
        ?.takeIf(String::isNotBlank)
        ?: notification.text.lineSequence().firstOrNull(String::isNotBlank).orEmpty()

    return if (isPrivate)
        title.replace(Regex("\\d"), "•")
    else
        title.trim()
}

@Composable
private fun ResultMark(
    @DrawableRes
    icon: Int,
    tint: Color,
    background: Color,
    description: String,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .size(32.dp)
        .clip(MoneyShapes.circle)
        .background(background)
        .semantics { contentDescription = description }
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(18.dp)
    )
}
