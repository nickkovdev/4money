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

package ua.com.radiokot.money.accounts.view

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.view.EditableItemLogo
import ua.com.radiokot.money.uikit.FieldLabel
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyPickerField
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
private fun EditAccountScreen(
    isNewAccount: Boolean,
    isSaveEnabled: State<Boolean>,
    onSaveClicked: () -> Unit,
    title: State<String>,
    onTitleChanged: (String) -> Unit,
    colorScheme: State<ItemColorScheme>,
    icon: State<ItemIcon?>,
    onLogoClicked: () -> Unit,
    currencyCode: State<String>,
    isCurrencyChangeEnabled: Boolean,
    onCurrencyClicked: () -> Unit,
    type: State<Account.Type>,
    isTypeChangeEnabled: State<Boolean>,
    onTypeClicked: () -> Unit,
    isArchived: State<Boolean>,
    isArchivedVisible: Boolean,
    onArchivedClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .only(WindowInsetsSides.Horizontal)
                .add(WindowInsets.statusBars)
        )
) {
    ScreenTopBar(
        title =
            if (isNewAccount)
                stringResource(R.string.accounts_new)
            else
                stringResource(R.string.accounts_edit),
        onNavigationClicked = onCloseClicked,
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = MoneySpacing.screen,
            )
    ) {
        EditableItemLogo(
            title = title.value,
            colorScheme = colorScheme.value,
            icon = icon.value,
            onClick = onLogoClicked,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        FieldLabel(text = stringResource(R.string.accounts_field_title))
        MoneyTextField(
            value = title.value,
            onValueChange = onTitleChanged,
            placeholder = stringResource(R.string.accounts_field_title_placeholder),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(text = stringResource(R.string.accounts_field_currency))
        MoneyPickerField(
            value = currencyCode.value,
            leadingIcon = R.drawable.ic_tabler_currency_euro,
            onClick =
                if (isCurrencyChangeEnabled)
                    onCurrencyClicked
                else
                    null,
            modifier = Modifier
                .fillMaxWidth()
        )
        if (!isCurrencyChangeEnabled) {
            Text(
                text = stringResource(R.string.accounts_currency_locked),
                style = MoneyTheme.typography.small,
                color = MoneyTheme.colors.ink3,
                modifier = Modifier
                    .padding(
                        start = 4.dp,
                        top = 6.dp,
                    )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(text = stringResource(R.string.accounts_field_type))
        MoneyPickerField(
            value = stringResource(type.value.titleRes),
            leadingIcon = accountTypeIcon(type.value),
            onClick =
                if (isTypeChangeEnabled.value)
                    onTypeClicked
                else
                    null,
            modifier = Modifier
                .fillMaxWidth()
        )

        if (isArchivedVisible) {
            Spacer(modifier = Modifier.height(16.dp))

            ListGroup {
                ListRow(
                    title = stringResource(R.string.accounts_field_archived),
                    subtitle = stringResource(R.string.accounts_field_archived_hint),
                    leading = {
                        IconTile(
                            icon = R.drawable.ic_tabler_archive,
                            tint = MoneyTheme.colors.ink2,
                            background = MoneyTheme.colors.surface2,
                        )
                    },
                    trailing = {
                        MoneySwitch(
                            isOn = isArchived.value,
                            onToggled = null,
                        )
                    },
                    onClick = onArchivedClicked,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    MoneyButton(
        text = stringResource(R.string.common_save),
        style = MoneyButtonStyle.Filled,
        isEnabled = isSaveEnabled.value,
        onClick = onSaveClicked,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 12.dp,
            )
    )
}

@Composable
fun EditAccountScreenRoot(
    viewModel: EditAccountScreenViewModel,
) {
    EditAccountScreen(
        isNewAccount = viewModel.isNewAccount,
        isSaveEnabled = viewModel.isSaveEnabled.collectAsState(),
        onSaveClicked = remember { viewModel::onSaveClicked },
        title = viewModel.title.collectAsState(),
        onTitleChanged = remember { viewModel::onTitleChanged },
        colorScheme = viewModel.colorScheme.collectAsState(),
        icon = viewModel.icon.collectAsState(),
        onLogoClicked = remember { viewModel::onLogoClicked },
        currencyCode = viewModel.currencyCode.collectAsState(),
        isCurrencyChangeEnabled = viewModel.isCurrencyChangeEnabled,
        onCurrencyClicked = remember { viewModel::onCurrencyClicked },
        type = viewModel.type.collectAsState(),
        isTypeChangeEnabled = viewModel.isTypeChangeEnabled.collectAsState(),
        onTypeClicked = remember { viewModel::onTypeClicked },
        isArchived = viewModel.isArchived.collectAsState(),
        isArchivedVisible = viewModel.isArchivedVisible,
        onArchivedClicked = remember { viewModel::onArchivedClicked },
        onCloseClicked = remember { viewModel::onCloseClicked },
    )
}

@Preview(
    apiLevel = 34,
)
@Composable
private fun EditAccountScreenPreview(

) {
    val isArchived = remember { mutableStateOf(false) }

    EditAccountScreen(
        isNewAccount = true,
        isSaveEnabled = false.let(::mutableStateOf),
        onSaveClicked = {},
        title = "Vault".let(::mutableStateOf),
        onTitleChanged = {},
        colorScheme = HardcodedItemColorSchemeRepository()
            .getItemColorSchemesByName()
            .getValue("Purple2")
            .let(::mutableStateOf),
        icon = null.let(::mutableStateOf),
        onLogoClicked = {},
        currencyCode = "PLN".let(::mutableStateOf),
        isCurrencyChangeEnabled = true,
        onCurrencyClicked = {},
        type = Account.Type.Savings.let(::mutableStateOf),
        isTypeChangeEnabled = true.let(::mutableStateOf),
        onTypeClicked = {},
        isArchived = isArchived,
        isArchivedVisible = true,
        onArchivedClicked = { isArchived.value = !isArchived.value },
        onCloseClicked = {},
    )
}

@get:StringRes
val Account.Type.titleRes: Int
    get() = when (this) {
        Account.Type.Regular -> R.string.accounts_type_regular
        Account.Type.Savings -> R.string.accounts_type_savings
    }

fun accountTypeIcon(type: Account.Type): Int =
    when (type) {
        Account.Type.Savings -> R.drawable.ic_tabler_pig_money
        else -> R.drawable.ic_tabler_wallet
    }
