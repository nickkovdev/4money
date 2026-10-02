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

import ua.com.radiokot.money.currency.view.formatOrPrivate
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.R
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import ua.com.radiokot.money.colors.data.HardcodedItemColorSchemeRepository
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.currency.view.ViewCurrency
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import ua.com.radiokot.money.inbox.logic.InboxCardSuggester
import ua.com.radiokot.money.transfers.view.ViewDateFormats
import ua.com.radiokot.money.transfers.view.rememberAppLocale
import ua.com.radiokot.money.uikit.EmptyState
import ua.com.radiokot.money.uikit.IconTile
import ua.com.radiokot.money.uikit.MoneyButton
import ua.com.radiokot.money.uikit.MoneyButtonStyle
import ua.com.radiokot.money.uikit.MoneyChip
import ua.com.radiokot.money.uikit.MoneyIconButton
import ua.com.radiokot.money.uikit.MoneyIconButtonStyle
import ua.com.radiokot.money.uikit.MoneySwitch
import ua.com.radiokot.money.uikit.ViewText
import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.uikit.theme.MidnightMoneyColors
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneySpacing
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import java.math.BigInteger
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private enum class SwipeAction {
    Accept,
    Skip,
    Pick,
    ;
}

@Composable
private fun InboxCardsScreen(
    cards: State<List<ViewInboxCard>>,
    progress: State<ViewInboxCardsProgress>,
    undo: State<ViewInboxCardUndo?>,
    onAcceptClicked: (ViewInboxCard) -> Unit,
    onSkipClicked: (ViewInboxCard) -> Unit,
    onPickClicked: (ViewInboxCard) -> Unit,
    onAlternativeClicked: (ViewInboxCard, ViewInboxCardCategory) -> Unit,
    onRememberToggled: (ViewInboxCard, Boolean) -> Unit = { _, _ -> },
    onAmountRulesClicked: (ViewInboxCard) -> Unit = {},
    onUndoClicked: () -> Unit,
    onUndoTimedOut: (ViewInboxCardUndo) -> Unit,
    onRulesClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) = Box(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .add(WindowInsets.statusBars)
        )
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                contentDescription = "Back",
                onClick = onCloseClicked,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = "Sort payments",
                    style = MoneyTheme.typography.headline,
                )
                val currentProgress = progress.value
                Text(
                    text =
                        if (cards.value.isEmpty())
                            "Nothing to sort"
                        else
                            "${currentProgress.sortedCount + 1} of ${currentProgress.totalCount} to sort",
                    style = MoneyTheme.typography.caption,
                    color = MoneyTheme.colors.ink3,
                )
            }

            MoneyIconButton(
                icon = R.drawable.ic_tabler_adjustments_horizontal,
                contentDescription = "Rules",
                onClick = onRulesClicked,
            )
        }

        ProgressSegments(
            progress = progress.value,
            modifier = Modifier
                .padding(
                    horizontal = MoneySpacing.screen,
                )
                .padding(
                    bottom = 16.dp,
                )
        )

        val topCard = cards.value.firstOrNull()

        if (topCard == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EmptyState(
                    icon = R.drawable.ic_tabler_check,
                    title = "All sorted",
                    text = "New bank payments will appear here as cards.",
                    action = {
                        MoneyButton(
                            text = "Back to the inbox",
                            onClick = onCloseClicked,
                            modifier = Modifier
                                .padding(top = 6.dp)
                        )
                    },
                )
            }
        } else {
            val scope = rememberCoroutineScope()
            val hapticFeedback = LocalHapticFeedback.current
            // Owned here so the buttons can throw the card the same way a swipe does.
            val offset = remember(topCard.key) {
                Animatable(Offset.Zero, Offset.VectorConverter)
            }
            val cardWidthPx = remember { mutableStateOf(1f) }
            val cardHeightPx = remember { mutableStateOf(1f) }

            fun commit(action: SwipeAction) {
                scope.launch {
                    val target = when (action) {
                        SwipeAction.Accept -> Offset(cardWidthPx.value * 1.4f, offset.value.y)
                        SwipeAction.Skip -> Offset(-cardWidthPx.value * 1.4f, offset.value.y)
                        SwipeAction.Pick -> Offset.Zero
                    }
                    if (action != SwipeAction.Pick) {
                        offset.animateTo(target, tween(durationMillis = 180))
                    } else {
                        offset.animateTo(Offset.Zero, spring())
                    }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    when (action) {
                        SwipeAction.Accept -> onAcceptClicked(topCard)
                        SwipeAction.Skip -> onSkipClicked(topCard)
                        SwipeAction.Pick -> onPickClicked(topCard)
                    }
                    offset.snapTo(Offset.Zero)
                }
            }

            CardStack(
                card = topCard,
                behindCount = (cards.value.size - 1).coerceAtMost(2),
                offset = offset,
                scope = scope,
                cardWidthPx = cardWidthPx,
                cardHeightPx = cardHeightPx,
                onCommit = ::commit,
                onAlternativeClicked = { category ->
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onAlternativeClicked(topCard, category)
                },
                onRememberToggled = { isOn -> onRememberToggled(topCard, isOn) },
                onAmountRulesClicked = { onAmountRulesClicked(topCard) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal = MoneySpacing.screen,
                    )
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 16.dp,
                    )
            ) {
                MoneyIconButton(
                    icon = R.drawable.ic_tabler_x,
                    contentDescription = "Skip for now",
                    tint = MoneyTheme.colors.expense,
                    size = 60.dp,
                    iconSize = 26.dp,
                    onClick = { commit(SwipeAction.Skip) },
                )
                MoneyIconButton(
                    icon = R.drawable.ic_tabler_layout_grid,
                    contentDescription = "Choose a category",
                    size = 48.dp,
                    onClick = { commit(SwipeAction.Pick) },
                )
                MoneyIconButton(
                    icon = R.drawable.ic_tabler_check,
                    contentDescription =
                        if (topCard.suggestion != null)
                            "Accept: ${topCard.suggestion.fullTitle}"
                        else
                            "Choose a category",
                    style = MoneyIconButtonStyle.Filled,
                    size = 60.dp,
                    iconSize = 28.dp,
                    onClick = { commit(SwipeAction.Accept) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MoneySpacing.screen + 8.dp,
                        vertical = 10.dp,
                    )
            ) {
                listOf(
                    "← skip" to TextAlign.Start,
                    "↑ pick" to TextAlign.Center,
                    "accept →" to TextAlign.End,
                ).forEach { (text, align) ->
                    Text(
                        text = text,
                        style = MoneyTheme.typography.small,
                        color = MoneyTheme.colors.ink3,
                        textAlign = align,
                        modifier = Modifier
                            .weight(1f)
                    )
                }
            }
        }
    }

    val currentUndo = undo.value
    LaunchedEffect(currentUndo?.id) {
        if (currentUndo != null) {
            delay(UNDO_VISIBLE_MS)
            onUndoTimedOut(currentUndo)
        }
    }

    AnimatedVisibility(
        visible = currentUndo != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MoneyTheme.colors.surface2,
                    shape = MoneyShapes.medium,
                )
                .padding(
                    start = 16.dp,
                    end = 6.dp,
                    top = 4.dp,
                    bottom = 4.dp,
                )
        ) {
            Text(
                text = currentUndo?.text ?: "",
                style = MoneyTheme.typography.labelRegular,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MoneyButton(
                text = "Undo",
                style = MoneyButtonStyle.Text,
                icon = R.drawable.ic_tabler_arrow_back_up,
                onClick = onUndoClicked,
            )
        }
    }
}

