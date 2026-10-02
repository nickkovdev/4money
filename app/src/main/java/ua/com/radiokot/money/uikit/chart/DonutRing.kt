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

package ua.com.radiokot.money.uikit.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigInteger

@Composable
fun DonutRing(
    modifier: Modifier = Modifier,
    segments: List<DonutSegment<Color>>,
    trackColor: Color,
    strokeWidth: Dp = 14.dp,
    animationKey: Any? = segments,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawBehind {
                val strokePx = strokeWidth.toPx()
                val diameter = size.minDimension - strokePx
                val topLeft = Offset(
                    x = (size.width - diameter) / 2,
                    y = (size.height - diameter) / 2,
                )
                val arcSize = Size(diameter, diameter)
                val stroke = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Butt,
                )

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )

                val currentProgress = progress.value
                segments.forEach { segment ->
                    drawArc(
                        color = segment.key,
                        startAngle = segment.startAngle,
                        sweepAngle = segment.sweepAngle * currentProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
            },
        content = content,
    )
}

@Preview
@Composable
private fun DonutRingPreview() = DonutRing(
    segments = computeDonutSegments(
        listOf(
            Color(0xFFBF3D3F) to BigInteger.valueOf(50),
            Color(0xFF4F63B3) to BigInteger.valueOf(30),
            Color(0xFF3FA56F) to BigInteger.valueOf(20),
        )
    ),
    trackColor = Color(0xFFBDBDBD),
    animationKey = Unit,
    modifier = Modifier.size(160.dp),
)
