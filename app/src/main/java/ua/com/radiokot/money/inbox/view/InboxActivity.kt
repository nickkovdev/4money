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

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.MoneyAppActivity
import ua.com.radiokot.money.MoneyAppModalBottomSheetHost
import ua.com.radiokot.money.auth.logic.UserSessionScope
import ua.com.radiokot.money.rememberMoneyAppNavController
import ua.com.radiokot.money.routeIs
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionSheetRoute
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import ua.com.radiokot.money.transfers.view.TransfersNavigator
import ua.com.radiokot.money.transfers.view.transferCounterpartySelectionSheet
import ua.com.radiokot.money.transfers.view.transferSheet
import ua.com.radiokot.money.theme.view.MoneyAppTheme

class InboxActivity : MoneyAppActivity(
    requiresUnlocking = true,
    requiresSession = true,
) {

    override fun onCreateAllowed(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        setContent {
            MoneyAppTheme {
                UserSessionScope {
                    Content(
                        finishActivity = ::finish,
                    )
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun Content(
    finishActivity: () -> Unit,
) {
    val navController = rememberMoneyAppNavController()
    val transfersNavigatorFactory = koinInject<TransfersNavigator.Factory>()
    val transfersNavigator = remember(transfersNavigatorFactory, navController) {
        transfersNavigatorFactory.create(
            isIncognito = false,
            navController = navController,
        )
    }
    val inboxViewModel: InboxScreenViewModel = koinViewModel()

    NavHost(
        navController = navController,
        startDestination = InboxScreenRoute,
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) },
        modifier = Modifier
            .fillMaxSize(),
    ) {

        inboxScreen(
            viewModel = inboxViewModel,
            onProceedToCategorySelection = { accountId, isIncome ->
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        // An income category is the source of an income.
                        isForSource = isIncome,
                        alreadySelectedCounterpartyId = accountId,
                        showAccounts = false,
                        showCategories = true,
                    ),
                )
            },
            onProceedToAccountSelection = {
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        isForSource = true,
                        alreadySelectedCounterpartyId = null,
                        showAccounts = true,
                        showCategories = false,
                    ),
                )
            },
            onProceedToTransfer = { route ->
                navController.navigate(route)
            },
            onProceedToRules = {
                navController.navigate(RulesScreenRoute)
            },
            onClose = finishActivity,
        )

        rulesScreen(
            onClose = navController::navigateUp,
        )

        transferCounterpartySelectionSheet(
            onSelected = { result ->
                // Selection requested by the transfer sheet itself (changing the account/category).
                if (navController.previousBackStackEntry?.destination?.routeIs<TransferSheetRoute>() == true) {
                    transfersNavigator.proceedToTransfer(result)
                } else {
                    navController.navigateUp()
                    inboxViewModel.onCounterpartySelected(result)
                }
            },
        )

        transferSheet(
            onProceedToTransferCounterpartySelection = {
                    alreadySelectedCounterpartyId: TransferCounterpartyId,
                    selectSource: Boolean,
                    showCategories: Boolean,
                    showAccounts: Boolean,
                ->
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        isForSource = selectSource,
                        alreadySelectedCounterpartyId = alreadySelectedCounterpartyId,
                        showCategories = showCategories,
                        showAccounts = showAccounts,
                    ),
                )
            },
            onTransferDone = navController::navigateUp,
        )
    }

    MoneyAppModalBottomSheetHost(
        moneyAppNavController = navController,
    )
}
