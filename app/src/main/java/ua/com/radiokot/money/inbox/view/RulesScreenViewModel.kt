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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer
import ua.com.radiokot.money.lazyLogger

class RulesScreenViewModel(
    private val payeeRuleRepository: PayeeRuleRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val log by lazyLogger("RulesScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    val ruleItemList: StateFlow<List<ViewPayeeRuleItem>> =
        combine(
            payeeRuleRepository.getRulesFlow(),
            categoryRepository.getSubcategoriesByCategoriesFlow(),
        ) { rules, subcategoriesByCategory ->
            val categoriesById = subcategoriesByCategory.keys.associateBy(Category::id)
            val subcategoriesById = subcategoriesByCategory.values.flatten().associateBy(Subcategory::id)

            rules
                .sortedWith(
                    compareByDescending<PayeeRule> { it.hits }
                        .thenBy { it.payeePattern }
                )
                .map { rule ->
                    val categoryTitle = categoriesById[rule.categoryId]
                        ?.let { category ->
                            val subcategory = rule.subcategoryId?.let(subcategoriesById::get)
                            if (subcategory != null)
                                "${category.title} / ${subcategory.title}"
                            else
                                category.title
                        }
                        ?: "Missing category"

                    ViewPayeeRuleItem(
                        rule = rule,
                        categoryTitle = categoryTitle,
                    )
                }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onRuleClicked(item: ViewPayeeRuleItem) {
        val rule = item.source
            ?: return

        _events.tryEmit(Event.ProceedToRuleActions(rule))
    }

    fun onPatternEdited(
        rule: PayeeRule,
        newPattern: String,
    ) {
        // The matcher compares patterns with the normalized payee,
        // which has no trailing terminal ID and city tokens.
        val pattern = PayeeNormalizer.normalize(newPattern)
        if (pattern.isEmpty()) {
            log.warn {
                "onPatternEdited(): ignoring empty pattern"
            }
            return
        }

        updateRule(
            rule = rule,
            payeePattern = pattern,
            matchType = rule.matchType,
        )
    }

    fun onMatchTypeToggled(rule: PayeeRule) {
        updateRule(
            rule = rule,
            payeePattern = rule.payeePattern,
            matchType =
                if (rule.matchType == PayeeRule.MatchType.Exact)
                    PayeeRule.MatchType.Contains
                else
                    PayeeRule.MatchType.Exact,
        )
    }

    private fun updateRule(
        rule: PayeeRule,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
    ) {
        viewModelScope.launch {
            payeeRuleRepository.updateRule(
                ruleId = rule.id,
                payeePattern = payeePattern,
                matchType = matchType,
            )
        }
    }

    fun onDeleteConfirmed(rule: PayeeRule) {
        viewModelScope.launch {
            payeeRuleRepository.deleteRule(rule.id)
        }
    }

    fun onCloseClicked() {
        _events.tryEmit(Event.Close)
    }

    sealed interface Event {

        /**
         * Pass the chosen action to [onPatternEdited], [onMatchTypeToggled] or [onDeleteConfirmed].
         */
        class ProceedToRuleActions(
            val rule: PayeeRule,
        ) : Event

        object Close : Event
    }
}
