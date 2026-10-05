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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.currency.view.formatOrPrivate
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.inbox.sources.logic.TeachDraft
import ua.com.radiokot.money.inbox.sources.view.AppIcon
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.logic.SampleToken
import ua.com.radiokot.money.inbox.templates.logic.TemplateBuilder
import ua.com.radiokot.money.inbox.templates.logic.TokenRole
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyChip
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.SegmentedControl
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * Step 4: tap the words of the sample to say what they are.
 * The sample and its words are shown as they are, the user teaches from them;
 * the extracted amounts are masked while the privacy mode is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceSetupTeachStep(
    packageName: String?,
    appLabel: State<String?>,
    draft: TeachDraft,
    onTokenTapped: (index: Int) -> Unit,
    onRoleChosen: (index: Int, role: TokenRole?) -> Unit,
    onDirectionChosen: (NotificationTemplate.Direction) -> Unit,
    onCheckClicked: () -> Unit,
) {
    val analysis = remember(draft.tokens, draft.marks, draft.direction) {
        draft.analyze()
    }
    val isBuilt = analysis.result is TemplateBuilder.Result.Built

    SetupStepLayout(
        footer = {
            MoneyButton(
                text = stringResource(R.string.setup_teach_check),
                style = MoneyButtonStyle.Filled,
                isEnabled = isBuilt,
                onClick = onCheckClicked,
                modifier = Modifier
                    .fillMaxWidth()
            )
        },
        body = {
            Text(
                text = stringResource(R.string.setup_teach_text),
                style = MoneyTheme.typography.body,
                color = MoneyTheme.colors.ink2,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MoneyShapes.medium)
                    .background(MoneyTheme.colors.surface)
                    .padding(16.dp)
            ) {
                if (packageName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val label = appLabel.value ?: packageName

                        AppIcon(
                            packageName = packageName,
                            label = label,
                            size = 22.dp,
                        )
                        Text(
                            text = label,
                            style = MoneyTheme.typography.caption,
                            color = MoneyTheme.colors.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    draft.tokens.forEach { token ->
                        TokenChip(
                            token = token,
                            role = draft.marks[token.index],
                            isSelected = token.index == draft.selectedTokenIndex,
                            onClick = { onTokenTapped(token.index) },
                        )
                    }
                }
            }

            val selected = draft.tokens.getOrNull(draft.selectedTokenIndex ?: -1)
            if (selected != null) {
                RoleChooser(
                    token = selected,
                    currentRole = draft.marks[selected.index],
                    canBeCard = draft.canBeCard(selected.index),
                    onRoleChosen = { role -> onRoleChosen(selected.index, role) },
                )
            }

            Legend(draft = draft)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_teach_direction),
                    style = MoneyTheme.typography.overline,
                    color = MoneyTheme.colors.ink3,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                )

                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.setup_teach_expense),
                        stringResource(R.string.setup_teach_income),
                    ),
                    selectedIndex =
                        if (draft.direction == NotificationTemplate.Direction.Outgoing)
                            0
                        else
                            1,
                    onSelected = { index ->
                        onDirectionChosen(
                            if (index == 0)
                                NotificationTemplate.Direction.Outgoing
                            else
                                NotificationTemplate.Direction.Incoming
                        )
                    },
                    isFillWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            val payment = analysis.payment
            if (payment != null) {
                Preview(payment = payment)
            }

            val problem = (analysis.result as? TemplateBuilder.Result.Invalid)?.problem
            if (problem != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_alert_triangle),
                        contentDescription = null,
                        tint = MoneyTheme.colors.warning,
                        modifier = Modifier
                            .size(18.dp)
                    )
                    Text(
                        text = stringResource(problem.hintRes()),
                        style = MoneyTheme.typography.caption,
                        color = MoneyTheme.colors.ink2,
                    )
                }
            }
        },
    )
}

