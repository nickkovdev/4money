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

import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class DonutSegmentsTest {

    private fun v(key: String, value: Long) = key to BigInteger.valueOf(value)

    @Test
    fun empty_And_NonPositive() {
        Assert.assertEquals(emptyList<DonutSegment<String>>(), computeDonutSegments<String>(emptyList()))
        Assert.assertEquals(
            emptyList<DonutSegment<String>>(),
            computeDonutSegments(listOf(v("a", 0), v("b", -5))),
        )
    }

    @Test
    fun single_IsFullCircle() {
        Assert.assertEquals(
            listOf(DonutSegment("x", -90f, 360f)),
            computeDonutSegments(listOf(v("a", 0), v("x", 42))),
        )
    }

    @Test
    fun shares_WithoutGap() {
        val segments = computeDonutSegments(
            listOf(v("a", 1), v("b", 1), v("c", 2)),
            gapAngle = 0f,
        )
        Assert.assertEquals(
            listOf(
                DonutSegment("a", -90f, 90f),
                DonutSegment("b", 0f, 90f),
                DonutSegment("c", 90f, 180f),
            ),
            segments,
        )
    }

    @Test
    fun shares_WithGap() {
        val segments = computeDonutSegments(listOf(v("a", 5), v("b", 5)), gapAngle = 2f)
        Assert.assertEquals(
            listOf(
                DonutSegment("a", -89f, 178f),
                DonutSegment("b", 91f, 178f),
            ),
            segments,
        )
    }

    @Test
    fun sweepsPlusGapsCoverTheCircle() {
        val segments = computeDonutSegments(
            listOf(v("a", 333), v("b", 120), v("c", 547)),
            gapAngle = 2f,
        )
        Assert.assertEquals(360f, segments.sumOf { (it.sweepAngle + 2f).toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun tinyShare_NeverNegative() {
        val segments = computeDonutSegments(listOf(v("a", 1), v("b", 100_000)), gapAngle = 2f)
        Assert.assertEquals(0f, segments[0].sweepAngle, 0f)
        Assert.assertTrue(segments.all { it.sweepAngle >= 0f })
    }
}
