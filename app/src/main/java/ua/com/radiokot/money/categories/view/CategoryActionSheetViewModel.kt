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

package ua.com.radiokot.money.categories.view

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryWithAmountsBySubcategory
import ua.com.radiokot.money.categories.logic.ArchivedSubcategoryAmounts
import ua.com.radiokot.money.categories.logic.GetCategoryAmountsBySubcategoryUseCase
import ua.com.radiokot.money.categories.logic.SubcategoryAmountKey
import ua.com.radiokot.money.categories.logic.UnarchiveCategoryUseCase
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.colors.data.ItemIcon
import ua.com.radiokot.money.coroutineScopeThatCancelsWith
import ua.com.radiokot.money.currency.view.ViewAmount
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.map
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.history.data.HistoryPeriod
import ua.com.radiokot.money.transfers.history.view.ViewHistoryPeriod
import java.math.BigInteger

class CategoryActionSheetViewModel(
    parameters: Parameters,
    private val getCategoryAmountsBySubcategoryUseCase: GetCategoryAmountsBySubcategoryUseCase,
    private val unarchiveCategoryUseCase: UnarchiveCategoryUseCase,
) : ViewModel() {

    private val log by lazyLogger("CategoryActionSheetVM")
    private val stateFlowScope = coroutineScopeThatCancelsWith(viewModelScope)
    private val categoryWithAmounts: StateFlow<CategoryWithAmountsBySubcategory> = runBlocking {
        getCategoryAmountsBySubcategoryUseCase(
            categoryId = parameters.categoryId,
            period = parameters.statsPeriod,
        )
            .stateIn(stateFlowScope)
    }

    private val category: StateFlow<Category> =
        categoryWithAmounts
            .map(stateFlowScope, CategoryWithAmountsBySubcategory::category)

    val title: StateFlow<String> =
        category
            .map(stateFlowScope, Category::title)

    val icon: StateFlow<ItemIcon?> =
        category
            .map(stateFlowScope, Category::icon)

    val statsPeriod: ViewHistoryPeriod =
        ViewHistoryPeriod.fromHistoryPeriod(parameters.statsPeriod)

    val statsAmount: StateFlow<ViewAmount> =
        categoryWithAmounts
            .map(stateFlowScope) { (category, amounts) ->
                ViewAmount(
                    value = amounts.values.fold(BigInteger.ZERO, BigInteger::add),
                    currency = category.currency,
                )
            }

    val subcategoryAmounts: StateFlow<List<ViewCategorySheetSubcategoryAmount>> =
        categoryWithAmounts
            .map(stateFlowScope) { (category, amounts) ->
                val folded = ArchivedSubcategoryAmounts.fold(amounts)

                // If the only subcategory amount is uncategorized,
                // no point in showing it as it is equal to the statsAmount.
                if (folded.size == 1 && folded.containsKey(SubcategoryAmountKey.None)) {
                    return@map emptyList()
                }

                // List subcategories in their order,
                // followed by the archived amount and the uncategorized one.
                val active = folded.entries
                    .mapNotNull { (key, amount) ->
                        (key as? SubcategoryAmountKey.Active)?.let { it.subcategory to amount }
                    }
                    .sortedBy { (subcategory, _) -> subcategory }
                    .map { (subcategory, amount) ->
                        ViewCategorySheetSubcategoryAmount(
                            title = subcategory.title,
                            isArchived = false,
                            amount = ViewAmount(amount, category.currency),
                        )
                    }

                val archived = folded[SubcategoryAmountKey.Archived]?.let { amount ->
                    ViewCategorySheetSubcategoryAmount(
                        title = null,
                        isArchived = true,
                        amount = ViewAmount(amount, category.currency),
                    )
                }

                val uncategorized = folded[SubcategoryAmountKey.None]?.let { amount ->
                    ViewCategorySheetSubcategoryAmount(
                        title = null,
                        isArchived = false,
                        amount = ViewAmount(amount, category.currency),
                    )
                }

                active + listOfNotNull(archived, uncategorized)
            }

    val colorScheme: StateFlow<ItemColorScheme> =
        category
            .map(stateFlowScope, Category::colorScheme)

    val isUnarchiveVisible: StateFlow<Boolean> =
        category
            .map(stateFlowScope, Category::isArchived)

    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    fun onEditClicked() {
        _events.tryEmit(
            Event.ProceedToEdit(
                category = category.value,
            )
        )
    }

    fun onActivityClicked() {
        _events.tryEmit(
            Event.ProceedToFilteredActivity(
                categoryCounterparty = TransferCounterparty.Category(
                    category = category.value
                )
            )
        )
    }

    fun onUnarchiveClicked() {
        unarchiveCategory()
    }

    private var unarchiveJob: Job? = null
    private fun unarchiveCategory() {
        unarchiveJob?.cancel()
        unarchiveJob = viewModelScope.launch {

            val category = category.value

            log.debug {
                "unarchiveCategory(): unarchiving:" +
                        "\naccount=$category"
            }

            unarchiveCategoryUseCase
                .invoke(
                    categoryToUnarchive = category,
                )
                .onFailure { error ->
                    log.error(error) {
                        "unarchiveCategory(): failed to unarchive category"
                    }
                }
                .onSuccess {
                    log.info {
                        "Unarchived category $category"
                    }

                    log.debug {
                        "unarchiveCategory(): category unarchived"
                    }

                    _events.emit(Event.Done)
                }
        }
    }

    sealed interface Event {

        class ProceedToEdit(
            val category: Category,
        ) : Event

        class ProceedToFilteredActivity(
            val categoryCounterparty: TransferCounterparty.Category,
        ) : Event

        object Done : Event
    }

    class Parameters(
        val categoryId: String,
        val isIncome: Boolean,
        val statsPeriod: HistoryPeriod,
    )
}

@Immutable
class ViewCategorySheetSubcategoryAmount(
    /**
     * Null for the uncategorized amount and for the archived one.
     */
    val title: String?,
    val isArchived: Boolean,
    val amount: ViewAmount,
) {
    operator fun component1() = title
    operator fun component2() = isArchived
    operator fun component3() = amount
}
