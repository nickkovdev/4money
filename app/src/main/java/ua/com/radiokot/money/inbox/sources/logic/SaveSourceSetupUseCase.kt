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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.sources.data.AutoBookBehaviour
import ua.com.radiokot.money.inbox.sources.data.AutoBookPreferences
import ua.com.radiokot.money.inbox.sources.data.RecentNotificationBuffer
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplate
import ua.com.radiokot.money.inbox.templates.data.NotificationTemplateRepository

/**
 * Saves what the setup wizard has collected, on its last step. Nothing is written before.
 *
 * The templates go first: if they can't be saved, the exception is thrown
 * and nothing else is touched, so the wizard can be finished again.
 *
 * Teaching a kind switches the whole source on: all the templates of the package
 * and, for a preset package, the preset. Otherwise teaching another kind
 * of a switched-off source would show it on while only the new kinds work.
 *
 * @param presetPackageNames packages of the built-in presets
 */
class SaveSourceSetupUseCase(
    private val templateRepository: NotificationTemplateRepository,
    private val cardAccountPreferences: CardAccountPreferences,
    private val autoBookPreferences: AutoBookPreferences,
    private val recentNotificationBuffer: RecentNotificationBuffer,
    private val presetPackageNames: Set<String> =
        BuiltInPresets.all.mapTo(mutableSetOf(), NotificationSourcePreset::packageName),
) {

    /**
     * @param templates the taught kinds, saved enabled under [packageName];
     * if any, the whole source is switched on
     * @param cardAccounts account IDs by card last 4 digits
     * @param sourceAccountId the account for payments of [packageName] without a card
     */
    class Request(
        val packageName: String,
        val templates: List<NotificationTemplate>,
        val cardAccounts: Map<String, String>,
        val sourceAccountId: String?,
        val behaviour: AutoBookBehaviour,
    )

    suspend operator fun invoke(request: Request) {
        if (request.templates.isNotEmpty()) {
            templateRepository.addTemplates(
                request.templates.map { template ->
                    template.copy(
                        sourcePackage = request.packageName,
                        isEnabled = true,
                    )
                }
            )
            templateRepository.setEnabledForPackage(request.packageName, true)
            if (request.packageName in presetPackageNames) {
                autoBookPreferences.setPresetEnabled(request.packageName, true)
            }
        }

        request.cardAccounts.forEach { (cardLast4, accountId) ->
            cardAccountPreferences.setAccountIdForCard(cardLast4, accountId)
        }
        if (request.sourceAccountId != null) {
            cardAccountPreferences.setAccountIdForSource(request.packageName, request.sourceAccountId)
        }

        autoBookPreferences.isRecordKnownPayeesEnabled = request.behaviour.recordKnownPayees
        autoBookPreferences.isAskInNotificationEnabled = request.behaviour.askInNotification
        autoBookPreferences.isLearnFromHistoryEnabled = request.behaviour.learnFromHistory

        // The samples are not needed any more.
        withContext(Dispatchers.IO) {
            recentNotificationBuffer.clear()
        }
    }
}
