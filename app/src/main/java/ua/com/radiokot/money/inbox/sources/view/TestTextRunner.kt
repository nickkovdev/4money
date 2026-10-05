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

import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.uikit.ViewText

/**
 * Tries a pasted notification on every source: the preset as a whole
 * and each template on its own, disabled ones too. Pure, nothing is saved.
 */
object TestTextRunner {

    class Match(
        val sourceLabel: String,
        val kind: ViewText,
        val payment: ParsedBankNotification.Payment,
    )

    class NoMatch(
        val sourceLabel: String,
        val kind: ViewText,
    )

    class Outcome(
        val matches: List<Match>,
        val noMatch: List<NoMatch>,
    ) {
        val isEmpty: Boolean
            get() = matches.isEmpty() && noMatch.isEmpty()
    }

    private val emptyOutcome = Outcome(emptyList(), emptyList())

    fun run(
        sources: List<NotificationSourceRegistry.Source>,
        title: String?,
        text: String,
        labelOf: (packageName: String) -> String,
    ): Outcome {
        if (text.isBlank()) {
            return emptyOutcome
        }

        val titleOrNull = title?.takeIf(String::isNotBlank)
        val matches = mutableListOf<Match>()
        val noMatch = mutableListOf<NoMatch>()

        fun record(
            sourceLabel: String,
            kind: ViewText,
            result: ParsedBankNotification,
        ) {
            if (result is ParsedBankNotification.Payment) {
                matches += Match(sourceLabel, kind, result)
            } else {
                noMatch += NoMatch(sourceLabel, kind)
            }
        }

        sources.forEach { source ->
            val sourceLabel = labelOf(source.packageName)

            if (source.preset != null) {
                record(
                    sourceLabel = sourceLabel,
                    kind = ViewText.Res(R.string.test_builtin),
                    result = NotificationSourceRegistry.parseWith(
                        preset = source.preset,
                        templates = emptyList(),
                        title = titleOrNull,
                        text = text,
                    ),
                )
            }

            source.templates.forEach { template ->
                record(
                    sourceLabel = sourceLabel,
                    kind = ViewText.Plain(template.name),
                    result = NotificationSourceRegistry.parseWith(
                        preset = null,
                        // A switched off template is still worth testing.
                        templates = listOf(template.copy(isEnabled = true)),
                        title = titleOrNull,
                        text = text,
                    ),
                )
            }
        }

        return Outcome(matches, noMatch)
    }
}
