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

import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.logic.mergeSamples
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate

/**
 * The state of the source setup wizard. Transitions are pure.
 *
 * @param samples the notifications of [packageName], newest first, distinct
 * @param drafts the kinds taught in the Teach step, kept across "Teach another kind"
 * @param startedAtSample the wizard was opened for an existing source, there is no way back
 * to choosing the app
 */
data class SourceSetupState(
    val step: Step,
    val packageName: String?,
    val samples: List<RecentNotification>,
    val selectedSample: RecentNotification?,
    val drafts: List<NotificationTemplate>,
    val startedAtSample: Boolean,
) {

    enum class Step {
        Intro,
        App,
        Sample,
        Teach,
        Test,
        Accounts,
        ;

        /**
         * One-based, for the step indicator.
         */
        val number: Int
            get() = ordinal + 1
    }

    val canGoBack: Boolean
        get() = when (step) {
            Step.Intro -> false
            // After "Teach another kind" the way back leads to the Test step.
            Step.Sample -> !startedAtSample || drafts.isNotEmpty()
            else -> true
        }

    // The sample being taught must not be swapped by a notification that arrives meanwhile.
    private val isSampleFrozen: Boolean
        get() = step == Step.Teach || step == Step.Test || step == Step.Accounts

    /**
     * Moves forward when the step is complete: an app is chosen on App,
     * a sample on Sample. Otherwise the state stays.
     */
    fun next(): SourceSetupState =
        when (step) {
            Step.Intro ->
                copy(step = Step.App)

            Step.App ->
                if (packageName != null)
                    copy(step = Step.Sample, selectedSample = null)
                else
                    this

            Step.Sample ->
                if (selectedSample != null)
                    copy(step = Step.Teach)
                else
                    this

            Step.Teach ->
                copy(step = Step.Test)

            Step.Test ->
                copy(step = Step.Accounts)

            Step.Accounts ->
                this
        }

    /**
     * Moves back if [canGoBack], otherwise the state stays.
     */
    fun back(): SourceSetupState =
        when {
            !canGoBack ->
                this

            step == Step.Sample && drafts.isNotEmpty() ->
                copy(step = Step.Test)

            else ->
                copy(step = Step.entries[step.ordinal - 1])
        }

    /**
     * "Teach another kind": from the Test step back to choosing a sample, the drafts are kept.
     */
    fun teachAnother(): SourceSetupState =
        if (step == Step.Test)
            copy(step = Step.Sample)
        else
            this

    /**
     * Adds the [draft], replacing the earlier one taught from the same sample.
     */
    fun withDraft(draft: NotificationTemplate): SourceSetupState =
        copy(
            drafts = drafts.filterNot { it.sampleText == draft.sampleText } + draft,
        )

    /**
     * Chooses the app. The samples and the selection of another app are dropped.
     */
    fun withPackage(packageName: String): SourceSetupState =
        if (packageName == this.packageName)
            this
        else
            copy(
                packageName = packageName,
                samples = emptyList(),
                selectedSample = null,
                drafts = emptyList(),
            )

    /**
     * Refreshes the [samples] of the chosen app from all the known [notifications].
     * Keeps the selection if it is still there, otherwise selects the newest.
     * From the Teach step on the selection is frozen, whatever arrives.
     */
    fun withNotifications(notifications: List<RecentNotification>): SourceSetupState {
        val samples =
            if (packageName != null)
                mergeSamples(
                    buffered = notifications,
                    active = emptyList(),
                    packageName = packageName,
                )
            else
                emptyList()

        return copy(
            samples = samples,
            selectedSample =
                if (isSampleFrozen)
                    selectedSample
                else
                    selectedSample?.takeIf { it in samples }
                        ?: samples.firstOrNull(),
        )
    }

    companion object {
        /**
         * @param packageName the source to teach, null to add a new app from the start
         */
        fun initial(packageName: String?) = SourceSetupState(
            step = if (packageName != null) Step.Sample else Step.Intro,
            packageName = packageName,
            samples = emptyList(),
            selectedSample = null,
            drafts = emptyList(),
            startedAtSample = packageName != null,
        )
    }
}
