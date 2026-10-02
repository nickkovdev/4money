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

package ua.com.radiokot.money.inbox.view

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.inbox.data.AmountRange
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.FieldLabel
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyChip
import ua.com.radiokot.money.uikit.MoneyDialogContainer
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.MoneyIconButtonStyle
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import java.math.BigDecimal

@Composable
private fun RulesScreen(
    groupList: State<List<ViewPayeeRuleGroup>>,
    onGroupMenuClicked: (ViewPayeeRuleGroup) -> Unit,
    onRowClicked: (ViewPayeeRuleGroup, ViewPayeeRuleRow) -> Unit,
    onAddRangeClicked: (ViewPayeeRuleGroup) -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .add(WindowInsets.statusBars)
        )
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 12.dp,
            )
    ) {
        MoneyIconButton(
            icon = R.drawable.ic_tabler_arrow_left,
            contentDescription = stringResource(R.string.common_back),
            onClick = onCloseClicked,
        )

        Text(
            text = stringResource(R.string.rules_title),
            style = MoneyTheme.typography.headline,
            modifier = Modifier
                .weight(1f)
        )
    }

    if (groupList.value.isEmpty()) {
        EmptyState(
            icon = R.drawable.ic_tabler_adjustments_horizontal,
            title = stringResource(R.string.rules_empty_title),
            text = stringResource(R.string.rules_empty_text),
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = MoneySpacing.screen,
            end = MoneySpacing.screen,
            bottom = 24.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.rules_hint),
                style = MoneyTheme.typography.caption,
                color = MoneyTheme.colors.ink2,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            )
        }

        items(
            items = groupList.value,
            key = ViewPayeeRuleGroup::key,
        ) { group ->
            RuleGroupCard(
                group = group,
                onMenuClicked = { onGroupMenuClicked(group) },
                onRowClicked = { row -> onRowClicked(group, row) },
                onAddRangeClicked = { onAddRangeClicked(group) },
            )
        }
    }
}

@Composable
private fun RuleGroupCard(
    group: ViewPayeeRuleGroup,
    onMenuClicked: () -> Unit,
    onRowClicked: (ViewPayeeRuleRow) -> Unit,
    onAddRangeClicked: () -> Unit,
) = ListGroup {
    val colors = MoneyTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MoneySpacing.rowHorizontal,
                end = 6.dp,
                top = 12.dp,
                bottom = 10.dp,
            )
    ) {
        if (group.colorScheme != null) {
            ItemLogo(
                title = group.displayPattern,
                colorScheme = group.colorScheme,
                icon = group.icon,
                modifier = Modifier
                    .size(42.dp)
            )
        } else {
            IconTile(
                icon = R.drawable.ic_tabler_receipt,
                size = 42.dp,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = group.displayPattern,
                style = MoneyTheme.typography.bodyStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.subtitle.resolve(),
                style = MoneyTheme.typography.caption,
                color = colors.ink3,
            )
        }

        MoneyIconButton(
            icon = R.drawable.ic_tabler_dots,
            contentDescription = stringResource(R.string.rules_actions_description),
            style = MoneyIconButtonStyle.Plain,
            size = 40.dp,
            onClick = onMenuClicked,
        )
    }

    group.rows.forEach { row ->
        ListDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { onRowClicked(row) })
                .padding(
                    horizontal = MoneySpacing.rowHorizontal,
                    vertical = 12.dp,
                )
        ) {
            Text(
                text = row.rangeText.resolve(),
                style = MoneyTheme.typography.label,
                maxLines = 1,
                modifier = Modifier
                    .width(108.dp)
            )

            Icon(
                painter = painterResource(R.drawable.ic_tabler_chevron_right),
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier
                    .size(16.dp)
            )

            if (row.isAsk) {
                IconTile(
                    icon = R.drawable.ic_tabler_message_question,
                    size = 30.dp,
                )
            } else if (row.colorScheme != null) {
                ItemLogo(
                    title = row.targetTitle.resolve(),
                    colorScheme = row.colorScheme,
                    icon = row.icon,
                    modifier = Modifier
                        .size(30.dp)
                )
            }

            Text(
                text = row.targetTitle.resolve(),
                style = MoneyTheme.typography.labelRegular,
                color =
                    if (row.isAsk)
                        colors.accent
                    else
                        colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
            )
        }
    }

    ListDivider()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddRangeClicked)
            .padding(
                horizontal = MoneySpacing.rowHorizontal,
                vertical = 12.dp,
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tabler_plus),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier
                .size(18.dp)
        )
        Text(
            text = stringResource(R.string.rules_add_range),
            style = MoneyTheme.typography.label,
            color = colors.accent,
        )
    }
}

/**
 * Edits one amount range of a payee: From (inclusive), Under (exclusive), the target.
 */
