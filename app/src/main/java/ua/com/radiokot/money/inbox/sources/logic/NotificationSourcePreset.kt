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

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.logic.SebLatviaNotificationParser

/**
 * A notification source built into the app: a bank whose texts are parsed by code,
 * not by user templates. Switched on and off in device-local preferences.
 */
interface NotificationSourcePreset {

    val packageName: String

    /**
     * The payment kinds the preset recognizes, shown as chips in the sources list.
     */
    val kinds: List<Kind>

    /**
     * Must never throw: anything not recognized is [ParsedBankNotification.Unrecognized].
     */
    fun parse(title: String?, text: String): ParsedBankNotification

    enum class Kind {
        CardPayment,
        AccountPayment,
        IncomingPayment,
        ;
    }
}

/**
 * SEB Latvia, parsed by [SebLatviaNotificationParser] (the text only).
 */
object SebLatviaPreset : NotificationSourcePreset {

    private val parser = SebLatviaNotificationParser()

    override val packageName: String = SebLatviaNotificationParser.PACKAGE_NAME

    override val kinds: List<NotificationSourcePreset.Kind> = listOf(
        NotificationSourcePreset.Kind.CardPayment,
        NotificationSourcePreset.Kind.AccountPayment,
        NotificationSourcePreset.Kind.IncomingPayment,
    )

    override fun parse(title: String?, text: String): ParsedBankNotification =
        parser.parse(title, text)
}

object BuiltInPresets {
    val all: List<NotificationSourcePreset> = listOf(
        SebLatviaPreset,
    )
}
