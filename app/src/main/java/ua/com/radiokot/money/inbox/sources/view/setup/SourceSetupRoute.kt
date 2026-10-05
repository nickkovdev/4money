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

import android.content.ActivityNotFoundException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.money.inbox.listener.NotificationAccess

/**
 * @param packageName the source to teach (step 3), null to add a new app (step 1)
 */
@Serializable
data class SourceSetupRoute(
    val packageName: String? = null,
)

private val log = KotlinLogging.logger("SourceSetupScreen")

/**
 * The wizard view model of the [entry], to pass the account selection result to.
 * The entry must be on the back stack, i.e. its screen has been shown.
 */
fun sourceSetupViewModelOf(entry: NavBackStackEntry): SourceSetupViewModel =
    ViewModelProvider(entry)[SourceSetupViewModel::class.java]

/**
 * @param onProceedToAccountSelection open the account selection sheet,
 * pass its result to [SourceSetupViewModel.onCounterpartySelected] of [sourceSetupViewModelOf]
 */
fun NavGraphBuilder.sourceSetupScreen(
    onProceedToAccountSelection: () -> Unit,
    onClose: () -> Unit,
) = composable<SourceSetupRoute> { entry ->

    val route = entry.toRoute<SourceSetupRoute>()
    val viewModel: SourceSetupViewModel = koinViewModel(
        parameters = { parametersOf(route.packageName) },
    )
    val context = LocalContext.current

    // The user may come back from the system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed(NotificationAccess.isGranted(context))
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SourceSetupViewModel.Event.ProceedToAccessSettings ->
                    try {
                        context.startActivity(NotificationAccess.getSettingsIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        try {
                            context.startActivity(NotificationAccess.getFallbackSettingsIntent())
                        } catch (e: ActivityNotFoundException) {
                            log.error(e) {
                                "No notification access settings screen"
                            }
                        }
                    }

                SourceSetupViewModel.Event.ProceedToAccountSelection ->
                    onProceedToAccountSelection()

                SourceSetupViewModel.Event.Close ->
                    onClose()
            }
        }
    }

    SourceSetupScreen(
        viewModel = viewModel,
    )
}