@Composable
fun RangeEditorDialog(
    draft: ViewRangeDraft,
    onFromChanged: (String) -> Unit,
    onUnderChanged: (String) -> Unit,
    onTargetSelected: (ViewRangeTarget) -> Unit,
    onPickCategoryClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onDismissRequest: () -> Unit,
) = MoneyDialogContainer(
    onDismissRequest = onDismissRequest,
) {
    RangeEditorContent(
        draft = draft,
        onFromChanged = onFromChanged,
        onUnderChanged = onUnderChanged,
        onTargetSelected = onTargetSelected,
        onPickCategoryClicked = onPickCategoryClicked,
        onSaveClicked = onSaveClicked,
        onDeleteClicked = onDeleteClicked,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun RangeEditorContent(
    draft: ViewRangeDraft,
    onFromChanged: (String) -> Unit,
    onUnderChanged: (String) -> Unit,
    onTargetSelected: (ViewRangeTarget) -> Unit,
    onPickCategoryClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onDismissRequest: () -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
        .padding(24.dp)
) {
    val colors = MoneyTheme.colors
    val currencySuffix = draft.currencyCode
        ?.let { code ->
            runCatching { java.util.Currency.getInstance(code.uppercase()).symbol }.getOrDefault(code)
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (draft.ruleId == null)
                        R.string.rules_range_new
                    else
                        R.string.rules_range_title
                ),
            style = MoneyTheme.typography.title,
        )
        Text(
            text = draft.displayPattern,
            style = MoneyTheme.typography.caption,
            color = colors.ink3,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple(stringResource(R.string.rules_range_from), draft.fromText, onFromChanged),
            Triple(stringResource(R.string.rules_range_under), draft.underText, onUnderChanged),
        ).forEach { (label, value, onChanged) ->
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                FieldLabel(text = label)
                MoneyTextField(
                    value = value,
                    onValueChange = onChanged,
                    placeholder = stringResource(R.string.rules_range_any),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    trailing =
                        if (currencySuffix != null) {
                            {
                                Text(
                                    text = currencySuffix,
                                    style = MoneyTheme.typography.caption,
                                    color = colors.ink3,
                                )
                            }
                        } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.rules_then),
            style = MoneyTheme.typography.overline,
            color = colors.ink3,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoneyChip(
                text = stringResource(R.string.rules_ask_me),
                icon = R.drawable.ic_tabler_message_question,
                isSelected = draft.target == ViewRangeTarget.Ask,
                onClick = { onTargetSelected(ViewRangeTarget.Ask) },
            )
            draft.categoryOptions.forEach { option ->
                MoneyChip(
                    text = option.title,
                    isSelected = draft.target == option,
                    onClick = { onTargetSelected(option) },
                )
            }
            MoneyChip(
                text = stringResource(R.string.rules_other_category),
                icon = R.drawable.ic_tabler_layout_grid,
                isMuted = true,
                onClick = onPickCategoryClicked,
            )
        }
    }

    if (draft.error != null) {
        Text(
            text = draft.error.resolve(),
            style = MoneyTheme.typography.caption,
            color = colors.expense,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        if (draft.ruleId != null) {
            MoneyIconButton(
                icon = R.drawable.ic_tabler_trash,
                contentDescription = stringResource(R.string.rules_delete_range),
                tint = colors.expense,
                size = 48.dp,
                onClick = onDeleteClicked,
            )
        }
        MoneyButton(
            text = stringResource(R.string.common_cancel),
            onClick = onDismissRequest,
            modifier = Modifier
                .weight(1f)
        )
        MoneyButton(
            text = stringResource(R.string.common_save),
            style = MoneyButtonStyle.Filled,
            onClick = onSaveClicked,
            modifier = Modifier
                .weight(1f)
        )
    }
}

@Composable
fun RulesScreen(
    viewModel: RulesScreenViewModel,
) = RulesScreen(
    groupList = viewModel.groupList.collectAsState(),
    onGroupMenuClicked = remember { viewModel::onGroupMenuClicked },
    onRowClicked = remember { viewModel::onRowClicked },
    onAddRangeClicked = remember { viewModel::onAddRangeClicked },
    onCloseClicked = remember { viewModel::onCloseClicked },
)

@Preview(
    apiLevel = 34,
    heightDp = 760,
)
@Composable
private fun RulesScreenPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    val schemes = HardcodedItemColorSchemeRepository().getItemColorSchemesByName()
    fun rule(id: String, range: AmountRange?, action: PayeeRule.Action = PayeeRule.Action.Record) = PayeeRule(
        payeePattern = "fuelstop",
        matchType = PayeeRule.MatchType.Exact,
        categoryId = "c",
        subcategoryId = null,
        accountId = null,
        hits = 3,
        lastUsedAt = null,
        id = id,
        amountRange = range,
        action = action,
    )

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(MoneyTheme.colors.background)
    ) {
        RulesScreen(
            groupList = listOf(
                ViewPayeeRuleGroup(
                    key = "1",
                    displayPattern = "Fuelstop",
                    matchType = PayeeRule.MatchType.Exact,
                    subtitle = ViewText.Plain("Exact payee · 3 rules · used 14×"),
                    rows = listOf(
                        ViewPayeeRuleRow(
                            ViewText.Plain("Under 10 €"), ViewText.Plain("Food"), false,
                            schemes.getValue("Orange3"), null, rule("a", AmountRange(null, max = BigDecimal("10"))),
                        ),
                        ViewPayeeRuleRow(
                            ViewText.Plain("10–35 €"), ViewText.Plain("Ask me"), true, null, null,
                            rule("b", AmountRange(BigDecimal("10"), max = BigDecimal("35")), PayeeRule.Action.Ask),
                        ),
                        ViewPayeeRuleRow(
                            ViewText.Plain("From 35 €"), ViewText.Plain("Car"), false,
                            schemes.getValue("Blue3"), null, rule("c", AmountRange(BigDecimal("35"), max = null)),
                        ),
                    ),
                    colorScheme = schemes.getValue("Blue3"),
                    icon = null,
                    anyRule = rule("a", null),
                    currencyCode = "EUR",
                ),
            ).let(::mutableStateOf),
            onGroupMenuClicked = {},
            onRowClicked = { _, _ -> },
            onAddRangeClicked = {},
            onCloseClicked = {},
        )
    }
}
