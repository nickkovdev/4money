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

package ua.com.radiokot.money.transfers.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyChip
import ua.com.radiokot.money.uikit.MoneyDialogContainer
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * A themed month calendar in a dialog. Future dates can't be picked.
 */
@Composable
fun DatePickerDialog(
    initialDate: LocalDate,
    onDatePicked: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
) = MoneyDialogContainer(
    onDismissRequest = onDismissRequest,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val today = remember { java.time.LocalDate.now() }
    var selected by remember { mutableStateOf(initialDate.toJavaLocalDate()) }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val colors = MoneyTheme.colors

    Column(
        modifier = Modifier
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = remember(month, locale) {
                    ViewDateFormats.monthYear(month.atDay(1).toKotlinLocalDate(), locale)
                },
                style = MoneyTheme.typography.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            MoneyIconButton(
                icon = R.drawable.ic_tabler_chevron_left,
                contentDescription = stringResource(R.string.date_picker_previous_month),
                onClick = { month = month.minusMonths(1) },
                size = 40.dp,
                iconSize = 20.dp,
            )

            MoneyIconButton(
                icon = R.drawable.ic_tabler_chevron_right,
                contentDescription = stringResource(R.string.date_picker_next_month),
                isEnabled = month < YearMonth.from(today),
                onClick = { month = month.plusMonths(1) },
                size = 40.dp,
                iconSize = 20.dp,
                modifier = Modifier
                    .padding(start = 8.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(
                    top = 14.dp,
                    bottom = 10.dp,
                )
        ) {
            listOf(
                stringResource(R.string.date_today) to today,
                stringResource(R.string.date_yesterday) to today.minusDays(1),
            ).forEach { (label, date) ->
                MoneyChip(
                    text = label,
                    isSelected = selected == date,
                    onClick = {
                        selected = date
                        month = YearMonth.from(date)
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            (0 until 7).forEach { offset ->
                val dayOfWeek = firstDayOfWeek.plus(offset.toLong())
                Text(
                    text = dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = MoneyTheme.typography.small,
                    color = colors.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                )
            }
        }

        val firstOfMonth = month.atDay(1)
        val leadingBlanks = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = List(leadingBlanks) { null } +
                (1..month.lengthOfMonth()).map(month::atDay)

        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                week.forEach { day ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                    ) {
                        if (day != null) {
                            val isSelected = day == selected
                            val isToday = day == today
                            val isEnabled = !day.isAfter(today)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .alpha(if (isEnabled) 1f else 0.3f)
                                    .clip(MoneyShapes.circle)
                                    .background(
                                        when {
                                            isSelected -> colors.accent
                                            isToday -> colors.accentTint
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(
                                        enabled = isEnabled,
                                        onClick = { selected = day },
                                    )
                            ) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    style = MoneyTheme.typography.labelRegular,
                                    fontWeight =
                                        if (isSelected || isToday)
                                            FontWeight.Bold
                                        else
                                            FontWeight.Medium,
                                    color = when {
                                        isSelected -> colors.onAccent
                                        isToday -> colors.accent
                                        else -> colors.ink
                                    },
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            MoneyButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
                modifier = Modifier
                    .weight(1f)
            )
            MoneyButton(
                text = stringResource(R.string.common_done),
                style = MoneyButtonStyle.Filled,
                onClick = { onDatePicked(selected.toKotlinLocalDate()) },
                modifier = Modifier
                    .weight(1f)
            )
        }
    }
}