@Composable
private fun TokenChip(
    token: SampleToken,
    role: TokenRole?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val style = role?.style()
    val description =
        if (role != null)
            stringResource(R.string.setup_token_marked, token.text, stringResource(role.labelRes()))
        else
            token.text
    val shape = MoneyShapes.small

    Text(
        text = token.text,
        style =
            if (role != null)
                MoneyTheme.typography.label
            else
                MoneyTheme.typography.labelRegular,
        color = style?.content ?: MoneyTheme.colors.ink,
        modifier = Modifier
            .clip(shape)
            .background(style?.background ?: MoneyTheme.colors.surface2)
            .border(
                width = 2.dp,
                color =
                    if (isSelected)
                        MoneyTheme.colors.accent
                    else
                        Color.Transparent,
                shape = shape,
            )
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleChooser(
    token: SampleToken,
    currentRole: TokenRole?,
    canBeCard: Boolean,
    onRoleChosen: (TokenRole?) -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        text = stringResource(R.string.setup_role_chooser_description, token.text),
        style = MoneyTheme.typography.caption,
        color = MoneyTheme.colors.ink3,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 4.dp)
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TokenRole.entries
            .filter { it != TokenRole.Card || canBeCard }
            .forEach { role ->
                MoneyChip(
                    text = stringResource(role.labelRes()),
                    isSelected = role == currentRole,
                    onClick = { onRoleChosen(role) },
                )
            }

        MoneyChip(
            text = stringResource(R.string.setup_role_clear),
            icon = R.drawable.ic_tabler_x,
            isMuted = true,
            onClick = { onRoleChosen(null) },
        )
    }
}

/**
 * What was found: the amount (masked while private), the currency, the payee, the card.
 */
@Composable
private fun Legend(
    draft: TeachDraft,
) {
    val isPrivate = LocalPrivacyMode.current
    val notMarked = stringResource(R.string.setup_teach_not_marked)

    fun valueOf(role: TokenRole): String? =
        draft.markedTexts(role)
            .takeIf(List<String>::isNotEmpty)
            ?.let { texts ->
                when (role) {
                    TokenRole.Amount ->
                        if (isPrivate) PrivacyAmounts.MASK else texts.joinToString(" ")

                    TokenRole.Card ->
                        "…" + texts.joinToString(" ") { it.takeLast(4) }

                    else ->
                        texts.joinToString(" ")
                }
            }

    ListGroup {
        listOf(
            TokenRole.Amount,
            TokenRole.Currency,
            TokenRole.Payee,
            TokenRole.Card,
        ).forEachIndexed { index, role ->
            if (index > 0) {
                ListDivider()
            }

            val value = valueOf(role)
            val style = role.style()

            ListRow(
                title = stringResource(role.labelRes()),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(MoneyShapes.circle)
                            .background(style.content)
                    )
                },
                trailing = {
                    Text(
                        text = value
                            ?: if (role == TokenRole.Card)
                                stringResource(R.string.setup_teach_optional)
                            else
                                notMarked,
                        style = MoneyTheme.typography.label,
                        color =
                            if (value != null)
                                MoneyTheme.colors.ink
                            else
                                MoneyTheme.colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                    )
                },
            )
        }
    }
}

/**
 * How the payment will look in the app, computed by the template on the sample.
 */
@Composable
private fun Preview(
    payment: ParsedBankNotification.Payment,
) {
    val amountFormat = rememberViewAmountFormat()
    val amountColor =
        if (payment.isIncoming)
            MoneyTheme.colors.income
        else
            MoneyTheme.colors.expense

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoneyShapes.large)
            .background(MoneyTheme.colors.accentTint)
            .padding(16.dp)
    ) {
        IconTile(
            icon = R.drawable.ic_tabler_receipt,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = PayeeNormalizer.displayName(payment.payee),
                style = MoneyTheme.typography.bodyStrong,
                color = MoneyTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (payment.cardLast4 != null)
                        stringResource(R.string.setup_teach_preview_card, payment.cardLast4)
                    else
                        stringResource(R.string.setup_teach_preview),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = amountFormat
                .formatOrPrivate(viewAmountOfPayment(payment), customColor = amountColor)
                .text,
            style = MoneyTheme.typography.bodyStrong,
            color = amountColor,
            maxLines = 1,
        )
    }
}
