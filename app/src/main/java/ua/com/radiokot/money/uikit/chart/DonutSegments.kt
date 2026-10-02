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

import java.math.BigInteger
import java.math.MathContext

data class DonutSegment<K>(
    val key: K,
    val startAngle: Float,
    val sweepAngle: Float,
)

/**
 * Splits a circle into segments proportional to positive [values], in their order.
 * Each segment is shortened by [gapAngle] (half on each side) to separate it from neighbours;
 * a single segment is a full circle.
 */
fun <K> computeDonutSegments(
    values: List<Pair<K, BigInteger>>,
    gapAngle: Float = 2f,
    startAngle: Float = -90f,
): List<DonutSegment<K>> {
    val positive = values.filter { it.second.signum() > 0 }
    if (positive.isEmpty()) {
        return emptyList()
    }
    if (positive.size == 1) {
        return listOf(DonutSegment(positive.first().first, startAngle, 360f))
    }

    val total = positive
        .fold(BigInteger.ZERO) { sum, (_, value) -> sum + value }
        .toBigDecimal()
    var cursor = startAngle

    return positive.map { (key, value) ->
        val share = value
            .toBigDecimal()
            .divide(total, MathContext.DECIMAL64)
            .toFloat()
        val fullSweep = 360f * share
        val segment = DonutSegment(
            key = key,
            startAngle = cursor + gapAngle / 2,
            sweepAngle = (fullSweep - gapAngle).coerceAtLeast(0f),
        )
        cursor += fullSweep
        segment
    }
}
