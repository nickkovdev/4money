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

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ua.com.radiokot.money.R
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import ua.com.radiokot.money.uikit.failureText
import ua.com.radiokot.money.uikit.resolve

const val InboxTabRoute = "inbox"

/**
 * The centre tab of the home screen.
 *
 * @param viewModel activity-level instance, as it also receives selection results
 */
fun NavGraphBuilder.inboxTab(
    viewModel: InboxScreenViewModel,
    onProceedToCategorySelection: (accountId: TransferCounterpartyId.Account, isIncome: Boolean) -> Unit,
    onProceedToTransfer: (TransferSheetRoute) -> Unit,
    onProceedToRules: () -> Unit,
    onProceedToCards: () -> Unit,
) = composable(
    route = InboxTabRoute,
) {

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxScreenViewModel.Event.ProceedToCategorySelection ->
                    onProceedToCategorySelection(event.accountId, event.isIncome)

                is InboxScreenViewModel.Event.ProceedToTransfer ->
                    onProceedToTransfer(event.route)

                InboxScreenViewModel.Event.ProceedToCards ->
                    onProceedToCards()

                InboxScreenViewModel.Event.ProceedToRules ->
                    onProceedToRules()

                is InboxScreenViewModel.Event.ShowError ->
                    Toast
                        .makeText(context, event.text.resolve(context), Toast.LENGTH_LONG)
                        .show()

                is InboxScreenViewModel.Event.ShowUndoError ->
                    Toast
                        .makeText(
                            context,
                            failureText(
                                withReasonId = R.string.inbox_undo_failed,
                                withoutReasonId = R.string.inbox_undo_failed_no_reason,
                                reason = event.technicalReason,
                            ).resolve(context),
                            Toast.LENGTH_LONG,
                        )
                        .show()
            }
        }
    }

    InboxTabScreen(
        viewModel = viewModel,
    )
}
