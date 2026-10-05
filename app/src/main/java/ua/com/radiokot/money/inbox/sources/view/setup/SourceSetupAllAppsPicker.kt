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

package ua.com.radiokot.money.inbox.sources.view.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import ua.com.radiokot.money.R
import ua.com.radiokot.money.inbox.sources.data.AppInfo
import ua.com.radiokot.money.inbox.sources.view.AppIcon
import ua.com.radiokot.money.uikit.ListRow
import ua.com.radiokot.money.uikit.MoneyTextField
import ua.com.radiokot.money.uikit.ScreenTopBar
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme

/**
 * A full-screen list of the launchable apps with a search field.
 * The apps that are sources already are marked.
 *
 * @param apps null while loading
 */
@Composable
fun SourceSetupAllAppsPicker(
    apps: State<List<AppInfo>?>,
    sourcePackages: State<Set<String>>,
    onPicked: (AppInfo) -> Unit,
    onBackClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .fillMaxSize()
) {
    var query by remember { mutableStateOf("") }

    ScreenTopBar(
        title = stringResource(R.string.setup_apps_title),
        navigationIcon = R.drawable.ic_tabler_arrow_left,
        navigationContentDescription = stringResource(R.string.common_back),
        onNavigationClicked = onBackClicked,
    )

    MoneyTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.setup_apps_search),
        leadingIcon = R.drawable.ic_tabler_search,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MoneySpacing.screen)
    )

    val allApps = apps.value
    val filtered = remember(allApps, query) {
        allApps.orEmpty().filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    if (allApps != null && filtered.isEmpty()) {
        Text(
            text = stringResource(R.string.setup_apps_empty),
            style = MoneyTheme.typography.body,
            color = MoneyTheme.colors.ink3,
            modifier = Modifier
                .padding(MoneySpacing.screen)
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier
            .fillMaxSize()
    ) {
        items(filtered, key = AppInfo::packageName) { app ->
            ListRow(
                title = app.label,
                subtitle =
                    if (app.packageName in sourcePackages.value)
                        stringResource(R.string.setup_apps_already_source)
                    else
                        null,
                leading = {
                    AppIcon(
                        packageName = app.packageName,
                        label = app.label,
                    )
                },
                onClick = { onPicked(app) },
            )
        }
    }
}