private const val UNDO_VISIBLE_MS = 4000L

@Composable
private fun ProgressSegments(
    modifier: Modifier = Modifier,
    progress: ViewInboxCardsProgress,
) {
    val colors = MoneyTheme.colors
    val total = progress.totalCount.coerceAtLeast(1)

    if (total <= MAX_PROGRESS_SEGMENTS) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = modifier
                .fillMaxWidth()
        ) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = when {
                                index < progress.sortedCount -> colors.accent
                                index == progress.sortedCount -> colors.ink2
                                else -> colors.surface2
                            },
                            shape = MoneyShapes.pill,
                        )
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    color = colors.surface2,
                    shape = MoneyShapes.pill,
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.sortedCount.toFloat() / total)
                    .height(4.dp)
                    .background(
                        color = colors.accent,
                        shape = MoneyShapes.pill,
                    )
            )
        }
    }
}

private const val MAX_PROGRESS_SEGMENTS = 12

/**
 * The top card follows the finger, tilts with the drag and shows a stamp of the action
 * it will commit; two plain cards peek from behind.
 */
@Composable
private fun CardStack(
    modifier: Modifier = Modifier,
    card: ViewInboxCard,
    behindCount: Int,
    offset: Animatable<Offset, *>,
    scope: CoroutineScope,
    cardWidthPx: androidx.compose.runtime.MutableState<Float>,
    cardHeightPx: androidx.compose.runtime.MutableState<Float>,
    onCommit: (SwipeAction) -> Unit,
    onAlternativeClicked: (ViewInboxCardCategory) -> Unit,
    onRememberToggled: (Boolean) -> Unit,
    onAmountRulesClicked: () -> Unit,
) = Box(
    modifier = modifier,
) {
    val colors = MoneyTheme.colors

    if (behindCount >= 2) {
        Box(
            modifier = Modifier
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 26.dp,
                )
                .fillMaxSize()
                .alpha(0.45f)
                .background(colors.surface, MoneyShapes.extraLarge)
        )
    }
    if (behindCount >= 1) {
        Box(
            modifier = Modifier
                .padding(
                    start = 11.dp,
                    end = 11.dp,
                    top = 13.dp,
                    bottom = 10.dp,
                )
                .fillMaxSize()
                .alpha(0.75f)
                .background(colors.surface, MoneyShapes.extraLarge)
        )
    }

    val acceptThreshold = 0.3f
    val pickThreshold = 0.22f

    fun actionOf(value: Offset): SwipeAction? {
        val x = value.x / cardWidthPx.value
        val y = value.y / cardHeightPx.value
        return when {
            x > acceptThreshold && abs(x) > abs(y) -> SwipeAction.Accept
            x < -acceptThreshold && abs(x) > abs(y) -> SwipeAction.Skip
            y < -pickThreshold -> SwipeAction.Pick
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .padding(bottom = 20.dp)
            .fillMaxSize()
            .onSizeChanged { size ->
                cardWidthPx.value = size.width.toFloat().coerceAtLeast(1f)
                cardHeightPx.value = size.height.toFloat().coerceAtLeast(1f)
            }
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = offset.value.x / cardWidthPx.value * 12f
            }
            .pointerInput(card.key) {
                detectDragGestures(
                    onDragEnd = {
                        val action = actionOf(offset.value)
                        if (action != null) {
                            onCommit(action)
                        } else {
                            scope.launch {
                                offset.animateTo(Offset.Zero, spring())
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offset.animateTo(Offset.Zero, spring())
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offset.snapTo(offset.value + dragAmount)
                        }
                    },
                )
            }
            .clip(MoneyShapes.extraLarge)
            .background(colors.surface2)
            .semantics {
                contentDescription = "Payment card: swipe right to accept, left to skip, up to pick"
            }
    ) {
        CardContent(
            card = card,
            onAlternativeClicked = onAlternativeClicked,
            onRememberToggled = onRememberToggled,
            onAmountRulesClicked = onAmountRulesClicked,
        )

        val action = actionOf(offset.value)
        val strength = (abs(offset.value.x) / cardWidthPx.value / acceptThreshold)
            .coerceAtLeast(-offset.value.y / cardHeightPx.value / pickThreshold)
            .coerceIn(0f, 1f)

        if (strength > 0.15f) {
            Stamp(
                action = action
                    ?: when {
                        offset.value.x > 0 && abs(offset.value.x) >= abs(offset.value.y) -> SwipeAction.Accept
                        offset.value.x < 0 && abs(offset.value.x) >= abs(offset.value.y) -> SwipeAction.Skip
                        else -> SwipeAction.Pick
                    },
                acceptTitle = card.suggestion?.title,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .alpha(strength)
            )
        }
    }
}

