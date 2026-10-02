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

package ua.com.radiokot.money.uikit

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text emitted by view models, resolved to a string
 * in the UI with the current app language.
 */
@Immutable
sealed interface ViewText {

    /**
     * Text that must not be translated, e.g. user data.
     */
    data class Plain(val text: String) : ViewText

    /**
     * String resource. [args] being [ViewText] are resolved recursively.
     */
    data class Res(
        @StringRes
        val id: Int,
        val args: List<Any> = emptyList(),
    ) : ViewText

    /**
     * Quantity string resource.
     * [args] being [ViewText] are resolved recursively.
     */
    data class Plural(
        @PluralsRes
        val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : ViewText
}

@Composable
fun ViewText.resolve(): String {
    // Reading the configuration makes the text recompose on a language change.
    LocalConfiguration.current
    return resolve(LocalContext.current)
}

fun ViewText.resolve(context: Context): String = when (this) {
    is ViewText.Plain ->
        text

    is ViewText.Res ->
        context.getString(id, *resolveArgs(context))

    is ViewText.Plural ->
        context.resources.getQuantityString(id, count, *resolveArgs(context))
}

private fun ViewText.Res.resolveArgs(context: Context): Array<Any> =
    args.resolveViewTexts(context)

private fun ViewText.Plural.resolveArgs(context: Context): Array<Any> =
    args.resolveViewTexts(context)

private fun List<Any>.resolveViewTexts(context: Context): Array<Any> =
    map { arg ->
        if (arg is ViewText) arg.resolve(context) else arg
    }.toTypedArray()
