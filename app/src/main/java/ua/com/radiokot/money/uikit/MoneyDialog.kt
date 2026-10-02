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

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Dialog
import com.composeunstyled.DialogPanel
import com.composeunstyled.DialogProperties
import com.composeunstyled.Scrim
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A themed modal dialog shown while it is in the composition.
 * Tapping outside or BACK calls [onDismissRequest].
 */
@Composable
fun MoneyDialogContainer(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dialogState = rememberDialogState(
        initiallyVisible = true,
    )

    Dialog(
        state = dialogState,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        onDismiss = onDismissRequest,
    ) {
        Scrim(
            scrimColor = MoneyTheme.colors.scrim,
            enter = fadeIn(),
            exit = fadeOut(),
        )

        DialogPanel(
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
            shape = MoneyShapes.extraLarge,
            backgroundColor = MoneyTheme.colors.surface2,
            contentColor = MoneyTheme.colors.ink,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * A confirmation dialog: title, explanation, a tonal dismiss and a filled
 * (or danger, if [isDestructive]) confirm button. Buttons are never ALL-CAPS.
 */
@Composable
fun MoneyDialog(
    title: String,
    text: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "Cancel",
    onDismissRequest: () -> Unit,
    isDestructive: Boolean = false,
) = MoneyDialogContainer(
    onDismissRequest = onDismissRequest,
) {
    MoneyDialogContent(
        title = title,
        text = text,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        onDismiss = onDismissRequest,
        isDestructive = isDestructive,
    )
}

@Composable
private fun MoneyDialogContent(
    title: String,
    text: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    isDestructive: Boolean,
) = Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
        .padding(24.dp)
) {
    Text(
        text = title,
        style = MoneyTheme.typography.title,
    )

    if (text != null) {
        Text(
            text = text,
            style = MoneyTheme.typography.body,
            color = MoneyTheme.colors.ink2,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        MoneyButton(
            text = dismissText,
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
        )
        MoneyButton(
            text = confirmText,
            onClick = onConfirm,
            style =
                if (isDestructive)
                    MoneyButtonStyle.Danger
                else
                    MoneyButtonStyle.Filled,
            modifier = Modifier
                .weight(1f)
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun DialogPaperPreview() = MoneyTheme {
    Column(
        modifier = Modifier
            .background(MoneyTheme.colors.surface2)
    ) {
        MoneyDialogContent(
            title = "Revert the transfer?",
            text = "The amount goes back to the account balances.",
            confirmText = "Revert",
            onConfirm = {},
            dismissText = "Cancel",
            onDismiss = {},
            isDestructive = true,
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun DialogMidnightPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    Column(
        modifier = Modifier
            .background(MoneyTheme.colors.surface2)
    ) {
        MoneyDialogContent(
            title = "Sign out?",
            text = "Unsynced changes will be lost.",
            confirmText = "Sign out",
            onConfirm = {},
            dismissText = "Cancel",
            onDismiss = {},
            isDestructive = false,
        )
    }
}
