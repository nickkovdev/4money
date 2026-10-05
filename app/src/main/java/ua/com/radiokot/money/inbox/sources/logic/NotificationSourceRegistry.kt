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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplateRepository
import ua.com.radiokot.money.inbox.templates.logic.TemplateMatcher
import ua.com.radiokot.money.lazyLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Notification sources: the built-in presets and the packages of user templates.
 * Keeps [AutoBookPreferences.setCachedActivePackages] in sync with the active ones.
 *
 * @param scope where the templates are collected; lives as long as the session
 * @param firstLoadTimeout how long [parse] waits for the templates to load
 */
class NotificationSourceRegistry(
    private val templateRepository: NotificationTemplateRepository,
    private val autoBookPreferences: AutoBookPreferences,
    scope: CoroutineScope,
    private val presets: List<NotificationSourcePreset> = BuiltInPresets.all,
    private val firstLoadTimeout: Duration = 2.seconds,
) : BankNotificationParsing {

    private val log by lazyLogger("NotificationSourceRegistry")

    /**
     * @param templates all the templates of the package, enabled or not,
     * by creation time, then by ID
     * @param isEnabled the preset switch for a preset package,
     * otherwise whether any of the templates is enabled
     */
    data class Source(
        val packageName: String,
        val preset: NotificationSourcePreset?,
        val templates: List<NotificationTemplate>,
        val isEnabled: Boolean,
    )

    // Null until the first load.
    private val templatesStateFlow = MutableStateFlow<List<NotificationTemplate>?>(null)

    private val loadedSourcesFlow =
        combine(
            templatesStateFlow.filterNotNull(),
            autoBookPreferences.getPresetEnabledFlow(),
        ) { templates, presetEnabled ->
            buildSources(
                templates = templates,
                isPresetEnabled = { presetEnabled[it] ?: autoBookPreferences.isPresetEnabled(it) },
            )
        }

    /**
     * Presets first, then the template packages by their first template creation time.
     * Until the templates are loaded, the presets only.
     */
    val sourcesFlow: StateFlow<List<Source>> =
        loadedSourcesFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = buildSources(
                templates = emptyList(),
                isPresetEnabled = autoBookPreferences::isPresetEnabled,
            ),
        )

    init {
        templateRepository
            .getTemplatesFlow()
            .onEach { templatesStateFlow.value = it }
            .catch { error ->
                log.error(error) {
                    "init(): failed to collect the templates"
                }
            }
            .launchIn(scope)

        // From the loaded sources only: until the templates are loaded,
        // the cache must keep the template packages.
        loadedSourcesFlow
            .map { sources ->
                sources
                    .filter(Source::isEnabled)
                    .mapTo(mutableSetOf(), Source::packageName)
            }
            .distinctUntilChanged()
            .onEach(autoBookPreferences::setCachedActivePackages)
            .catch { error ->
                log.error(error) {
                    "init(): failed to cache the active packages"
                }
            }
            .launchIn(scope)
    }

    /**
     * Toggles the switch of a preset package,
     * otherwise all the templates of the package.
     */
    suspend fun setSourceEnabled(packageName: String, isEnabled: Boolean) {
        if (presets.any { it.packageName == packageName }) {
            autoBookPreferences.setPresetEnabled(packageName, isEnabled)
        } else {
            templateRepository.setEnabledForPackage(packageName, isEnabled)
        }
    }

    /**
     * Waits for the first template load (at most [firstLoadTimeout]) before deciding,
     * so notifications right after a process start are not lost. If the templates
     * don't load in time, a template package among the cached active packages
     * is a source with nothing matched.
     */
    override suspend fun parse(
        packageName: String,
        title: String?,
        text: String,
    ): ParsedBankNotification? {
        val preset = presets.firstOrNull { it.packageName == packageName }
        if (preset != null && !autoBookPreferences.isPresetEnabled(packageName)) {
            return null
        }

        val templates = withTimeoutOrNull(firstLoadTimeout) {
            templatesStateFlow.filterNotNull().first()
        }

        if (templates == null) {
            log.warn {
                "parse(): the templates are not loaded in time:" +
                        "\npackageName=$packageName"
            }

            return when {
                preset != null ->
                    parseWith(preset, emptyList(), title, text)

                packageName in autoBookPreferences.getCachedActivePackages() ->
                    ParsedBankNotification.Unrecognized

                else ->
                    null
            }
        }

        val packageTemplates = templates.filter { it.sourcePackage == packageName }
        if (preset == null && packageTemplates.none(NotificationTemplate::isEnabled)) {
            return null
        }

        return parseWith(preset, packageTemplates, title, text)
    }

    private fun buildSources(
        templates: List<NotificationTemplate>,
        isPresetEnabled: (packageName: String) -> Boolean,
    ): List<Source> {
        // groupBy keeps the order of the first template of each package.
        val templatesByPackage: Map<String, List<NotificationTemplate>> =
            templates
                .sortedWith(templateOrder)
                .groupBy(NotificationTemplate::sourcePackage)

        val presetSources = presets.map { preset ->
            Source(
                packageName = preset.packageName,
                preset = preset,
                templates = templatesByPackage[preset.packageName].orEmpty(),
                isEnabled = isPresetEnabled(preset.packageName),
            )
        }

        val presetPackages = presets.mapTo(mutableSetOf(), NotificationSourcePreset::packageName)
        val templateSources = templatesByPackage
            .filterKeys { it !in presetPackages }
            .map { (packageName, packageTemplates) ->
                Source(
                    packageName = packageName,
                    preset = null,
                    templates = packageTemplates,
                    isEnabled = packageTemplates.any(NotificationTemplate::isEnabled),
                )
            }

        return presetSources + templateSources
    }

    companion object {
        private val templateOrder: Comparator<NotificationTemplate> =
            compareBy(NotificationTemplate::createdAt, NotificationTemplate::id)

        /**
         * Parses with the [preset] first, then with the enabled [templates]
         * by creation time, then by ID; the first payment wins. Never throws.
         *
         * @return the payment, or [ParsedBankNotification.Unrecognized] if nothing matched
         */
        fun parseWith(
            preset: NotificationSourcePreset?,
            templates: List<NotificationTemplate>,
            title: String?,
            text: String,
        ): ParsedBankNotification {
            val presetResult = preset?.let {
                try {
                    it.parse(title, text)
                } catch (e: Exception) {
                    null
                }
            }
            if (presetResult is ParsedBankNotification.Payment) {
                return presetResult
            }

            return templates
                .filter(NotificationTemplate::isEnabled)
                .sortedWith(templateOrder)
                .firstNotNullOfOrNull { TemplateMatcher.match(it, title, text) }
                ?: ParsedBankNotification.Unrecognized
        }
    }
}
