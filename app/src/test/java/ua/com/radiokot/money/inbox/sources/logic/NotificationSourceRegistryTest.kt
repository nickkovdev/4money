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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.logic.SebLatviaNotificationParser
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplateRepository
import java.math.BigDecimal
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NotificationSourceRegistryTest {

    private val seb = "se.seb.latvia"
    private val bank = EXAMPLE_PACKAGE

    private val sebSamples = listOf(
        "Jauna rezervācija" to "Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .",
        "Jauns darījums" to "Jūs samaksājāt 30,00 EUR EXAMPLE SIA par parking. Konta bilance:",
        "Jauns darījums" to "EXAMPLE SIA samaksāja 1000,00 EUR par Darba alga. Konta bilance:",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = FakeTemplateRepository()
    private val preferences = FakeAutoBookPreferences(presetPackages = setOf(seb))

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun registry(
        firstLoadTimeout: kotlin.time.Duration = 2.seconds,
    ) = NotificationSourceRegistry(
        templateRepository = repository,
        autoBookPreferences = preferences,
        scope = scope,
        firstLoadTimeout = firstLoadTimeout,
    )

    private fun template(
        id: String,
        sourcePackage: String = bank,
        pattern: String = TEST_TEMPLATE_PATTERN,
        isEnabled: Boolean = true,
        createdAt: LocalDateTime = LocalDateTime(2026, 10, 1, 10, 0),
        direction: NotificationTemplate.Direction = NotificationTemplate.Direction.Outgoing,
    ) = testTemplate(
        id = id,
        sourcePackage = sourcePackage,
        pattern = pattern,
        isEnabled = isEnabled,
        createdAt = createdAt,
        direction = direction,
    )

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(2.seconds) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    @Test
    fun sebPresetIsSourceByDefaultAndParsesLikeTheParser() = runBlocking {
        repository.templates.value = emptyList()
        val registry = registry()
        val parser = SebLatviaNotificationParser()

        sebSamples.forEach { (title, text) ->
            val expected = parser.parse(title, text)
            assertTrue(expected is ParsedBankNotification.Payment)
            assertEquals(expected, registry.parse(seb, title, text))
        }

        awaitUntil { registry.sourcesFlow.value.isNotEmpty() }
        val source = registry.sourcesFlow.value.single()
        assertEquals(seb, source.packageName)
        assertSame(SebLatviaPreset, source.preset)
        assertTrue(source.isEnabled)
    }

    @Test
    fun disabledPresetIsNotSource() = runBlocking {
        repository.templates.value = emptyList()
        val registry = registry()

        registry.setSourceEnabled(seb, false)

        assertFalse(preferences.isPresetEnabled(seb))
        assertNull(registry.parse(seb, sebSamples[0].first, sebSamples[0].second))
        awaitUntil { registry.sourcesFlow.value.singleOrNull()?.isEnabled == false }
    }

    @Test
    fun unknownPackageIsNotSource() = runBlocking {
        repository.templates.value = emptyList()

        assertNull(registry().parse(bank, null, "Paid 3,40 EUR at Fuelstop"))
    }

    @Test
    fun templatePackageParsesWithEnabledTemplate() = runBlocking {
        repository.templates.value = listOf(template("t1"))

        assertEquals(
            ParsedBankNotification.Payment(
                amount = BigDecimal("12.50"),
                currencyCode = "EUR",
                cardLast4 = null,
                payee = "Fuelstop",
                isIncoming = false,
                hasTimestamp = false,
            ),
            registry().parse(bank, null, "Paid 12,50 EUR at Fuelstop"),
        )
    }

    @Test
    fun allTemplatesDisabledIsNotSourceButListed() = runBlocking {
        repository.templates.value = listOf(template("t1", isEnabled = false))
        val registry = registry()

        assertNull(registry.parse(bank, null, "Paid 12,50 EUR at Fuelstop"))
        awaitUntil { registry.sourcesFlow.value.size == 2 }
        val source = registry.sourcesFlow.value[1]
        assertEquals(bank, source.packageName)
        assertNull(source.preset)
        assertFalse(source.isEnabled)
        assertEquals(listOf("t1"), source.templates.map(NotificationTemplate::id))
    }

    @Test
    fun setSourceEnabledTogglesTemplatesOfPackage() = runBlocking {
        repository.templates.value = listOf(template("t1"))
        val registry = registry()

        registry.setSourceEnabled(bank, false)

        assertEquals(listOf(bank to false), repository.enabledCalls)
        assertFalse(repository.templates.value!!.single().isEnabled)
        assertTrue(preferences.isPresetEnabled(seb))
    }

    @Test
    fun presetFirstThenTemplateOfSamePackage() = runBlocking {
        repository.templates.value = listOf(
            template(
                id = "seb-extra",
                sourcePackage = seb,
                pattern = "SEB\\nIenākošs maksājums (\\d+,\\d{2}) (EUR) no (.+?)",
                direction = NotificationTemplate.Direction.Incoming,
            )
        )
        val registry = registry()

        // The preset still wins for the texts it knows.
        assertEquals(
            SebLatviaNotificationParser().parse(sebSamples[0].first, sebSamples[0].second),
            registry.parse(seb, sebSamples[0].first, sebSamples[0].second),
        )

        val payment = registry.parse(seb, "SEB", "Ienākošs maksājums 10,00 EUR no EXAMPLE SIA")
                as ParsedBankNotification.Payment
        assertEquals("EXAMPLE SIA", payment.payee)
        assertTrue(payment.isIncoming)

        awaitUntil { registry.sourcesFlow.value.isNotEmpty() }
        val source = registry.sourcesFlow.value.single()
        assertSame(SebLatviaPreset, source.preset)
        assertEquals(listOf("seb-extra"), source.templates.map(NotificationTemplate::id))
    }

    @Test
    fun olderTemplateWinsWhenBothMatch() = runBlocking {
        repository.templates.value = listOf(
            template(
                id = "newer",
                pattern = "Paid (\\d+,\\d{2}) (EUR) (.+?)",
                createdAt = LocalDateTime(2026, 10, 3, 10, 0),
            ),
            template(
                id = "older",
                pattern = "Paid (\\d+,\\d{2}) (EUR) at (.+?)",
                createdAt = LocalDateTime(2026, 10, 2, 10, 0),
            ),
        )

        val payment = registry().parse(bank, null, "Paid 12,50 EUR at Fuelstop")
                as ParsedBankNotification.Payment

        assertEquals("Fuelstop", payment.payee)
    }

    @Test
    fun sourceTextMatchingNothingIsUnrecognized() = runBlocking {
        repository.templates.value = listOf(template("t1"))
        val registry = registry()

        assertSame(
            ParsedBankNotification.Unrecognized,
            registry.parse(bank, null, "Your statement is ready"),
        )
        assertSame(
            ParsedBankNotification.Unrecognized,
            registry.parse(seb, "SEB", "Jums ir jauns ziņojums internetbankā"),
        )
    }

    @Test
    fun parseWaitsForFirstTemplateLoad() = runBlocking {
        val registry = registry()

        val parsing = async(Dispatchers.Default) {
            registry.parse(bank, null, "Paid 12,50 EUR at Fuelstop")
        }
        delay(100)
        assertFalse(parsing.isCompleted)

        repository.templates.value = listOf(template("t1"))

        assertEquals("Fuelstop", (parsing.await() as ParsedBankNotification.Payment).payee)
    }

    @Test
    fun cachedActivePackageIsUnrecognizedWhenTemplatesNeverLoad() = runBlocking {
        preferences.setCachedActivePackages(setOf(seb, bank))
        val registry = registry(firstLoadTimeout = 50.milliseconds)

        assertSame(
            ParsedBankNotification.Unrecognized,
            registry.parse(bank, null, "Paid 12,50 EUR at Fuelstop"),
        )
        assertNull(registry.parse("com.example.other", null, "Paid 12,50 EUR at Fuelstop"))
        // The preset doesn't need templates.
        assertTrue(registry.parse(seb, sebSamples[0].first, sebSamples[0].second) is ParsedBankNotification.Payment)
        // Not overwritten before the first load.
        assertEquals(setOf(seb, bank), preferences.getCachedActivePackages())
    }

    @Test
    fun cachedActivePackagesFollowSources() = runBlocking {
        repository.templates.value = emptyList()
        val registry = registry()

        awaitUntil { preferences.cachedSets.isNotEmpty() }
        assertEquals(setOf(seb), preferences.getCachedActivePackages())

        repository.templates.value = listOf(template("t1"))
        awaitUntil { preferences.getCachedActivePackages() == setOf(seb, bank) }

        registry.setSourceEnabled(bank, false)
        awaitUntil { preferences.getCachedActivePackages() == setOf(seb) }

        registry.setSourceEnabled(seb, false)
        awaitUntil { preferences.getCachedActivePackages() == emptySet<String>() }
    }

    @Test
    fun sourcesOrderedPresetsFirstThenByFirstTemplate() = runBlocking {
        repository.templates.value = listOf(
            template("a1", sourcePackage = "com.example.a", createdAt = LocalDateTime(2026, 10, 3, 10, 0)),
            template("b1", sourcePackage = "com.example.b", createdAt = LocalDateTime(2026, 10, 1, 10, 0)),
            template("a0", sourcePackage = "com.example.a", createdAt = LocalDateTime(2026, 10, 4, 10, 0)),
        )
        val registry = registry()

        awaitUntil { registry.sourcesFlow.value.size == 3 }
        assertEquals(
            listOf(seb, "com.example.b", "com.example.a"),
            registry.sourcesFlow.value.map(NotificationSourceRegistry.Source::packageName),
        )
        assertEquals(
            listOf("a1", "a0"),
            registry.sourcesFlow.value[2].templates.map(NotificationTemplate::id),
        )
    }

    @Test
    fun parseWithSkipsDisabledTemplates() {
        assertSame(
            ParsedBankNotification.Unrecognized,
            NotificationSourceRegistry.parseWith(
                preset = null,
                templates = listOf(template("t1", isEnabled = false)),
                title = null,
                text = "Paid 12,50 EUR at Fuelstop",
            ),
        )
    }

    private class FakeTemplateRepository : NotificationTemplateRepository {
        val templates = MutableStateFlow<List<NotificationTemplate>?>(null)
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()

        override fun getTemplatesFlow(): Flow<List<NotificationTemplate>> =
            templates.filterNotNull()

        override suspend fun getTemplates(): List<NotificationTemplate> =
            templates.value.orEmpty()

        override suspend fun addTemplates(templates: List<NotificationTemplate>) {
            this.templates.update { it.orEmpty() + templates }
        }

        override suspend fun setEnabledForPackage(sourcePackage: String, isEnabled: Boolean) {
            enabledCalls += sourcePackage to isEnabled
            templates.update { current ->
                current?.map {
                    if (it.sourcePackage == sourcePackage) it.copy(isEnabled = isEnabled) else it
                }
            }
        }

        override suspend fun deleteTemplate(id: String) {
            templates.update { current -> current?.filterNot { it.id == id } }
        }
    }
}
