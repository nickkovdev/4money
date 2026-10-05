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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.MoneyAppActivity
import ua.com.radiokot.money.MoneyAppModalBottomSheetHost
import ua.com.radiokot.money.auth.logic.UserSessionScope
import ua.com.radiokot.money.inbox.sources.view.setup.SourceSetupRoute
import ua.com.radiokot.money.inbox.sources.view.setup.sourceSetupScreen
import ua.com.radiokot.money.inbox.sources.view.setup.sourceSetupViewModelOf
import ua.com.radiokot.money.inbox.view.RulesScreenRoute
import ua.com.radiokot.money.inbox.view.RulesScreenViewModel
import ua.com.radiokot.money.inbox.view.rulesScreen
import ua.com.radiokot.money.rememberMoneyAppNavController
import ua.com.radiokot.money.routeIs
import ua.com.radiokot.money.theme.view.MoneyAppTheme
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionSheetRoute
import ua.com.radiokot.money.transfers.view.transferCounterpartySelectionSheet

/**
 * Auto-booking: the notification sources, their setup, the card accounts,
 * the payee rules and the text tester.
 */
class AutoBookActivity : MoneyAppActivity(
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

    companion object {
        fun getIntent(context: Context): Intent =
            Intent(context, AutoBookActivity::class.java)
    }
}

@Composable
private fun Content(
    finishActivity: () -> Unit,
) {
    val navController = rememberMoneyAppNavController()
    val sourcesViewModel: SourcesScreenViewModel = koinViewModel()
    val testTextViewModel: TestTextScreenViewModel = koinViewModel()
    val cardAccountsViewModel: CardAccountsScreenViewModel = koinViewModel()
    val rulesViewModel: RulesScreenViewModel = koinViewModel()

    NavHost(
        navController = navController,
        startDestination = SourcesScreenRoute,
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .add(WindowInsets.statusBars)
            )
            .displayCutoutPadding(),
    ) {

        sourcesScreen(
            viewModel = sourcesViewModel,
            onProceedToWizard = { packageName ->
                navController.navigate(SourceSetupRoute(packageName))
            },
            onProceedToCards = { navController.navigate(CardAccountsScreenRoute) },
            onProceedToRules = { navController.navigate(RulesScreenRoute) },
            onProceedToTestText = { navController.navigate(TestTextScreenRoute) },
            onClose = finishActivity,
        )

        sourceSetupScreen(
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
            onClose = navController::navigateUp,
        )

        testTextScreen(
            viewModel = testTextViewModel,
            onClose = navController::navigateUp,
        )

        cardAccountsScreen(
            viewModel = cardAccountsViewModel,
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
            onClose = navController::navigateUp,
        )

        rulesScreen(
            viewModel = rulesViewModel,
            onProceedToCategorySelection = { isIncome ->
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        isForSource = isIncome,
                        alreadySelectedCounterpartyId = null,
                        showAccounts = false,
                        showCategories = true,
                    ),
                )
            },
            onClose = navController::navigateUp,
        )

        transferCounterpartySelectionSheet(
            onSelected = { result ->
                val previousEntry = navController.previousBackStackEntry
                val previousDestination = previousEntry?.destination
                navController.navigateUp()

                when {
                    previousEntry != null && previousDestination?.routeIs<SourceSetupRoute>() == true ->
                        sourceSetupViewModelOf(previousEntry).onCounterpartySelected(result)

                    previousDestination?.routeIs<RulesScreenRoute>() == true ->
                        rulesViewModel.onCounterpartySelected(result)

                    previousDestination?.routeIs<CardAccountsScreenRoute>() == true ->
                        cardAccountsViewModel.onCounterpartySelected(result)
                }
            },
        )
    }

    MoneyAppModalBottomSheetHost(
        moneyAppNavController = navController,
    )
}
