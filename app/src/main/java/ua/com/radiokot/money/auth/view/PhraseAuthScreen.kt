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

package ua.com.radiokot.money.auth.view

import androidx.compose.foundation.background
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import androidx.compose.ui.res.stringResource

@Composable
private fun PhraseAuthScreen(
    modifier: Modifier = Modifier,
    phrase: State<String>,
    onPhraseChanged: (String) -> Unit,
    isSignInEnabled: State<Boolean>,
    onSignInClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .only(WindowInsetsSides.Horizontal)
                .add(WindowInsets.statusBars)
        )
        .scrollable(
            state = rememberScrollState(),
            orientation = Orientation.Vertical,
        )
        .padding(
            horizontal = 16.dp,
        )
) {
    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
    ) {
        MoneyIconButton(
            icon = R.drawable.ic_tabler_x,
            contentDescription = stringResource(R.string.common_close),
            onClick = onCloseClicked,
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.auth_recovery_phrase),
        style = MoneyTheme.typography.headline,
    )

    Spacer(modifier = Modifier.height(6.dp))

    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    BasicTextField(
        value = phrase.value,
        onValueChange = onPhraseChanged,
        textStyle = MoneyTheme.typography.body.copy(color = MoneyTheme.colors.ink),
        cursorBrush = SolidColor(MoneyTheme.colors.accent),
        singleLine = false,
        minLines = 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                softwareKeyboardController?.hide()
                if (isSignInEnabled.value) {
                    onSignInClicked()
                }
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MoneyTheme.colors.surface,
                shape = MoneyShapes.medium,
            )
            .padding(14.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    MoneyButton(
        text = stringResource(R.string.auth_sign_in),
        style = MoneyButtonStyle.Filled,
        isEnabled = isSignInEnabled.value,
        onClick = onSignInClicked,
        modifier = Modifier
            .fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
fun PhraseAuthScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: PhraseAuthScreenViewModel,
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PhraseAuthScreenViewModel.Event.ShowAuthError ->
                    Toast
                        .makeText(
                            context,
                            ViewText.Res(R.string.auth_sign_in_failed, listOf(event.technicalReason))
                                .resolve(context),
                            Toast.LENGTH_LONG,
                        )
                        .show()

                PhraseAuthScreenViewModel.Event.Done,
                PhraseAuthScreenViewModel.Event.Close,
                -> {
                }
            }
        }
    }

    PhraseAuthScreen(
        phrase = viewModel.phrase.collectAsState(),
        onPhraseChanged = remember { viewModel::onPhraseChanged },
        isSignInEnabled = viewModel.isSignInEnabled.collectAsState(),
        onSignInClicked = remember { viewModel::onSignInClicked },
        onCloseClicked = remember { viewModel::onCloseClicked },
        modifier = modifier,
    )
}

@Preview(
    apiLevel = 34,
)
@Composable
private fun Preview(

) {
    PhraseAuthScreen(
        phrase = "tonight fat have keen intact happy social powder tired shaft length cram".let(::mutableStateOf),
        onPhraseChanged = {},
        isSignInEnabled = true.let(::mutableStateOf),
        onSignInClicked = {},
        onCloseClicked = {},
    )
}
