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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun TestTextScreen(
    viewModel: TestTextScreenViewModel,
) = TestTextScreen(
    title = viewModel.title.collectAsState(),
    text = viewModel.text.collectAsState(),
    outcome = viewModel.outcome.collectAsState(),
    onTitleChanged = viewModel::onTitleChanged,
    onTextChanged = viewModel::onTextChanged,
    onBackClicked = viewModel::onBackClicked,
)

@Composable
private fun TestTextScreen(
    title: State<String>,
    text: State<String>,
    outcome: State<TestTextRunner.Outcome>,
    onTitleChanged: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onBackClicked: () -> Unit,
) = Column {

    ScreenTopBar(
        title = stringResource(R.string.sources_test_title),
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
        MoneyTextField(
            value = title.value,
            onValueChange = onTitleChanged,
            placeholder = stringResource(R.string.test_title_placeholder),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        MoneyTextField(
            value = text.value,
            onValueChange = onTextChanged,
            placeholder = stringResource(R.string.test_text_placeholder),
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp)
        )

        val currentOutcome = outcome.value
        when {
            currentOutcome.isEmpty ->
                EmptyState(
                    icon = R.drawable.ic_tabler_notes,
                    title = stringResource(R.string.test_empty_title),
                    text = stringResource(R.string.test_empty_text),
                )

            else ->
                Results(
                    outcome = currentOutcome,
                )
        }
    }
}

@Composable
private fun Results(
    outcome: TestTextRunner.Outcome,
) = Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    if (outcome.matches.isEmpty()) {
        Text(
            text = stringResource(R.string.test_nothing_matched),
            style = MoneyTheme.typography.body,
            color = MoneyTheme.colors.ink2,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        )
    } else {
        ListGroup {
            outcome.matches.forEachIndexed { index, match ->
                if (index > 0) {
                    ListDivider()
                }
                MatchRow(match)
            }
        }
    }

    if (outcome.noMatch.isNotEmpty()) {
        NoMatchGroup(outcome.noMatch)
    }
}

@Composable
private fun MatchRow(
    match: TestTextRunner.Match,
) {
    val payment = match.payment
    val isPrivate = LocalPrivacyMode.current
    val colors = MoneyTheme.colors

    // The amount is private, the currency is not.
    val amountText =
        (if (payment.isIncoming) "+" else "−") +
                (if (isPrivate) PrivacyAmounts.MASK else payment.amount.toPlainString()) +
                " " + payment.currencyCode

    val details = listOf(
        "${match.sourceLabel} · ${match.kind.resolve()}",
        listOf(
            payment.cardLast4
                ?.let { stringResource(R.string.test_card, it) }
                ?: stringResource(R.string.test_no_card),
            stringResource(
                if (payment.isIncoming)
                    R.string.test_incoming
                else
                    R.string.test_outgoing
            ),
        ).joinToString(" · "),
    ).joinToString("\n")

    ListRow(
        title = payment.payee,
        subtitle = details,
        trailing = {
            Text(
                text = amountText,
                style = MoneyTheme.typography.bodyStrong,
                color =
                    if (payment.isIncoming)
                        colors.income
                    else
                        colors.expense,
            )
        },
    )
}

@Composable
private fun NoMatchGroup(
    noMatch: List<TestTextRunner.NoMatch>,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    ListGroup {
        ListRow(
            title = stringResource(R.string.test_no_match, noMatch.size),
            titleColor = MoneyTheme.colors.ink2,
            trailing = { RowChevron() },
            onClick = { isExpanded = !isExpanded },
        )

        if (isExpanded) {
            noMatch.forEach { item ->
                ListDivider()
                ListRow(
                    title = item.sourceLabel,
                    subtitle = item.kind.resolve(),
                )
            }
        }
    }
}
