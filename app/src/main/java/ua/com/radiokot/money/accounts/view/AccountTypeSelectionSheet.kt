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

import androidx.compose.ui.res.stringResource
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.ListRowTileDividerInset
import ua.com.radiokot.money.uikit.SelectionMark
import ua.com.radiokot.money.uikit.SheetScaffold
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun AccountTypeSelectionSheet(
    modifier: Modifier = Modifier,
    selectedType: Account.Type,
    onTypeClicked: (Account.Type) -> Unit,
) = SheetScaffold(
    title = stringResource(R.string.accounts_type_title),
    modifier = modifier,
) {
    ListGroup {
        Account.Type.entries.forEachIndexed { index, type ->
            if (index > 0) {
                ListDivider(startInset = ListRowTileDividerInset)
            }

            ListRow(
                title = stringResource(type.titleRes),
                subtitle = when (type) {
                    Account.Type.Regular -> stringResource(R.string.accounts_type_regular_hint)
                    Account.Type.Savings -> stringResource(R.string.accounts_type_savings_hint)
                },
                leading = {
                    IconTile(
                        icon = accountTypeIcon(type),
                        size = MoneySpacing.itemTile,
                    )
                },
                trailing = {
                    SelectionMark(
                        isSelected = type == selectedType,
                    )
                },
                onClick = { onTypeClicked(type) },
            )
        }
    }
}

@Preview(
    apiLevel = 34,
)
@Composable
private fun Preview(

) {
    AccountTypeSelectionSheet(
        selectedType = Account.Type.Savings,
        onTypeClicked = {},
    )
}
