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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.view.setup.SourceSetupState.Step

class SourceSetupStateTest {

    private val sample = RecentNotification(
        packageName = "com.example.bank",
        postTimeMillis = 1L,
        title = null,
        text = "Paid 3.40 EUR",
    )

    @Test
    fun initial_withoutPackage_startsAtIntro() {
        val state = SourceSetupState.initial(null)

        assertEquals(Step.Intro, state.step)
        assertNull(state.packageName)
        assertFalse(state.startedAtSample)
    }

    @Test
    fun initial_withPackage_startsAtSample() {
        val state = SourceSetupState.initial("com.example.bank")

        assertEquals(Step.Sample, state.step)
        assertEquals("com.example.bank", state.packageName)
        assertTrue(state.startedAtSample)
    }

    @Test
    fun next_introToApp() {
        assertEquals(Step.App, SourceSetupState.initial(null).next().step)
    }

    @Test
    fun next_appWithoutPackage_stays() {
        val state = SourceSetupState.initial(null).next()

        assertEquals(Step.App, state.next().step)
    }

    @Test
    fun next_appWithPackage_toSample_clearsSelection() {
        val state = SourceSetupState.initial(null).next()
            .withPackage("com.example.bank")
            .copy(selectedSample = sample)
            .next()

        assertEquals(Step.Sample, state.step)
        assertNull(state.selectedSample)
    }

    @Test
    fun next_sampleWithoutSelection_stays() {
        val state = SourceSetupState.initial("com.example.bank")

        assertEquals(Step.Sample, state.next().step)
    }

    @Test
    fun next_sampleWithSelection_toTeach() {
        val state = SourceSetupState.initial("com.example.bank")
            .copy(selectedSample = sample)

        assertEquals(Step.Teach, state.next().step)
    }

    @Test
    fun next_walksTheRestAndStopsAtAccounts() {
        var state = SourceSetupState.initial("com.example.bank")
            .copy(selectedSample = sample, step = Step.Teach)

        state = state.next()
        assertEquals(Step.Test, state.step)
        state = state.next()
        assertEquals(Step.Accounts, state.step)
        assertEquals(Step.Accounts, state.next().step)
    }

    @Test
    fun back_walksDown() {
        val state = SourceSetupState.initial(null)
            .copy(step = Step.Accounts, packageName = "com.example.bank")

        assertEquals(Step.Test, state.back().step)
        assertEquals(Step.Teach, state.back().back().step)
        assertEquals(Step.Sample, state.back().back().back().step)
        assertEquals(Step.App, state.back().back().back().back().step)
        assertEquals(Step.Intro, state.back().back().back().back().back().step)
    }

    @Test
    fun canGoBack_falseAtIntro() {
        assertFalse(SourceSetupState.initial(null).canGoBack)
        assertEquals(Step.Intro, SourceSetupState.initial(null).back().step)
    }

    @Test
    fun canGoBack_falseAtSample_whenStartedThere() {
        val state = SourceSetupState.initial("com.example.bank")

        assertFalse(state.canGoBack)
        assertEquals(Step.Sample, state.back().step)
    }

    @Test
    fun canGoBack_trueAtTeach_whenStartedAtSample() {
        val state = SourceSetupState.initial("com.example.bank").copy(step = Step.Teach)

        assertTrue(state.canGoBack)
        assertEquals(Step.Sample, state.back().step)
    }

    @Test
    fun canGoBack_trueAtSample_whenStartedAtIntro() {
        val state = SourceSetupState.initial(null).copy(step = Step.Sample)

        assertTrue(state.canGoBack)
    }

    @Test
    fun withPackage_otherPackage_resetsSamplesAndSelection() {
        val state = SourceSetupState.initial("com.example.bank")
            .copy(samples = listOf(sample), selectedSample = sample)
            .withPackage("com.example.other")

        assertEquals("com.example.other", state.packageName)
        assertEquals(emptyList<RecentNotification>(), state.samples)
        assertNull(state.selectedSample)
    }

    @Test
    fun withPackage_samePackage_keepsSamples() {
        val state = SourceSetupState.initial("com.example.bank")
            .copy(samples = listOf(sample), selectedSample = sample)
            .withPackage("com.example.bank")

        assertEquals(listOf(sample), state.samples)
        assertEquals(sample, state.selectedSample)
    }

    @Test
    fun stepNumber_isOneBased_sixSegments() {
        assertEquals(6, Step.entries.size)
        assertEquals(1, Step.Intro.number)
        assertEquals(6, Step.Accounts.number)
    }

    @Test
    fun withNotifications_takesOwnPackage_preselectsNewest() {
        val older = sample.copy(postTimeMillis = 1L, text = "older")
        val newer = sample.copy(postTimeMillis = 2L, text = "newer")
        val other = sample.copy(packageName = "com.example.other")

        val state = SourceSetupState.initial("com.example.bank")
            .withNotifications(listOf(older, other, newer))

        assertEquals(listOf(newer, older), state.samples)
        assertEquals(newer, state.selectedSample)
    }

    @Test
    fun withNotifications_keepsSelection_whenStillThere() {
        val older = sample.copy(postTimeMillis = 1L, text = "older")
        val newer = sample.copy(postTimeMillis = 2L, text = "newer")

        val state = SourceSetupState.initial("com.example.bank")
            .withNotifications(listOf(older, newer))
            .copy(selectedSample = older)
            .withNotifications(listOf(older, newer))

        assertEquals(older, state.selectedSample)
    }

    @Test
    fun withNotifications_dropsSelection_whenGone() {
        val state = SourceSetupState.initial("com.example.bank")
            .copy(samples = listOf(sample), selectedSample = sample)
            .withNotifications(emptyList())

        assertEquals(emptyList<RecentNotification>(), state.samples)
        assertNull(state.selectedSample)
    }

    @Test
    fun withNotifications_withoutPackage_noSamples() {
        val state = SourceSetupState.initial(null).withNotifications(listOf(sample))

        assertEquals(emptyList<RecentNotification>(), state.samples)
        assertNull(state.selectedSample)
    }
}
