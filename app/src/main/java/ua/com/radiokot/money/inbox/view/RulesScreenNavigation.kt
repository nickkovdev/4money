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

import android.app.Activity
import android.widget.EditText
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.inbox.data.PayeeRule

@Serializable
object RulesScreenRoute

fun NavGraphBuilder.rulesScreen(
    onClose: () -> Unit,
) = composable<RulesScreenRoute> {

    val activity: Activity? = LocalActivity.current
    val viewModel: RulesScreenViewModel = koinViewModel()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RulesScreenViewModel.Event.Close ->
                    onClose()

                is RulesScreenViewModel.Event.ProceedToRuleActions ->
                    showRuleActions(
                        activity = checkNotNull(activity) {
                            "The screen must have an activity to proceed"
                        },
                        rule = event.rule,
                        viewModel = viewModel,
                    )
            }
        }
    }

    RulesScreen(
        viewModel = viewModel,
    )
}

private fun showRuleActions(
    activity: Activity,
    rule: PayeeRule,
    viewModel: RulesScreenViewModel,
) {
    val toggleMatchTypeTitle =
        if (rule.matchType == PayeeRule.MatchType.Exact)
            "Match payees containing it"
        else
            "Match the exact payee only"

    AlertDialog.Builder(activity)
        .setTitle(rule.payeePattern)
        .setItems(arrayOf("Edit pattern", toggleMatchTypeTitle, "Delete")) { _, which ->
            when (which) {
                0 -> showPatternEditor(activity, rule, viewModel)
                1 -> viewModel.onMatchTypeToggled(rule)
                2 -> AlertDialog.Builder(activity)
                    .setMessage("Delete the rule for “${rule.payeePattern}”?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.onDeleteConfirmed(rule) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        .show()
}

private fun showPatternEditor(
    activity: Activity,
    rule: PayeeRule,
    viewModel: RulesScreenViewModel,
) {
    val input = EditText(activity).apply {
        setText(rule.payeePattern)
        setSingleLine()
        setSelection(text.length)
    }

    AlertDialog.Builder(activity)
        .setTitle("Payee pattern")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            viewModel.onPatternEdited(rule, input.text.toString())
        }
        .setNegativeButton("Cancel", null)
        .show()
}