@Composable
private fun Stamp(
    modifier: Modifier = Modifier,
    action: SwipeAction,
    acceptTitle: String?,
) {
    val colors = MoneyTheme.colors
    val (text, background, content) = when (action) {
        SwipeAction.Accept ->
            if (acceptTitle != null)
                Triple(acceptTitle, colors.accent, colors.onAccent)
            else
                Triple("Pick a category", colors.accent, colors.onAccent)

        SwipeAction.Skip ->
            Triple("Skip", colors.expense, colors.background)

        SwipeAction.Pick ->
            Triple("Pick a category", colors.ink, colors.background)
    }

    Text(
        text = text,
        style = MoneyTheme.typography.label,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .graphicsLayer {
                rotationZ = if (action == SwipeAction.Skip) -6f else 6f
            }
            .background(
                color = background,
                shape = MoneyShapes.pill,
            )
            .padding(
                horizontal = 12.dp,
                vertical = 7.dp,
            )
    )
}

@Composable
@OptIn(ExperimentalTime::class)
private fun CardContent(
    card: ViewInboxCard,
    onAlternativeClicked: (ViewInboxCardCategory) -> Unit,
    onRememberToggled: (Boolean) -> Unit,
    onAmountRulesClicked: () -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(22.dp)
) {
    val colors = MoneyTheme.colors
    val suggestion = card.suggestion

    if (suggestion != null) {
        ItemLogo(
            title = suggestion.title,
            colorScheme = suggestion.colorScheme,
            icon = suggestion.icon,
            modifier = Modifier
                .size(52.dp)
        )
    } else {
        IconTile(
            icon = R.drawable.ic_tabler_receipt,
            size = 52.dp,
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text =
                if (card.isTitleRawText && LocalPrivacyMode.current)
                    PRIVATE_TITLE
                else
                    card.title,
            style = MoneyTheme.typography.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val locale = rememberAppLocale()
        val time = remember(card.receivedAt, locale) {
            ViewDateFormats.time(card.receivedAt, locale)
        }
        val dateTimeText =
            if (card.isReceivedToday)
                stringResource(R.string.date_today_at, time)
            else
                remember(card.receivedAt, locale) {
                    val today = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                    ViewDateFormats.dayMonth(card.receivedAt.date, locale, today) + " " + time
                }
        Text(
            text = listOf(dateTimeText, card.sourceText)
                .filter(String::isNotEmpty)
                .joinToString(" · "),
            style = MoneyTheme.typography.labelRegular,
            color = colors.ink2,
        )
    }

    val amount = card.amount
    if (amount != null) {
        val amountFormat = rememberViewAmountFormat()
        Text(
            text = amountFormat.formatOrPrivate(
                amount = amount,
                customColor =
                    if (card.isIncoming)
                        colors.income
                    else
                        colors.expense,
            ),
            style = MoneyTheme.typography.amountLarge,
            maxLines = 1,
        )
    }

    if (card.isForeignCurrency) {
        HintBox(
            icon = R.drawable.ic_tabler_alert_triangle,
            iconTint = colors.warning,
            text = "Foreign currency: the transfer sheet opens to convert the amount",
        )
    }

    HintBox(
        icon = R.drawable.ic_tabler_adjustments_horizontal,
        iconTint = colors.accent,
        text = card.reasonText?.resolve() ?: "No suggestion yet: pick a category",
    )

    val isRememberOn = card.isRememberOn
    if (isRememberOn != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MoneyShapes.medium)
                .clickable { onRememberToggled(!isRememberOn) }
                .padding(
                    horizontal = 4.dp,
                    vertical = 2.dp,
                )
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = "Remember for this payee",
                    style = MoneyTheme.typography.label,
                )
                Text(
                    text =
                        if (isRememberOn)
                            "Next time it is recorded automatically"
                        else
                            "Next time it waits here again",
                    style = MoneyTheme.typography.small,
                    color = colors.ink3,
                )
            }
            MoneySwitch(
                isOn = isRememberOn,
                onToggled = null,
            )
        }
    }

    if (card.isAmountRulesHinted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(MoneyShapes.pill)
                .clickable(onClick = onAmountRulesClicked)
                .padding(
                    horizontal = 4.dp,
                    vertical = 4.dp,
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_adjustments_horizontal),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .size(16.dp)
            )
            Text(
                text = "Different categories here: set up amount rules",
                style = MoneyTheme.typography.label,
                color = colors.accent,
            )
        }
    }

    if (card.alternatives.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "OR PICK",
                style = MoneyTheme.typography.overline,
                color = colors.ink3,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                card.alternatives.forEach { category ->
                    MoneyChip(
                        text = category.fullTitle,
                        onClick = { onAlternativeClicked(category) },
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun HintBox(
    @DrawableRes
    icon: Int,
    iconTint: Color,
    text: String,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
        .fillMaxWidth()
        .background(
            color = MoneyTheme.colors.surface,
            shape = MoneyShapes.medium,
        )
        .padding(
            horizontal = 14.dp,
            vertical = 12.dp,
        )
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier
            .size(18.dp)
    )
    Text(
        text = text,
        style = MoneyTheme.typography.caption,
        color = MoneyTheme.colors.ink2,
    )
}

@Composable
fun InboxCardsScreen(
    viewModel: InboxCardsViewModel,
    onRulesClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) = InboxCardsScreen(
    cards = viewModel.cards.collectAsState(),
    progress = viewModel.progress.collectAsState(),
    undo = viewModel.undo.collectAsState(),
    onAcceptClicked = remember { viewModel::onAcceptClicked },
    onSkipClicked = remember { viewModel::onSkipClicked },
    onPickClicked = remember { viewModel::onPickClicked },
    onAlternativeClicked = remember { viewModel::onAlternativeClicked },
    onRememberToggled = remember { viewModel::onRememberToggled },
    onAmountRulesClicked = remember { viewModel::onAmountRulesClicked },
    onUndoClicked = remember { viewModel::onUndoClicked },
    onUndoTimedOut = remember { viewModel::onUndoTimedOut },
    onRulesClicked = onRulesClicked,
    onCloseClicked = onCloseClicked,
)

@Preview(
    apiLevel = 34,
    heightDp = 760,
    widthDp = 360,
)
@Composable
private fun InboxCardsScreenPreview() = MoneyTheme(colors = MidnightMoneyColors) {
    val schemes = HardcodedItemColorSchemeRepository().getItemColorSchemesByName()
    val food = ViewInboxCardCategory(
        key = InboxCardSuggester.CategoryKey("food", null),
        title = "Food",
        subcategoryTitle = null,
        colorScheme = schemes.getValue("Orange3"),
        icon = null,
    )
    val car = food.copy(
        key = InboxCardSuggester.CategoryKey("car", null),
        title = "Car",
        colorScheme = schemes.getValue("Blue3"),
    )

    Box(
        modifier = Modifier
            .background(MoneyTheme.colors.background)
    ) {
        InboxCardsScreen(
            cards = listOf(
                ViewInboxCard(
                    key = "1",
                    title = "Fuelstop",
                    amount = ViewAmount(
                        value = BigInteger("-702"),
                        currency = ViewCurrency(symbol = "€", precision = 2),
                    ),
                    isIncoming = false,
                    isForeignCurrency = false,
                    receivedAt = LocalDateTime(2026, 10, 3, 11, 55),
                    isReceivedToday = true,
                    sourceText = "Card",
                    suggestion = food,
                    reasonText = ViewText.Plain("Remembered payee → Food"),
                    alternatives = listOf(car),
                ),
                ViewInboxCard(
                    key = "2",
                    title = "Rimi",
                    amount = null,
                    isIncoming = false,
                    isForeignCurrency = false,
                    receivedAt = LocalDateTime(2026, 10, 3, 9, 30),
                    isReceivedToday = true,
                    sourceText = "",
                    suggestion = null,
                    reasonText = null,
                    alternatives = emptyList(),
                ),
            ).let(::mutableStateOf),
            progress = ViewInboxCardsProgress(1, 4).let(::mutableStateOf),
            undo = ViewInboxCardUndo("Recorded to Food", 1).let(::mutableStateOf),
            onAcceptClicked = {},
            onSkipClicked = {},
            onPickClicked = {},
            onAlternativeClicked = { _, _ -> },
            onUndoClicked = {},
            onUndoTimedOut = {},
            onRulesClicked = {},
            onCloseClicked = {},
        )
    }
}
