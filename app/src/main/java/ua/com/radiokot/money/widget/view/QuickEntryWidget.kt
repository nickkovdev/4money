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

package ua.com.radiokot.money.widget.view

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ua.com.radiokot.money.R
import ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION
import ua.com.radiokot.money.home.view.HomeActivity
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.theme.data.ThemeMode
import ua.com.radiokot.money.theme.data.ThemePreferences
import ua.com.radiokot.money.transfers.view.QuickTransferActivity
import ua.com.radiokot.money.transfers.view.QuickTransferDirection
import ua.com.radiokot.money.widget.logic.HomeWidgetBadge
import ua.com.radiokot.money.widget.logic.HomeWidgetColors
import ua.com.radiokot.money.widget.logic.HomeWidgetPalette

/**
 * Home screen widget with the income, the inbox and the expense buttons.
 */
class QuickEntryWidget : GlanceAppWidget(), KoinComponent {
    private val log by lazyLogger("QuickEntryWidget")

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The initial values, so the first frame is already right.
        val initialState = WidgetState(
            themeMode = get<ThemePreferences>().themeMode.value,
            pendingCount = readPendingCount(),
        )
        provideContent {
            // A running Glance session is not restarted by an update, only its state is
            // reloaded. The updater bumps this key, so the values are re-read on every update.
            val refresh = currentState(REFRESH_KEY)
            val state by produceState(initialState, refresh) {
                value = WidgetState(
                    themeMode = get<ThemePreferences>().themeMode.value,
                    pendingCount = readPendingCount(),
                )
            }
            QuickEntryWidgetContent(
                palette = HomeWidgetPalette.of(state.themeMode),
                pendingCount = state.pendingCount,
            )
        }
    }

    private class WidgetState(
        val themeMode: ThemeMode,
        val pendingCount: Long,
    )

    /** One COUNT query off the main thread; 0 without a session or on an error. */
    private suspend fun readPendingCount(): Long = withContext(Dispatchers.IO) {
        val sessionScope = getKoin().getScopeOrNull(DI_SCOPE_SESSION)
            ?.takeIf { it.isNotClosed() } ?: return@withContext 0L
        try {
            sessionScope.get<InboxRepository>().getPendingCountFlow().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "readPendingCount(): failed" }
            0L
        }
    }

    companion object {
        /** Bumped by the updater to make a running Glance session re-read the values. */
        internal val REFRESH_KEY = longPreferencesKey("refresh")
    }
}

@Composable
private fun QuickEntryWidgetContent(
    palette: HomeWidgetPalette,
    pendingCount: Long,
) {
    val context = LocalContext.current

    fun color(pick: (HomeWidgetColors) -> Color) =
        ColorProvider(day = pick(palette.day), night = pick(palette.night))

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(GlanceModifier.width(8.dp))
        RoundButton(
            background = color { it.income },
            icon = R.drawable.ic_tabler_arrow_up,
            iconTint = color { it.onButton },
            contentDescription = context.getString(R.string.add_income_shortcut_title),
            onClick = actionStartActivity(
                QuickTransferActivity.intent(context, QuickTransferDirection.Income)
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        InboxButton(
            pendingCount = pendingCount,
            badgeBackground = color { it.badge },
            badgeText = color { it.onBadge },
        )
        Spacer(GlanceModifier.defaultWeight())
        RoundButton(
            background = color { it.expense },
            icon = R.drawable.ic_tabler_arrow_down,
            iconTint = color { it.onButton },
            contentDescription = context.getString(R.string.add_expense_shortcut_title),
            onClick = actionStartActivity(
                QuickTransferActivity.intent(context, QuickTransferDirection.Expense)
            ),
        )
        Spacer(GlanceModifier.width(8.dp))
    }
}

@Composable
private fun RoundButton(
    background: ColorProvider,
    icon: Int,
    iconTint: ColorProvider,
    contentDescription: String,
    onClick: Action,
) {
    Box(
        modifier = GlanceModifier
            .size(48.dp)
            .background(
                ImageProvider(R.drawable.widget_circle),
                colorFilter = ColorFilter.tint(background),
            )
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}

@Composable
private fun InboxButton(
    pendingCount: Long,
    badgeBackground: ColorProvider,
    badgeText: ColorProvider,
) {
    val context = LocalContext.current
    val description =
        if (pendingCount > 0) {
            val count = pendingCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            context.resources.getQuantityString(R.plurals.widget_open_inbox_pending, count, count)
        } else {
            context.getString(R.string.widget_open_inbox)
        }
    val onClick = actionStartActivity(
        Intent(context, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_OPEN_INBOX, true)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
    )

    Box(
        modifier = GlanceModifier
            .size(56.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .background(
                    ImageProvider(R.drawable.widget_circle),
                    colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.pear_by_francesco_cesqo_stefanini_from_noun_project_cc_by_3_0),
                contentDescription = description,
                modifier = GlanceModifier.size(48.dp),
            )
        }

        val badge = HomeWidgetBadge.text(pendingCount)
        if (badge != null) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd,
            ) {
                Box(
                    modifier = GlanceModifier
                        .height(18.dp)
                        .padding(horizontal = 5.dp)
                        .background(
                            ImageProvider(R.drawable.widget_badge),
                            colorFilter = ColorFilter.tint(badgeBackground),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge,
                        style = TextStyle(
                            color = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}
