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

package ua.com.radiokot.money.transfers.view

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import kotlin.math.roundToInt

/**
 * @return whether a released row must settle revealed.
 */
fun shouldSettleRevealed(
    offsetPx: Float,
    revealWidthPx: Float,
    velocityX: Float,
    flingVelocity: Float = 1200f,
): Boolean = when {
    revealWidthPx <= 0f -> false
    velocityX <= -flingVelocity -> true
    velocityX >= flingVelocity -> false
    else -> -offsetPx >= revealWidthPx / 2f
}

/**
 * A row that reveals [actions] at its end when swiped to the left.
 * Rightward drags on a closed row are not consumed, so a parent can handle them.
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    isSwipeEnabled: Boolean,
    actions: @Composable RowScope.(close: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var actionsWidthPx by remember { mutableIntStateOf(0) }
    val close: () -> Unit = remember {
        {
            coroutineScope.launch { offsetX.animateTo(0f) }
            Unit
        }
    }

    Box(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.matchParentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .onSizeChanged { actionsWidthPx = it.width },
            ) {
                actions(close)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MoneyTheme.colors.background)
                .pointerInput(isSwipeEnabled) {
                    if (!isSwipeEnabled) {
                        return@pointerInput
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val wasRevealed = offsetX.value < 0f
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            // Claim leftward drags, or any drag of a revealed row.
                            if (overSlop < 0f || wasRevealed) {
                                change.consume()
                            }
                        } ?: return@awaitEachGesture

                        val minOffset = -actionsWidthPx.toFloat()
                        var currentOffset = offsetX.value

                        horizontalDrag(dragStart.id) { change ->
                            currentOffset = (currentOffset + change.positionChange().x)
                                .coerceIn(minOffset, 0f)
                            val snapTarget = currentOffset
                            coroutineScope.launch { offsetX.snapTo(snapTarget) }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                        }

                        val isRevealed = shouldSettleRevealed(
                            offsetPx = currentOffset,
                            revealWidthPx = actionsWidthPx.toFloat(),
                            velocityX = velocityTracker.calculateVelocity().x,
                        )
                        coroutineScope.launch {
                            offsetX.animateTo(if (isRevealed) minOffset else 0f)
                        }
                    }
                }
        ) {
            content()
        }
    }
}
