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
import kotlinx.coroutines.flow.stateIn
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.sources.data.AppInfoSource
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry

/**
 * Tries a pasted notification on all the sources. The texts are never logged or stored.
 */
class TestTextScreenViewModel(
    registry: NotificationSourceRegistry,
    private val appInfoSource: AppInfoSource,
) : ViewModel() {

    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    val outcome: StateFlow<TestTextRunner.Outcome> =
        combine(
            title,
            text,
            registry.sourcesFlow,
        ) { title, text, sources ->
            TestTextRunner.run(
                sources = sources,
                title = title,
                text = text,
                labelOf = { packageName ->
                    appInfoSource.getLabel(packageName) ?: packageName
                },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = TestTextRunner.Outcome(emptyList(), emptyList()),
            )

    fun onTitleChanged(newValue: String) {
        _title.value = newValue
    }

    fun onTextChanged(newValue: String) {
        _text.value = newValue
    }

    fun onBackClicked() {
        _events.tryEmit(Event.Close)
    }

    sealed interface Event {
        object Close : Event
    }
}
