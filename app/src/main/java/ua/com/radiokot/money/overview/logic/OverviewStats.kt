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

package ua.com.radiokot.money.overview.logic

import kotlinx.datetime.LocalDate
import java.math.BigInteger

data class OverviewStats(
    val total: BigInteger,
    /**
     * One entry per day within the bounds, or empty if there are too many days to chart.
     */
    val days: List<OverviewDay>,
    /**
     * Categories having their own segment in [days], by total descending.
     */
    val stackCategoryIds: List<String>,
    val dayAverage: BigInteger,
    val weekAverage: BigInteger,
    val topCategories: List<OverviewCategoryShare>,
    /**
     * Number of categories with a positive total.
     */
    val categoryCount: Int,
)

data class OverviewDay(
    val date: LocalDate,
    val total: BigInteger,
    /**
     * Positive segments only, stack categories first, then the rest.
     */
    val segments: List<OverviewDaySegment>,
)

data class OverviewDaySegment(
    /**
     * Null for the rest of the categories.
     */
    val categoryId: String?,
    val amount: BigInteger,
)

data class OverviewCategoryShare(
    val categoryId: String,
    val amount: BigInteger,
    val percent: Int,
)
