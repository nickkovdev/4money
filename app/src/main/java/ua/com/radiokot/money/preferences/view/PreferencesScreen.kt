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

package ua.com.radiokot.money.preferences.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.preferences.logic.AppLanguage
import ua.com.radiokot.money.theme.data.ThemeMode
import ua.com.radiokot.money.theme.view.moneyColorsOf
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.ListDivider
import ua.com.radiokot.money.uikit.ListGroup
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.RowChevron
import ua.com.radiokot.money.uikit.SectionHeader
import ua.com.radiokot.money.uikit.SelectionMark
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import androidx.compose.ui.res.pluralStringResource

@Composable
private fun PreferencesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    themeMode: State<ThemeMode>,
    onThemeModeClicked: (ThemeMode) -> Unit,
    language: State<AppLanguage>,
    onLanguageClicked: (AppLanguage) -> Unit,
    primaryCurrencyCode: State<String>,
    onPrimaryCurrencyCodeChanged: (String) -> Unit,
    isSaveCurrencyPreferencesEnabled: State<Boolean>,
    onSaveCurrencyPreferencesClicked: () -> Unit,
    isAppLockEnabled: State<Boolean>,
    onAppLockClicked: () -> Unit,
    userId: State<String>,
    onSignOutClicked: () -> Unit,
    isSyncErrorsNoticeVisible: State<Boolean>,
    isNotificationAccessGranted: State<Boolean>,
    onNotificationAccessClicked: () -> Unit,
) = Column(
    modifier = modifier
) {
    val colors = MoneyTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MoneySpacing.screen,
                vertical = 12.dp,
            )
    ) {
        MoneyIconButton(
            icon = R.drawable.ic_tabler_arrow_left,
            contentDescription = stringResource(R.string.common_back),
            onClick = onBack,
        )

        Text(
            text = stringResource(R.string.settings_title),
            style = MoneyTheme.typography.headline,
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = MoneySpacing.screen,
            )
            .padding(
                bottom = 32.dp,
            )
    ) {
        if (isSyncErrorsNoticeVisible.value) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MoneyShapes.large)
                    .background(colors.expenseTint)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_alert_triangle),
                    contentDescription = null,
                    tint = colors.expense,
                    modifier = Modifier
                        .size(22.dp)
                )
                Text(
                    text = stringResource(R.string.settings_sync_error),
                    style = MoneyTheme.typography.labelRegular,
                    color = colors.ink,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        SectionHeader(title = stringResource(R.string.settings_appearance))

        ListGroup {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) {
                    ListDivider(startInset = 16.dp + 36.dp + 14.dp)
                }

                ListRow(
                    title = stringResource(
                        when (mode) {
                            ThemeMode.System -> R.string.settings_language_system
                            ThemeMode.Light -> R.string.settings_theme_paper
                            ThemeMode.Dark -> R.string.settings_theme_midnight
                            ThemeMode.Ember -> R.string.settings_theme_ember
                            ThemeMode.Aurora -> R.string.settings_theme_aurora
                        }
                    ),
                    subtitle = stringResource(
                        when (mode) {
                            ThemeMode.System -> R.string.settings_theme_system_subtitle
                            ThemeMode.Light -> R.string.settings_theme_paper_subtitle
                            ThemeMode.Dark -> R.string.settings_theme_midnight_subtitle
                            ThemeMode.Ember -> R.string.settings_theme_ember_subtitle
                            ThemeMode.Aurora -> R.string.settings_theme_aurora_subtitle
                        }
                    ),
                    leading = {
                        PaletteSwatch(
                            mode = mode,
                        )
                    },
                    trailing = {
                        SelectionMark(
                            isSelected = themeMode.value == mode,
                        )
                    },
                    onClick = { onThemeModeClicked(mode) },
                )
            }
        }

        SectionHeader(
            title = stringResource(R.string.settings_language),
            modifier = Modifier
                .padding(top = MoneySpacing.section)
        )

        ListGroup {
            AppLanguage.entries.forEachIndexed { index, appLanguage ->
                if (index > 0) {
                    ListDivider(startInset = MoneySpacing.rowHorizontal)
                }

                ListRow(
                    title = stringResource(
                        when (appLanguage) {
                            AppLanguage.System -> R.string.settings_language_system
                            AppLanguage.English -> R.string.language_english
                            AppLanguage.Russian -> R.string.language_russian
                        }
                    ),
                    trailing = {
                        SelectionMark(
                            isSelected = language.value == appLanguage,
                        )
                    },
                    onClick = { onLanguageClicked(appLanguage) },
                )
            }
        }

        SectionHeader(
            title = stringResource(R.string.settings_bank_notifications),
            modifier = Modifier
                .padding(top = MoneySpacing.section)
        )

        ListGroup {
            ListRow(
                title = stringResource(R.string.settings_notification_access),
                subtitle =
                    if (isNotificationAccessGranted.value)
                        stringResource(R.string.settings_notification_access_granted)
                    else
                        stringResource(R.string.settings_notification_access_hint),
                leading = {
                    IconTile(icon = R.drawable.ic_tabler_bell)
                },
                trailing =
                    if (isNotificationAccessGranted.value) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_tabler_circle_check),
                                contentDescription = stringResource(R.string.settings_granted),
                                tint = colors.income,
                                modifier = Modifier
                                    .size(22.dp)
                            )
                        }
                    } else {
                        {
                            MoneyButton(
                                text = stringResource(R.string.settings_allow),
                                style = MoneyButtonStyle.Filled,
                                onClick = onNotificationAccessClicked,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 8.dp,
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                            )
                        }
                    },
                onClick =
                    if (isNotificationAccessGranted.value)
                        null
                    else
                        onNotificationAccessClicked,
            )
        }

        SectionHeader(
            title = stringResource(R.string.settings_currency),
            modifier = Modifier
                .padding(top = MoneySpacing.section)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoneyTextField(
                value = primaryCurrencyCode.value,
                onValueChange = onPrimaryCurrencyCodeChanged,
                placeholder = stringResource(R.string.settings_currency_placeholder),
                leadingIcon = R.drawable.ic_tabler_currency_euro,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isSaveCurrencyPreferencesEnabled.value) {
                            onSaveCurrencyPreferencesClicked()
                        }
                    },
                ),
                trailing = {
                    Text(
                        text = stringResource(R.string.settings_currency_primary),
                        style = MoneyTheme.typography.caption,
                        color = colors.ink3,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
            )

            // Only offered when there is something to save.
            if (isSaveCurrencyPreferencesEnabled.value) {
                MoneyButton(
                    text = stringResource(R.string.settings_currency_save),
                    style = MoneyButtonStyle.Filled,
                    onClick = onSaveCurrencyPreferencesClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }

        SectionHeader(
            title = stringResource(R.string.settings_security),
            modifier = Modifier
                .padding(top = MoneySpacing.section)
        )

        ListGroup {
            ListRow(
                title = stringResource(R.string.settings_passcode_lock),
                subtitle = stringResource(R.string.settings_passcode_lock_subtitle),
                leading = {
                    IconTile(icon = R.drawable.ic_tabler_lock)
                },
                trailing = {
                    MoneySwitch(
                        isOn = isAppLockEnabled.value,
                        onToggled = null,
                    )
                },
                onClick = onAppLockClicked,
            )
        }

        SectionHeader(
            title = stringResource(R.string.settings_account),
            modifier = Modifier
                .padding(top = MoneySpacing.section)
        )

        val clipboardManager = LocalClipboardManager.current

        ListGroup {
            ListRow(
                title = stringResource(R.string.settings_user_id),
                subtitle = shortUserId(userId.value),
                leading = {
                    IconTile(
                        icon = R.drawable.ic_tabler_id,
                        tint = colors.ink2,
                        background = colors.surface2,
                    )
                },
                trailing = {
                    Icon(
                        painter = painterResource(R.drawable.ic_tabler_copy),
                        contentDescription = stringResource(R.string.settings_copy),
                        tint = colors.ink3,
                        modifier = Modifier
                            .size(18.dp)
                    )
                },
                onClick = {
                    clipboardManager.setText(AnnotatedString(userId.value))
                },
            )

            ListDivider(startInset = 16.dp + 36.dp + 14.dp)

            ListRow(
                title = stringResource(R.string.settings_sign_out),
                titleColor = colors.expense,
                leading = {
                    IconTile(
                        icon = R.drawable.ic_tabler_logout,
                        tint = colors.expense,
                        background = colors.expenseTint,
                    )
                },
                onClick = onSignOutClicked,
            )
        }
    }
}

