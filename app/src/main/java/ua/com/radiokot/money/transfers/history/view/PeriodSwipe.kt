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

package ua.com.radiokot.money.transfers.history.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import kotlin.math.sign

enum class PeriodSwipe {
    Previous,
    Next,
    ;
}

/**
 * @param totalDragX horizontal drag distance, positive when the finger moved right
 */
fun resolvePeriodSwipe(
    totalDragX: Float,
    thresholdPx: Float,
): PeriodSwipe? = when {
    totalDragX >= thresholdPx -> PeriodSwipe.Previous
    totalDragX <= -thresholdPx -> PeriodSwipe.Next
    else -> null
}

/**
 * @return 1 if [new] starts later than [old], -1 if earlier, 0 if at the same time.
 */
fun periodChangeDirection(
    old: HistoryPeriod,
    new: HistoryPeriod,
): Int =
    new.startInclusive.compareTo(old.startInclusive).sign

private val PeriodSwipeThreshold = 72.dp

/**
 * Switches the period on a horizontal swipe. Doesn't interfere with vertical scrolling.
 */
fun Modifier.periodSwipe(
    isPreviousEnabled: State<Boolean>,
    isNextEnabled: State<Boolean>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = composed {
    val thresholdPx = with(LocalDensity.current) { PeriodSwipeThreshold.toPx() }
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)

    pointerInput(thresholdPx) {
        var totalDragX = 0f

        detectHorizontalDragGestures(
            onDragStart = { totalDragX = 0f },
            onDragCancel = { totalDragX = 0f },
            onDragEnd = {
                when (resolvePeriodSwipe(totalDragX, thresholdPx)) {
                    PeriodSwipe.Previous ->
                        if (isPreviousEnabled.value) currentOnPrevious()

                    PeriodSwipe.Next ->
                        if (isNextEnabled.value) currentOnNext()

                    null ->
                        Unit
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                change.consume()
            },
        )
    }
}

/**
 * Slides and fades the [content] in from the side of the new [period]
 * when it changes: later period from the right, earlier from the left.
 */
@Composable
fun PeriodSlideContainer(
    period: HistoryPeriod,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offsetFraction = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var shownPeriod by remember { mutableStateOf(period) }

    LaunchedEffect(period) {
        val direction = periodChangeDirection(shownPeriod, period)
        shownPeriod = period
        if (direction == 0) {
            return@LaunchedEffect
        }

        offsetFraction.snapTo(0.3f * direction)
        alpha.snapTo(0.3f)
        launch {
            offsetFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
            )
        }
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(250),
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetFraction.value * size.width
                this.alpha = alpha.value
            }
    ) {
        content()
    }
}
