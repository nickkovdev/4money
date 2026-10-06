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

package ua.com.radiokot.money.inbox.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.logic.PayeeWordSelection
import ua.com.radiokot.money.uikit.MoneyChip
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Immutable
data class ViewRememberPayee(
    val words: List<String>,
    val first: Int,
    val last: Int,
    /**
     * «mcdonalds …»; null when the whole payee is selected.
     */
    val hintPattern: String?,
) {
    constructor(selection: PayeeWordSelection) : this(
        words = selection.words,
        first = selection.first,
        last = selection.last,
        hintPattern = selection.hintPattern,
    )
}

/**
 * "Remember for:" + word chips + hint; no switch (callers keep their switch).
 */
@Composable
fun RememberPayeeWords(
    remember: ViewRememberPayee,
    onWordClicked: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier,
) {
    Text(
        text = stringResource(R.string.remember_payee_for),
        style = MoneyTheme.typography.small,
        color = MoneyTheme.colors.ink3,
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        remember.words.forEachIndexed { index, word ->
            MoneyChip(
                text = word,
                isSelected = index in remember.first..remember.last,
                onClick = { onWordClicked(index) },
            )
        }
    }

    Text(
        text =
            if (remember.hintPattern != null)
                stringResource(R.string.remember_payee_contains_hint, remember.hintPattern)
            else
                stringResource(R.string.remember_payee_exact_hint),
        style = MoneyTheme.typography.small,
        color = MoneyTheme.colors.ink3,
    )
}

@Preview
@Composable
private fun RememberPayeeWordsPreview() = MoneyTheme {
    RememberPayeeWords(
        remember = ViewRememberPayee(
            PayeeWordSelection.leading("mcdonalds akropole rig", 1)!!
        ),
        onWordClicked = {},
    )
}