private fun shortUserId(userId: String): String =
    if (userId.length > 16)
        userId.take(8) + "…" + userId.takeLast(4)
    else
        userId

/**
 * Two dots of the palette: its ground with the accent on top.
 */
@Composable
private fun PaletteSwatch(
    mode: ThemeMode,
) {
    val left: MoneyColors
    val right: MoneyColors
    if (mode == ThemeMode.System) {
        left = moneyColorsOf(ThemeMode.Light, isSystemDark = false)
        right = moneyColorsOf(ThemeMode.Dark, isSystemDark = true)
    } else {
        left = moneyColorsOf(mode, isSystemDark = false)
        right = left
    }

    Box(
        modifier = Modifier
            .size(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .background(
                    color = left.background,
                    shape = MoneyShapes.circle,
                )
                .border(
                    width = 1.dp,
                    color = MoneyTheme.colors.line,
                    shape = MoneyShapes.circle,
                )
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = left.accent,
                        shape = MoneyShapes.circle,
                    )
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(x = 10.dp, y = 10.dp)
                .size(26.dp)
                .background(
                    color = right.background,
                    shape = MoneyShapes.circle,
                )
                .border(
                    width = 1.dp,
                    color = MoneyTheme.colors.line,
                    shape = MoneyShapes.circle,
                )
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = right.accent,
                        shape = MoneyShapes.circle,
                    )
            )
        }
    }
}

