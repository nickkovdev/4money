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

package ua.com.radiokot.money.transfers.history.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ua.com.radiokot.money.uikit.MoneyDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.money.home.view.HomeViewModel
import ua.com.radiokot.money.transfers.data.Transfer

const val ActivityScreenRoute = "activity"

fun NavGraphBuilder.activityScreen(
    homeViewModel: HomeViewModel,
    onProceedToEditingTransfer: (transferToEdit: Transfer) -> Unit,
) = composable(ActivityScreenRoute) {

    var transferToRevertId by rememberSaveable { mutableStateOf<String?>(null) }
    val viewModel: ActivityViewModel = koinViewModel {
        parametersOf(
            homeViewModel,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ActivityViewModel.Event.ProceedToEditingTransfer -> {
                    onProceedToEditingTransfer(event.transferToEdit)
                }

                is ActivityViewModel.Event.ProceedToRevertingTransferConfirmation -> {
                    transferToRevertId = event.transferToRevertId
                }
            }
        }
    }

    ActivityScreenRoot(
        viewModel = viewModel,
        modifier = Modifier
            .fillMaxSize()
    )

    transferToRevertId?.also { idToRevert ->
        MoneyDialog(
            title = "Revert this transaction?",
            text = "It will be removed, and the amount goes back " +
                    "to the balances of the accounts it touched.",
            confirmText = "Revert",
            isDestructive = true,
            onConfirm = {
                transferToRevertId = null
                viewModel.onTransferRevertConfirmed(
                    transferToRevertId = idToRevert,
                )
            },
            onDismissRequest = {
                transferToRevertId = null
            },
        )
    }
}
