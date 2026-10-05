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

import android.content.ActivityNotFoundException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import ua.com.radiokot.money.inbox.listener.NotificationAccess

@Serializable
object SourcesScreenRoute

private val log = KotlinLogging.logger("SourcesScreen")

/**
 * @param viewModel activity-level instance
 * @param onProceedToWizard the package to teach, null to add a new app
 */
fun NavGraphBuilder.sourcesScreen(
    viewModel: SourcesScreenViewModel,
    onProceedToWizard: (packageName: String?) -> Unit,
    onProceedToCards: () -> Unit,
    onProceedToRules: () -> Unit,
    onProceedToTestText: () -> Unit,
    onClose: () -> Unit,
) = composable<SourcesScreenRoute> {

    val context = LocalContext.current

    // The user may come back from the system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed(NotificationAccess.isGranted(context))
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SourcesScreenViewModel.Event.ProceedToAccessSettings ->
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

                is SourcesScreenViewModel.Event.ProceedToWizard ->
                    onProceedToWizard(event.packageName)

                SourcesScreenViewModel.Event.ProceedToCards ->
                    onProceedToCards()

                SourcesScreenViewModel.Event.ProceedToRules ->
                    onProceedToRules()

                SourcesScreenViewModel.Event.ProceedToTestText ->
                    onProceedToTestText()

                SourcesScreenViewModel.Event.Close ->
                    onClose()
            }
        }
    }

    SourcesScreen(
        viewModel = viewModel,
    )
}