@Composable
fun PreferencesScreen(
    modifier: Modifier = Modifier,
    viewModel: PreferencesScreenViewModel,
    onBack: () -> Unit,
) = PreferencesScreen(
    modifier = modifier,
    onBack = onBack,
    themeMode = viewModel.themeMode.collectAsState(),
    onThemeModeClicked = remember { viewModel::onThemeModeClicked },
    language = viewModel.language.collectAsState(),
    onLanguageClicked = remember { viewModel::onLanguageClicked },
    primaryCurrencyCode = viewModel.primaryCurrencyCodeValue.collectAsState(),
    onPrimaryCurrencyCodeChanged = remember { viewModel::onPrimaryCurrencyCodeChanged },
    isSaveCurrencyPreferencesEnabled = viewModel.isSaveCurrencyPreferencesEnabled.collectAsState(),
    onSaveCurrencyPreferencesClicked = remember { viewModel::onSaveCurrencyPreferencesClicked },
    onSignOutClicked = remember { viewModel::onSignOutClicked },
    userId = viewModel.userId.collectAsState(),
    isSyncErrorsNoticeVisible = viewModel.isSyncErrorsNoticeVisible.collectAsState(),
    isAppLockEnabled = viewModel.isAppLockEnabled.collectAsState(),
    onAppLockClicked = remember { viewModel::onAppLockClicked },
    isNotificationAccessGranted = viewModel.isNotificationAccessGranted.collectAsState(),
    onNotificationAccessClicked = remember { viewModel::onNotificationAccessClicked },
)

@Preview(
    apiLevel = 34,
    heightDp = 1400,
)
@Composable
private fun PreferencesScreenPreview(
) = MoneyTheme(colors = MidnightMoneyColors) {
    PreferencesScreen(
        modifier = Modifier
            .background(MoneyTheme.colors.background),
        onBack = {},
        themeMode = ThemeMode.System.let(::mutableStateOf),
        onThemeModeClicked = {},
        language = AppLanguage.System.let(::mutableStateOf),
        onLanguageClicked = {},
        primaryCurrencyCode = "USD".let(::mutableStateOf),
        onPrimaryCurrencyCodeChanged = {},
        isSaveCurrencyPreferencesEnabled = true.let(::mutableStateOf),
        onSaveCurrencyPreferencesClicked = {},
        isAppLockEnabled = true.let(::mutableStateOf),
        onAppLockClicked = {},
        userId = "8c1a2f3e-0000-4000-8000-1234567890ab".let(::mutableStateOf),
        onSignOutClicked = {},
        isSyncErrorsNoticeVisible = true.let(::mutableStateOf),
        isNotificationAccessGranted = false.let(::mutableStateOf),
        onNotificationAccessClicked = {},
    )
}
