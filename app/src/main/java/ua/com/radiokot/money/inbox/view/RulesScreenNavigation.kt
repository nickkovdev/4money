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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.composeunstyled.Text
import kotlinx.serialization.Serializable
import androidx.compose.runtime.collectAsState
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyDialog
import ua.com.radiokot.money.uikit.MoneyDialogContainer
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Serializable
object RulesScreenRoute

private sealed interface RuleDialog {
    val rule: PayeeRule

    class Actions(override val rule: PayeeRule) : RuleDialog
    class EditPattern(override val rule: PayeeRule) : RuleDialog
    class ConfirmDelete(override val rule: PayeeRule) : RuleDialog
}

/**
 * @param viewModel activity-level instance, as it also receives selection results
 */
fun NavGraphBuilder.rulesScreen(
    viewModel: RulesScreenViewModel,
    onProceedToCategorySelection: (isIncome: Boolean) -> Unit,
    onClose: () -> Unit,
) = composable<RulesScreenRoute> {

    var dialog by remember { mutableStateOf<RuleDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RulesScreenViewModel.Event.Close ->
                    onClose()

                is RulesScreenViewModel.Event.ProceedToRuleActions ->
                    dialog = RuleDialog.Actions(event.rule)

                is RulesScreenViewModel.Event.ProceedToCategorySelection ->
                    onProceedToCategorySelection(event.isIncome)
            }
        }
    }

    RulesScreen(
        viewModel = viewModel,
    )

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    val rangeDraft = viewModel.rangeDraft.collectAsState().value
    if (rangeDraft != null) {
        RangeEditorDialog(
            draft = rangeDraft,
            onFromChanged = viewModel::onDraftFromChanged,
            onUnderChanged = viewModel::onDraftUnderChanged,
            onTargetSelected = viewModel::onDraftTargetSelected,
            onPickCategoryClicked = viewModel::onDraftPickCategoryClicked,
            onSaveClicked = {
                // "Ask me" needs the app's own notifications.
                if (rangeDraft.target == ViewRangeTarget.Ask
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.onDraftSaveClicked()
            },
            onDeleteClicked = viewModel::onDraftDeleteClicked,
            onDismissRequest = viewModel::onDraftDismissed,
        )
    }

    when (val currentDialog = dialog) {
        is RuleDialog.Actions ->
            RuleActionsDialog(
                rule = currentDialog.rule,
                onEditPattern = { dialog = RuleDialog.EditPattern(currentDialog.rule) },
                onToggleMatchType = {
                    dialog = null
                    viewModel.onMatchTypeToggled(currentDialog.rule)
                },
                onDelete = { dialog = RuleDialog.ConfirmDelete(currentDialog.rule) },
                onDismissRequest = { dialog = null },
            )

        is RuleDialog.EditPattern ->
            PatternEditorDialog(
                rule = currentDialog.rule,
                onSave = { newPattern ->
                    dialog = null
                    viewModel.onPatternEdited(currentDialog.rule, newPattern)
                },
                onDismissRequest = { dialog = null },
            )

        is RuleDialog.ConfirmDelete ->
            MoneyDialog(
                title = "Delete the rules?",
                text = "All rules for “${currentDialog.rule.payeePattern}” are deleted, " +
                        "its payments will wait in the inbox again.",
                confirmText = "Delete",
                isDestructive = true,
                onConfirm = {
                    dialog = null
                    viewModel.onDeleteConfirmed(currentDialog.rule)
                },
                onDismissRequest = { dialog = null },
            )

        null ->
            Unit
    }
}

@Composable
private fun RuleActionsDialog(
    rule: PayeeRule,
    onEditPattern: () -> Unit,
    onToggleMatchType: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) = MoneyDialogContainer(
    onDismissRequest = onDismissRequest,
) {
    Column(
        modifier = Modifier
            .padding(20.dp)
    ) {
        Text(
            text = rule.payeePattern,
            style = MoneyTheme.typography.title,
            modifier = Modifier
                .padding(
                    start = 4.dp,
                    bottom = 14.dp,
                )
        )

        ListGroup {
            ListRow(
                title = "Edit pattern",
                leading = { IconTile(icon = R.drawable.ic_tabler_pencil) },
                onClick = onEditPattern,
            )
            ListDivider()
            ListRow(
                title =
                    if (rule.matchType == PayeeRule.MatchType.Exact)
                        "Match payees containing it"
                    else
                        "Match the exact payee only",
                leading = { IconTile(icon = R.drawable.ic_tabler_filter) },
                onClick = onToggleMatchType,
            )
            ListDivider()
            ListRow(
                title = "Delete",
                titleColor = MoneyTheme.colors.expense,
                leading = {
                    IconTile(
                        icon = R.drawable.ic_tabler_trash,
                        tint = MoneyTheme.colors.expense,
                        background = MoneyTheme.colors.expenseTint,
                    )
                },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun PatternEditorDialog(
    rule: PayeeRule,
    onSave: (String) -> Unit,
    onDismissRequest: () -> Unit,
) = MoneyDialogContainer(
    onDismissRequest = onDismissRequest,
) {
    var pattern by remember { mutableStateOf(rule.payeePattern) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(24.dp)
    ) {
        Text(
            text = "Payee pattern",
            style = MoneyTheme.typography.title,
        )

        MoneyTextField(
            value = pattern,
            onValueChange = { pattern = it },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            MoneyButton(
                text = "Cancel",
                onClick = onDismissRequest,
                modifier = Modifier
                    .weight(1f)
            )
            MoneyButton(
                text = "Save",
                style = MoneyButtonStyle.Filled,
                isEnabled = pattern.isNotBlank(),
                onClick = { onSave(pattern) },
                modifier = Modifier
                    .weight(1f)
            )
        }
    }
}
