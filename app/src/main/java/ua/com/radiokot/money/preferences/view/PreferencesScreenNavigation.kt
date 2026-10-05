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

package ua.com.radiokot.money.preferences.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ua.com.radiokot.money.uikit.MoneyDialog
import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.inbox.listener.NotificationAccess
import androidx.compose.ui.res.stringResource
import ua.com.radiokot.money.R

const val PreferencesScreenRoute = "preferences"

private val log = KotlinLogging.logger("PreferencesScreen")

fun NavGraphBuilder.preferencesScreen(
    onBack: () -> Unit,
    onProceedToPasscodeSetup: () -> Unit,
    onSignedOut: () -> Unit,
) = composable(
    route = PreferencesScreenRoute,
    enterTransition = {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250))
    },
    popExitTransition = {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(200))
    },
) {

    var isSignOutConfirmationShown by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val viewModel = koinViewModel<PreferencesScreenViewModel>()

    // The user may come back from the system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.onNotificationAccessChecked(NotificationAccess.isGranted(context))
        // The per-app language may be changed in the system settings.
        viewModel.refreshLanguage()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PreferencesScreenViewModel.Event.ProceedToPasscodeSetup ->
                    onProceedToPasscodeSetup()

                PreferencesScreenViewModel.Event.SignedOut ->
                    onSignedOut()

                PreferencesScreenViewModel.Event.ProceedToNotificationAccessSettings ->
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

                PreferencesScreenViewModel.Event.ProceedToSignOutConfirmation -> {
                    isSignOutConfirmationShown = true
                }
            }
        }
    }

    PreferencesScreen(
        viewModel = viewModel,
        onBack = onBack,
        modifier = Modifier
            .fillMaxSize()
    )

    if (isSignOutConfirmationShown) {
        MoneyDialog(
            title = stringResource(R.string.settings_sign_out_title),
            text = stringResource(R.string.settings_sign_out_text),
            confirmText = stringResource(R.string.settings_sign_out),
            isDestructive = true,
            onConfirm = {
                isSignOutConfirmationShown = false
                viewModel.onSignOutConfirmed()
            },
            onDismissRequest = {
                isSignOutConfirmationShown = false
            },
        )
    }
}
