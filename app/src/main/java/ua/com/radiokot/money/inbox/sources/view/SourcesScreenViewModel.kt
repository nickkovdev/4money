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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.data.SourceStats
import ua.com.radiokot.money.inbox.sources.data.AppInfoSource
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve
import kotlin.time.Clock

class SourcesScreenViewModel(
    private val registry: NotificationSourceRegistry,
    private val appInfoSource: AppInfoSource,
    private val recentNotificationBuffer: RecentNotificationBuffer,
    inboxRepository: InboxRepository,
    payeeRuleRepository: PayeeRuleRepository,
) : ViewModel() {

    private val log by lazyLogger("SourcesScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    private val _isAccessGranted = MutableStateFlow(false)
    val isAccessGranted = _isAccessGranted.asStateFlow()

    val sourceList: StateFlow<List<ViewSource>> =
        combine(
            registry.sourcesFlow,
            inboxRepository.getSourceStatsFlow(since = firstDayOfThisMonth()),
        ) { sources, statsByPackage ->
            sources.map { source ->
                ViewSource(
                    source = source,
                    label = appInfoSource.getLabel(source.packageName)
                        ?: source.packageName,
                    stats = statsByPackage[source.packageName],
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rulesSubtitle: StateFlow<ViewText> =
        payeeRuleRepository
            .getRulesFlow()
            .map { rules ->
                // Rules of one payee are shown as a single group.
                val payeeCount = rules
                    .mapTo(mutableSetOf()) { it.matchType to it.payeePattern }
                    .size
                val rangeCount = rules.count { it.amountRange != null }

                when {
                    payeeCount == 0 ->
                        ViewText.Res(R.string.sources_rules_none)

                    rangeCount == 0 ->
                        ViewText.Plural(R.plurals.sources_rules_count, payeeCount)

                    else ->
                        ViewText.Dynamic { context ->
                            listOf(
                                ViewText.Plural(R.plurals.sources_rules_count, payeeCount),
                                ViewText.Plural(R.plurals.sources_rules_by_amount, rangeCount),
                            ).joinToString(" · ") { it.resolve(context) }
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, ViewText.Res(R.string.sources_rules_none))

    /**
     * Called whenever the screen is resumed, e.g. back from the system settings.
     * Without the access, the recent notifications are not kept.
     */
    fun onResumed(isNotificationAccessGranted: Boolean) {
        _isAccessGranted.value = isNotificationAccessGranted

        if (!isNotificationAccessGranted) {
            viewModelScope.launch(Dispatchers.IO) {
                recentNotificationBuffer.clear()
            }
        }
    }

    fun onAccessClicked() {
        if (!_isAccessGranted.value) {
            _events.tryEmit(Event.ProceedToAccessSettings)
        }
    }

    fun onSourceSwitched(item: ViewSource, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                registry.setSourceEnabled(item.source.packageName, isEnabled)
            } catch (e: Exception) {
                log.error(e) {
                    "onSourceSwitched(): failed to switch:" +
                            "\npackageName=${item.source.packageName}"
                }
            }
        }
    }

    fun onTeachAnotherKindClicked(item: ViewSource) {
        _events.tryEmit(Event.ProceedToWizard(packageName = item.source.packageName))
    }

    fun onAddAppClicked() {
        _events.tryEmit(Event.ProceedToWizard(packageName = null))
    }

    fun onCardsClicked() {
        _events.tryEmit(Event.ProceedToCards)
    }

    fun onRulesClicked() {
        _events.tryEmit(Event.ProceedToRules)
    }

    fun onTestTextClicked() {
        _events.tryEmit(Event.ProceedToTestText)
    }

    fun onBackClicked() {
        _events.tryEmit(Event.Close)
    }

    private fun firstDayOfThisMonth(): LocalDateTime {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        return LocalDateTime(today.year, today.month, 1, 0, 0)
    }

    /**
     * @param stats null if there is no item of the source yet
     */
    class ViewSource(
        val source: NotificationSourceRegistry.Source,
        val label: String,
        val stats: SourceStats?,
    )

    sealed interface Event {

        object ProceedToAccessSettings : Event

        /**
         * @param packageName the source to teach, null to add a new app
         */
        class ProceedToWizard(val packageName: String?) : Event

        object ProceedToCards : Event

        object ProceedToRules : Event

        object ProceedToTestText : Event

        object Close : Event
    }
}
