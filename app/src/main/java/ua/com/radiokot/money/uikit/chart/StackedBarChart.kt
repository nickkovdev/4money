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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @param bars per bar, segments from the bottom up as color to value
 * @param labels per bar, a label under it or null
 * @param animationKey the bars grow from zero when it changes
 */
@Composable
fun StackedBarChart(
    modifier: Modifier = Modifier,
    bars: List<List<Pair<Color, Float>>>,
    labels: List<String?>,
    labelColor: Color,
    animationKey: Any? = bars,
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = labelColor,
    )

    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Canvas(modifier = modifier) {
        if (bars.isEmpty()) {
            return@Canvas
        }

        val labelAreaHeight = 16.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        val slotWidth = size.width / bars.size
        val barWidth = slotWidth * 0.6f
        val maxTotal = bars
            .maxOf { segments -> segments.sumOf { it.second.toDouble() } }
            .toFloat()
            .coerceAtLeast(Float.MIN_VALUE)
        val currentProgress = progress.value

        bars.forEachIndexed { index, segments ->
            val x = index * slotWidth + (slotWidth - barWidth) / 2
            var bottom = chartHeight

            segments.forEach { (color, value) ->
                val height = value / maxTotal * chartHeight * currentProgress
                drawRect(
                    color = color,
                    topLeft = Offset(x, bottom - height),
                    size = Size(barWidth, height),
                )
                bottom -= height
            }

            val label = labels.getOrNull(index)
            if (label != null) {
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = index * slotWidth + (slotWidth - layout.size.width) / 2,
                        y = chartHeight + (labelAreaHeight - layout.size.height) / 2,
                    ),
                )
            }
        }
    }
}

@Preview(widthDp = 320)
@Composable
private fun StackedBarChartPreview() = StackedBarChart(
    bars = (1..30).map { day ->
        listOf(
            Color.Red to (day % 5).toFloat(),
            Color.LightGray to (day % 3).toFloat(),
        )
    },
    labels = (1..30).map { day -> if (day == 1 || day % 5 == 0) day.toString() else null },
    labelColor = Color.Gray,
    animationKey = Unit,
    modifier = Modifier
        .fillMaxWidth()
        .height(160.dp),
)
