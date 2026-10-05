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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.TemplateFields

const val EXAMPLE_PACKAGE = "com.example.bank"

/**
 * Matches "Paid 12,50 EUR at <payee>", after a title line or without one (a null title).
 */
const val TEST_TEMPLATE_PATTERN = "(?:[^\\n]*\\n)?Paid (\\d+[.,]\\d{2}) (EUR) at (.+?)"

fun testTemplate(
    id: String,
    sourcePackage: String = EXAMPLE_PACKAGE,
    pattern: String = TEST_TEMPLATE_PATTERN,
    isEnabled: Boolean = true,
    createdAt: LocalDateTime = LocalDateTime(2026, 10, 1, 10, 0),
    direction: NotificationTemplate.Direction = NotificationTemplate.Direction.Outgoing,
) = NotificationTemplate(
    id = id,
    sourcePackage = sourcePackage,
    name = "Paid",
    direction = direction,
    pattern = pattern,
    fields = TemplateFields(amount = 1, currency = 2, payee = 3),
    sampleText = "Paid 3,40 EUR at Fuelstop",
    isEnabled = isEnabled,
    createdAt = createdAt,
)

class FakeAutoBookPreferences(
    private val presetPackages: Set<String>,
) : AutoBookPreferences {
    private val presetEnabled = MutableStateFlow(presetPackages.associateWith { true })
    private var cachedActivePackages: Set<String>? = null
    val cachedSets = mutableListOf<Set<String>>()

    override fun isPresetEnabled(packageName: String): Boolean =
        presetEnabled.value[packageName] ?: true

    override fun setPresetEnabled(packageName: String, isEnabled: Boolean) {
        presetEnabled.update { it + (packageName to isEnabled) }
    }

    override fun getPresetEnabledFlow(): Flow<Map<String, Boolean>> =
        presetEnabled.asStateFlow()

    @Synchronized
    override fun getCachedActivePackages(): Set<String> =
        cachedActivePackages
            ?: presetPackages.filterTo(mutableSetOf(), ::isPresetEnabled)

    @Synchronized
    override fun setCachedActivePackages(packages: Set<String>) {
        cachedActivePackages = packages
        cachedSets += packages
    }
}
