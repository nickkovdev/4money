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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import ua.com.radiokot.money.MoneyAppActivity
import ua.com.radiokot.money.MoneyAppModalBottomSheetHost
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.auth.logic.UserSessionScope
import ua.com.radiokot.money.auth.view.AuthActivity
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource
import ua.com.radiokot.money.rememberMoneyAppNavController
import ua.com.radiokot.money.theme.view.MoneyAppTheme
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.logic.GetLastUsedAccountsByCategoryUseCase

/**
 * Quick transfer entry started from the home screen widget:
 * a category selection followed by the transfer sheet, over the launcher.
 *
 * @see QuickTransferDirection
 */
class QuickTransferActivity : MoneyAppActivity(
    requiresSession = true,
    requiresUnlocking = true,
) {

    override fun onCreateAllowed(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        // Keep the status bar icons light over the dim scrim in light themes.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val direction = QuickTransferDirection.fromAction(intent.action)

        setContent {
            MoneyAppTheme(paintWindow = false) {
                UserSessionScope {
                    QuickTransferScreen(
                        direction = direction,
                        finishActivity = ::finish,
                    )
                }
            }
        }
    }

    // The quick entry lives in its own task: open the sign-in in the app task.
    override fun goToAuth() {
        startActivity(
            Intent(this, AuthActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        fun intent(
            context: Context,
            direction: QuickTransferDirection,
        ): Intent =
            Intent(context, QuickTransferActivity::class.java)
                .setAction(direction.action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun QuickTransferScreen(
    direction: QuickTransferDirection,
    finishActivity: () -> Unit,
) {
    val navController = rememberMoneyAppNavController()
    val getLastUsedAccountsByCategoryUseCase = koinInject<GetLastUsedAccountsByCategoryUseCase>()
    val mostUsedAccountSource = koinInject<MostUsedAccountSource>()
    val accountRepository = koinInject<AccountRepository>()
    val transfersNavigator = remember(navController) {
        TransfersNavigator(
            getLastUsedAccountsByCategoryUseCase = getLastUsedAccountsByCategoryUseCase,
            isIncognito = false,
            navController = navController,
            fallbackAccount = {
                mostUsedAccountSource.getMostUsedAccountId()
                    ?.let { accountRepository.getAccount(it) }
            },
        )
    }

    LaunchedEffect(navController) {
        navController.currentBackStack.collectLatest { backStack ->
            if (backStack.isEmpty()) {
                finishActivity()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = TransferCounterpartySelectionSheetRoute(
            isIncognito = false,
            // An income category is the source of an income.
            isForSource = direction == QuickTransferDirection.Income,
            showAccounts = false,
            showCategories = true,
            alreadySelectedCounterpartyId = null,
        ),
        modifier = Modifier
            .fillMaxSize()
    ) {
        transferCounterpartySelectionSheet(
            onSelected = transfersNavigator::proceedToTransfer,
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
                        isIncognito = false,
                    )
                )
            },
            onTransferDone = finishActivity,
        )
    }

    MoneyAppModalBottomSheetHost(
        moneyAppNavController = navController,
    )
}
