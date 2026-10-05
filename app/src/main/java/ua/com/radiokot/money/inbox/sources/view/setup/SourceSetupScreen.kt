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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.view.setup.SourceSetupState.Step
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun SourceSetupScreen(
    viewModel: SourceSetupViewModel,
) {
    val state by viewModel.state.collectAsState()
    val isPickingApp by viewModel.isPickingApp.collectAsState()

    BackHandler(onBack = viewModel::onBackClicked)

    if (isPickingApp) {
        SourceSetupAllAppsPicker(
            apps = viewModel.allApps.collectAsState(),
            sourcePackages = viewModel.sourcePackages.collectAsState(),
            onPicked = viewModel::onAppPickedFromAll,
            onBackClicked = viewModel::onBackClicked,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ScreenTopBar(
            title = stepTitle(state.step),
            navigationIcon =
                if (state.canGoBack)
                    R.drawable.ic_tabler_arrow_left
                else
                    R.drawable.ic_tabler_x,
            navigationContentDescription = stringResource(
                if (state.canGoBack)
                    R.string.common_back
                else
                    R.string.common_close
            ),
            onNavigationClicked = viewModel::onBackClicked,
        )

        StepIndicator(
            step = state.step,
        )

        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "setup-step",
            modifier = Modifier
                .weight(1f)
        ) { step ->
            when (step) {
                Step.Intro ->
                    SourceSetupIntroStep(
                        isAccessGranted = viewModel.isAccessGranted.collectAsState(),
                        onGrantClicked = viewModel::onGrantAccessClicked,
                        onNextClicked = viewModel::onNextClicked,
                        onLaterClicked = viewModel::onLaterClicked,
                    )

                Step.App ->
                    SourceSetupAppStep(
                        detectedApps = viewModel.detectedApps.collectAsState(),
                        selectedPackage = state.packageName,
                        chosenAppLabel = viewModel.chosenAppLabel.collectAsState(),
                        onAppSelected = viewModel::onAppSelected,
                        onPickFromAllClicked = viewModel::onPickFromAllAppsClicked,
                        onNextClicked = viewModel::onNextClicked,
                    )

                Step.Sample ->
                    SourceSetupSampleStep(
                        packageName = state.packageName,
                        appLabel = viewModel.chosenAppLabel.collectAsState(),
                        samples = state.samples,
                        selectedSample = state.selectedSample,
                        onSampleSelected = viewModel::onSampleSelected,
                        onUseClicked = viewModel::onNextClicked,
                    )

                Step.Teach -> {
                    val teach by viewModel.teach.collectAsState()

                    teach?.let { draft ->
                        SourceSetupTeachStep(
                            packageName = state.packageName,
                            appLabel = viewModel.chosenAppLabel.collectAsState(),
                            draft = draft,
                            onTokenTapped = viewModel::onTokenTapped,
                            onRoleChosen = viewModel::onRoleChosen,
                            onDirectionChosen = viewModel::onDirectionChosen,
                            onCheckClicked = viewModel::onCheckOnOthersClicked,
                        )
                    }
                }

                Step.Test ->
                    SourceSetupTestStep(
                        packageName = state.packageName,
                        appLabel = viewModel.chosenAppLabel.collectAsState(),
                        result = viewModel.testRun.collectAsState().value,
                        canFinish = viewModel.canFinish.collectAsState().value,
                        onTeachAnotherClicked = viewModel::onTeachAnotherClicked,
                        onNextClicked = viewModel::onNextClicked,
                    )

                Step.Accounts ->
                    SourceSetupAccountsStep(
                        mapping = viewModel.mapping.collectAsState().value,
                        behaviour = viewModel.behaviour.collectAsState().value,
                        isSaving = viewModel.isSaving.collectAsState().value,
                        isSaveFailed = viewModel.isSaveFailed.collectAsState().value,
                        onRowClicked = viewModel::onAccountRowClicked,
                        onRecordKnownPayeesChanged = viewModel::onRecordKnownPayeesChanged,
                        onAskInNotificationChanged = viewModel::onAskInNotificationChanged,
                        onLearnFromHistoryChanged = viewModel::onLearnFromHistoryChanged,
                        onDoneClicked = viewModel::onDoneClicked,
                    )
            }
        }
    }
}

@Composable
private fun stepTitle(step: Step): String =
    when (step) {
        Step.App -> stringResource(R.string.setup_app_title)
        Step.Sample -> stringResource(R.string.setup_sample_title)
        Step.Teach -> stringResource(R.string.setup_teach_title)
        Step.Test -> stringResource(R.string.setup_test_title)
        Step.Accounts -> stringResource(R.string.setup_accounts_title)
        else -> ""
    }

/**
 * Six segments, the ones up to the current step are filled.
 */
@Composable
private fun StepIndicator(
    step: Step,
) {
    val description = stringResource(
        R.string.setup_step_description,
        step.number,
        Step.entries.size,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 4.dp,
            )
            .semantics { contentDescription = description }
    ) {
        Step.entries.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(MoneyShapes.pill)
                    .background(
                        if (segment.number <= step.number)
                            MoneyTheme.colors.accent
                        else
                            MoneyTheme.colors.line
                    )
            )
        }
    }
}

/**
 * A scrolling body with a footer of actions pinned to the bottom.
 */
@Composable
internal fun SetupStepLayout(
    footer: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = Modifier
        .fillMaxSize()
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(
                start = MoneySpacing.screen,
                end = MoneySpacing.screen,
                top = 12.dp,
                bottom = 16.dp,
            ),
        content = body,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MoneySpacing.screen,
                end = MoneySpacing.screen,
                bottom = 16.dp,
            ),
        content = footer,
    )
}
