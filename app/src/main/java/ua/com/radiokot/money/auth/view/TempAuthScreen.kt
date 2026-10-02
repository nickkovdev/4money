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

import androidx.compose.foundation.layout.fillMaxWidth
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve

@Composable
private fun TempAuthScreen(
    onAuthenticateClicked: () -> Unit,
    onPhraseClicked: () -> Unit,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
        .safeDrawingPadding()
        .padding(
            vertical = 24.dp,
            horizontal = 16.dp,
        )
        .fillMaxSize()
) {
    Spacer(modifier = Modifier.weight(1f))

    Text(
        text = stringResource(R.string.app_name),
        style = MoneyTheme.typography.display,
    )

    Spacer(modifier = Modifier.weight(1f))

    MoneyButton(
        text = stringResource(R.string.auth_just_authenticate),
        style = MoneyButtonStyle.Filled,
        onClick = onAuthenticateClicked,
        modifier = Modifier
            .fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    MoneyButton(
        text = stringResource(R.string.auth_use_phrase),
        onClick = onPhraseClicked,
        modifier = Modifier
            .fillMaxWidth()
    )
}

@Composable
fun TempAuthScreenRoot(
    viewModel: TempAuthScreenViewModel,
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TempAuthScreenViewModel.Event.ShowAuthError ->
                    Toast
                        .makeText(
                            context,
                            ViewText.Res(R.string.auth_sign_in_failed, listOf(event.technicalReason))
                                .resolve(context),
                            Toast.LENGTH_LONG,
                        )
                        .show()

                TempAuthScreenViewModel.Event.Done,
                TempAuthScreenViewModel.Event.ProceedToPhraseAuth,
                -> {
                }
            }
        }
    }

    TempAuthScreen(
        onAuthenticateClicked = remember { viewModel::onAuthenticateClicked },
        onPhraseClicked = remember { viewModel::onPhraseClicked },
    )
}

@Composable
@Preview(
    apiLevel = 34,
)
private fun Preview() = TempAuthScreen(
    onAuthenticateClicked = {},
    onPhraseClicked = {},
)
