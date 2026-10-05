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

import androidx.annotation.StringRes
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.data.SourceStats
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourcePreset
import ua.com.radiokot.money.inbox.sources.logic.NotificationSourceRegistry
import ua.com.radiokot.money.uikit.ViewText

/**
 * The texts of a source card of the Sources screen.
 *
 * @param kindChips the preset kinds localized, then the template names
 * @param meta the line under the app name
 * @param recognized the recognized this month line, null if nothing was recognized
 * @param teachActionText the label of the tonal action under the chips
 */
data class SourceCardTexts(
    val kindChips: List<ViewText>,
    val meta: ViewText,
    val recognized: ViewText?,
    val teachActionText: ViewText,
) {
    companion object {
        /**
         * @param formatLastReceived the localized "today, 09:12" style time
         */
        fun of(
            source: NotificationSourceRegistry.Source,
            stats: SourceStats?,
            formatLastReceived: (LocalDateTime) -> String,
        ): SourceCardTexts {
            val kindChips: List<ViewText> =
                source.preset?.kinds.orEmpty().map { ViewText.Res(kindLabel(it)) } +
                        source.templates.map { ViewText.Plain(it.name) }

            val lastReceivedAt = stats?.lastReceivedAt

            return SourceCardTexts(
                kindChips = kindChips,
                meta = when {
                    kindChips.isEmpty() ->
                        ViewText.Res(R.string.sources_meta_no_kinds)

                    lastReceivedAt != null ->
                        ViewText.Res(
                            R.string.sources_meta_last,
                            listOf(ViewText.Plain(formatLastReceived(lastReceivedAt))),
                        )

                    else ->
                        ViewText.Res(R.string.sources_meta_nothing_yet)
                },
                recognized = stats?.recognizedCount
                    ?.takeIf { it > 0 }
                    ?.let { ViewText.Plural(R.plurals.sources_recognized_month, it) },
                teachActionText = ViewText.Res(
                    if (kindChips.isEmpty())
                        R.string.sources_set_up
                    else
                        R.string.sources_teach_another
                ),
            )
        }

        @StringRes
        fun kindLabel(kind: NotificationSourcePreset.Kind): Int = when (kind) {
            NotificationSourcePreset.Kind.CardPayment -> R.string.sources_kind_card_payment
            NotificationSourcePreset.Kind.AccountPayment -> R.string.sources_kind_account_payment
            NotificationSourcePreset.Kind.IncomingPayment -> R.string.sources_kind_incoming_payment
        }
    }
}
