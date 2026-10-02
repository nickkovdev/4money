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

package ua.com.radiokot.money.categories.view

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.composeunstyled.Icon
import ua.com.radiokot.money.R
import ua.com.radiokot.money.colors.view.EditableItemLogo
import ua.com.radiokot.money.uikit.FieldLabel
import ua.com.radiokot.money.uikit.GroupPosition
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyPickerField
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.listGroupItem
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
private fun EditCategoryScreen(
    isNewCategory: Boolean,
    isIncome: Boolean,
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
    subcategoryItemList: State<List<ViewSubcategoryToUpdateListItem>>,
    isSubcategoryEditEnabled: State<Boolean>,
    onSubcategoryItemClicked: (ViewSubcategoryToUpdateListItem) -> Unit,
    onSubcategoryItemMoved: suspend (fromIndex: Int, toIndex: Int) -> Unit,
    onAddSubcategoryClicked: () -> Unit,
    isArchived: State<Boolean>,
    isArchivedVisible: Boolean,
    onArchivedClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .add(WindowInsets.statusBars)
        )
) {
    ScreenTopBar(
        title =
            stringResource(
                when {
                    isNewCategory && isIncome -> R.string.categories_new_income
                    isNewCategory -> R.string.categories_new_expense
                    isIncome -> R.string.categories_edit_income
                    else -> R.string.categories_edit_expense
                }
            ),
        onNavigationClicked = onCloseClicked,
    )

    val subcategoryListState = rememberLazyListState()
    val subcategoryReorderableState = rememberReorderableLazyListState(
        lazyListState = subcategoryListState,
        onMove = { from, to ->
            // 1 is subtracted because the first item in the column
            // is the section with title and currency.
            onSubcategoryItemMoved(
                from.index - 1,
                to.index - 1,
            )
        },
    )
    val subcategories = subcategoryItemList.value
    // Subcategories and the add row form one group.
    val groupSize = subcategories.size + 1
    val isSubcategoryEditable = isSubcategoryEditEnabled.value

    LazyColumn(
        state = subcategoryListState,
        contentPadding = PaddingValues(
            start = MoneySpacing.screen,
            end = MoneySpacing.screen,
            bottom = 16.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .imePadding()
    ) {
        item {
            Column {
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
                    placeholder = stringResource(R.string.categories_field_title_placeholder),
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

                SectionHeader(
                    title = stringResource(R.string.categories_subcategories),
                    modifier = Modifier
                        .padding(top = 16.dp)
                )
            }
        }

        itemsIndexed(
            items = subcategories,
            key = { _, item -> item.key },
        ) { index, item ->
            ReorderableItem(
                state = subcategoryReorderableState,
                key = item.key,
            ) { isDragging ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .graphicsLayer {
                            alpha =
                                if (isDragging)
                                    0.8f
                                else
                                    1f
                        }
                        .listGroupItem(
                            position =
                                if (isDragging)
                                    GroupPosition.Single
                                else
                                    GroupPosition.of(index, groupSize) { true },
                            dividerStartInset = 16.dp + 18.dp + 12.dp,
                        )
                        .clickable(
                            enabled = isSubcategoryEditable,
                            onClick = {
                                onSubcategoryItemClicked(item)
                            },
                        )
                        .longPressDraggableHandle(
                            enabled = isSubcategoryEditable,
                        )
                        .padding(
                            horizontal = MoneySpacing.rowHorizontal,
                            vertical = 14.dp,
                        )
                        .alpha(if (isSubcategoryEditable) 1f else 0.5f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_grip_vertical),
                        contentDescription = stringResource(R.string.categories_hold_to_move),
                        tint = MoneyTheme.colors.ink3,
                        modifier = Modifier
                            .size(18.dp)
                    )

                    Text(
                        text = item.title,
                        style = MoneyTheme.typography.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                    )

                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_pencil),
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MoneyTheme.colors.ink3,
                        modifier = Modifier
                            .size(16.dp)
                    )
                }
            }
        }

        item(
            key = "add-subcategory",
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .listGroupItem(
                        position = GroupPosition.of(groupSize - 1, groupSize) { true },
                        dividerStartInset = 16.dp + 18.dp + 12.dp,
                    )
                    .clickable(
                        enabled = isSubcategoryEditable,
                        onClick = onAddSubcategoryClicked,
                    )
                    .padding(
                        horizontal = MoneySpacing.rowHorizontal,
                        vertical = 14.dp,
                    )
                    .alpha(if (isSubcategoryEditable) 1f else 0.5f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_plus),
                    contentDescription = null,
                    tint = MoneyTheme.colors.accent,
                    modifier = Modifier
                        .size(18.dp)
                )

                Text(
                    text = stringResource(R.string.categories_add_subcategory),
                    style = MoneyTheme.typography.label,
                    color = MoneyTheme.colors.accent,
                    modifier = Modifier
                        .weight(1f)
                )
            }
        }

        if (isArchivedVisible) {
            item(
                key = "archived"
            ) {
                ListGroup(
                    modifier = Modifier
                        .padding(top = 16.dp)
                ) {
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
        }
    }

    MoneyButton(
        text = stringResource(R.string.common_save),
        style = MoneyButtonStyle.Filled,
        isEnabled = isSaveEnabled.value,
        onClick = onSaveClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 12.dp,
            )
    )
}

@Composable
fun EditCategoryScreenRoot(
    viewModel: EditCategoryScreenViewModel,
) {
    EditCategoryScreen(
        isNewCategory = viewModel.isNewCategory,
        isIncome = viewModel.isIncome,
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
        subcategoryItemList = viewModel.subcategories.collectAsState(),
        isSubcategoryEditEnabled = viewModel.isSubcategoryEditEnabled.collectAsState(),
        onSubcategoryItemClicked = remember { viewModel::onSubcategoryItemClicked },
        onSubcategoryItemMoved = remember { viewModel::onSubcategoryItemMoved },
        onAddSubcategoryClicked = remember { viewModel::onAddSubcategoryClicked },
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
private fun Preview(

) {
    val isArchived = remember { mutableStateOf(false) }
    val isSubcategoryEditEnabled = remember {
        derivedStateOf {
            !isArchived.value
        }
    }

    EditCategoryScreen(
        isNewCategory = true,
        isIncome = false,
        isSaveEnabled = false.let(::mutableStateOf),
        onSaveClicked = {},
        title = "Hobbies".let(::mutableStateOf),
        onTitleChanged = {},
        colorScheme = HardcodedItemColorSchemeRepository()
            .getItemColorSchemesByName()
            .getValue("Pink3")
            .let(::mutableStateOf),
        icon = null.let(::mutableStateOf),
        onLogoClicked = {},
        currencyCode = "USD".let(::mutableStateOf),
        isCurrencyChangeEnabled = true,
        onCurrencyClicked = {},
        subcategoryItemList =
            listOf(
                ViewSubcategoryToUpdateListItem(
                    title = "Koshka",
                    source = null,
                ),
                ViewSubcategoryToUpdateListItem(
                    title = "Kartoshka",
                    source = null,
                )
            ).let(::mutableStateOf),
        isSubcategoryEditEnabled = isSubcategoryEditEnabled,
        onSubcategoryItemClicked = {},
        onSubcategoryItemMoved = { _, _ -> },
        onAddSubcategoryClicked = {},
        isArchived = isArchived,
        isArchivedVisible = true,
        onArchivedClicked = { isArchived.value = !isArchived.value },
        onCloseClicked = {},
    )
}
