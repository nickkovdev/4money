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

package ua.com.radiokot.money.inbox.sources.logic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.data.RecentNotification
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplateRepository

class SaveSourceSetupUseCaseTest {

    private val templates = FakeTemplates()
    private val cards = FakeCards()
    private val buffer = FakeBuffer()
    private val preferences = FakeAutoBookPreferences(emptySet())
    private val useCase = SaveSourceSetupUseCase(templates, cards, preferences, buffer)

    private val behaviour = AutoBookBehaviour(
        recordKnownPayees = false,
        askInNotification = true,
        learnFromHistory = false,
    )

    private fun request(
        templates: List<NotificationTemplate> = emptyList(),
        cardAccounts: Map<String, String> = emptyMap(),
        sourceAccountId: String? = null,
    ) = SaveSourceSetupUseCase.Request(
        packageName = EXAMPLE_PACKAGE,
        templates = templates,
        cardAccounts = cardAccounts,
        sourceAccountId = sourceAccountId,
        behaviour = behaviour,
    )

    @Test
    fun savesTemplatesEnabledUnderThePackage() = runBlocking {
        useCase(
            request(
                templates = listOf(
                    testTemplate("a", sourcePackage = "other", isEnabled = false),
                    testTemplate("b", isEnabled = false),
                ),
            )
        )

        val saved = templates.addCalls.single()
        assertEquals(listOf("a", "b"), saved.map { it.id })
        assertTrue(saved.all { it.isEnabled })
        assertTrue(saved.all { it.sourcePackage == EXAMPLE_PACKAGE })
    }

    @Test
    fun storesAccountsAndBehaviour() = runBlocking {
        useCase(
            request(
                cardAccounts = mapOf("1234" to "acc-1", "5678" to "acc-2"),
                sourceAccountId = "acc-3",
            )
        )

        assertEquals(mapOf("1234" to "acc-1", "5678" to "acc-2"), cards.byCard)
        assertEquals(mapOf(EXAMPLE_PACKAGE to "acc-3"), cards.bySource)
        assertFalse(preferences.isRecordKnownPayeesEnabled)
        assertTrue(preferences.isAskInNotificationEnabled)
        assertFalse(preferences.isLearnFromHistoryEnabled)
    }

    @Test
    fun noTemplatesNoSourceAccount_writesNoTemplatesNorSourceAccount() = runBlocking {
        useCase(request())

        assertTrue(templates.addCalls.isEmpty())
        assertTrue(cards.byCard.isEmpty())
        assertNull(cards.getAccountIdForSource(EXAMPLE_PACKAGE))
    }

    @Test
    fun clearsTheBuffer() = runBlocking {
        buffer.add(RecentNotification(EXAMPLE_PACKAGE, 1L, null, "x"))

        useCase(request())

        assertEquals(1, buffer.clearCount)
        assertTrue(buffer.getAll().isEmpty())
    }

    @Test
    fun failedTemplateSave_throwsAndWritesNothingElse() {
        templates.failure = IllegalStateException("db")

        val error = runCatching {
            runBlocking {
                useCase(
                    request(
                        templates = listOf(testTemplate("a")),
                        cardAccounts = mapOf("1234" to "acc-1"),
                        sourceAccountId = "acc-3",
                    )
                )
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(cards.byCard.isEmpty())
        assertTrue(cards.bySource.isEmpty())
        assertEquals(0, buffer.clearCount)
        assertTrue(preferences.isRecordKnownPayeesEnabled)
    }

    private class FakeTemplates : NotificationTemplateRepository {
        val addCalls = mutableListOf<List<NotificationTemplate>>()
        var failure: Exception? = null

        override fun getTemplatesFlow(): Flow<List<NotificationTemplate>> =
            MutableStateFlow(emptyList())

        override suspend fun getTemplates(): List<NotificationTemplate> = emptyList()

        override suspend fun addTemplates(templates: List<NotificationTemplate>) {
            failure?.also { throw it }
            addCalls += templates
        }

        override suspend fun setEnabledForPackage(sourcePackage: String, isEnabled: Boolean) = Unit

        override suspend fun deleteTemplate(id: String) = Unit
    }

    private class FakeCards : CardAccountPreferences {
        val byCard = linkedMapOf<String, String>()
        val bySource = linkedMapOf<String, String>()
        private val cardsFlow = MutableStateFlow(emptyMap<String, String>())

        override fun getAccountIdForCard(cardLast4: String): String? = byCard[cardLast4]

        override fun setAccountIdForCard(cardLast4: String, accountId: String) {
            byCard[cardLast4] = accountId
            cardsFlow.update { byCard.toMap() }
        }

        override fun getCardAccountsFlow(): Flow<Map<String, String>> = cardsFlow

        override fun getAccountIdForSource(sourcePackage: String): String? = bySource[sourcePackage]

        override fun setAccountIdForSource(sourcePackage: String, accountId: String) {
            bySource[sourcePackage] = accountId
        }

        override fun getSourceAccountsFlow(): Flow<Map<String, String>> =
            cardsFlow.map { bySource.toMap() }
    }

    private class FakeBuffer : RecentNotificationBuffer {
        private val items = mutableListOf<RecentNotification>()
        var clearCount = 0

        override fun add(notification: RecentNotification) {
            items += notification
        }

        override fun getAll(): List<RecentNotification> = items.toList()

        override fun clear() {
            clearCount++
            items.clear()
        }
    }
}
