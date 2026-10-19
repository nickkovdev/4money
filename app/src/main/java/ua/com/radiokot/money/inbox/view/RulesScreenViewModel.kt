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

import ua.com.radiokot.money.uikit.resolve
import ua.com.radiokot.money.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.uikit.ViewText
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Activity-level: also receives the category picker result for a range being edited.
 */
class RulesScreenViewModel(
    private val payeeRuleRepository: PayeeRuleRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val log by lazyLogger("RulesScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()

    private val _rangeDraft = MutableStateFlow<ViewRangeDraft?>(null)
    val rangeDraft = _rangeDraft.asStateFlow()

    private var categoriesById: Map<String, Category> = emptyMap()
    private var subcategoriesById: Map<String, Subcategory> = emptyMap()

    val groupList: StateFlow<List<ViewPayeeRuleGroup>> =
        combine(
            payeeRuleRepository.getRulesFlow(),
            categoryRepository.getSubcategoriesByCategoriesFlow(),
        ) { rules, subcategoriesByCategory ->
            val categoriesById = subcategoriesByCategory.keys.associateBy(Category::id)
            val subcategoriesById = subcategoriesByCategory.values.flatten().associateBy(Subcategory::id)
            this.categoriesById = categoriesById
            this.subcategoriesById = subcategoriesById

            rules
                .groupBy { it.matchType to it.payeePattern }
                .map { (_, groupRules) ->
                    toViewGroup(groupRules, categoriesById, subcategoriesById)
                }
                .sortedWith(
                    compareByDescending<ViewPayeeRuleGroup> { group ->
                        groupHits(group)
                    }.thenBy(ViewPayeeRuleGroup::displayPattern)
                )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun groupHits(group: ViewPayeeRuleGroup): Long =
        group.rows.sumOf { it.rule.hits }

    private fun toViewGroup(
        rules: List<PayeeRule>,
        categoriesById: Map<String, Category>,
        subcategoriesById: Map<String, Subcategory>,
    ): ViewPayeeRuleGroup {
        val first = rules.first()
        val anyCategory = rules.firstNotNullOfOrNull { rule -> rule.categoryId?.let(categoriesById::get) }
        val currencyCode = anyCategory?.currency?.code

        val rows = rules
            .sortedWith(
                // Ranges from the lowest, the plain rule last.
                compareBy<PayeeRule, java.math.BigDecimal?>(nullsLast()) { rule ->
                    rule.amountRange?.let { it.min ?: java.math.BigDecimal.ZERO }
                }
            )
            .map { rule ->
                val category = rule.categoryId?.let(categoriesById::get)
                val subcategory = rule.subcategoryId?.let(subcategoriesById::get)
                ViewPayeeRuleRow(
                    rangeText = rule.amountRange
                        ?.let { ViewText.Dynamic(describeRangeText(it, currencyCode)) }
                        ?: ViewText.Res(
                            if (rules.size > 1)
                                R.string.rules_other_amounts
                            else
                                R.string.inbox_range_any
                        ),
                    targetTitle = when {
                        rule.action == PayeeRule.Action.Ask -> ViewText.Res(R.string.rules_ask_me)
                        category == null -> ViewText.Res(R.string.rules_missing_category)
                        subcategory != null -> ViewText.Plain("${category.title} · ${subcategory.title}")
                        else -> ViewText.Plain(category.title)
                    },
                    isAsk = rule.action == PayeeRule.Action.Ask,
                    colorScheme = category?.colorScheme,
                    icon = category?.icon,
                    rule = rule,
                )
            }
        val hits = rules.sumOf(PayeeRule::hits)

        return ViewPayeeRuleGroup(
            key = first.matchType.slug + ":" + first.payeePattern,
            displayPattern = first.payeePattern
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) },
            matchType = first.matchType,
            subtitle = ViewText.Dynamic { context ->
                listOfNotNull(
                    ViewText.Res(
                        if (first.matchType == PayeeRule.MatchType.Contains)
                            R.string.rules_subtitle_contains
                        else
                            R.string.rules_subtitle_exact
                    ),
                    ViewText.Plural(R.plurals.rules_rule_count, rules.size),
                    ViewText.Res(R.string.rules_used, listOf(hits))
                        .takeIf { hits > 0 },
                ).joinToString(" · ") { it.resolve(context) }
            },
            rows = rows,
            colorScheme = anyCategory?.colorScheme,
            icon = anyCategory?.icon,
            anyRule = first,
            currencyCode = currencyCode,
        )
    }

    fun onGroupMenuClicked(group: ViewPayeeRuleGroup) {
        _events.tryEmit(Event.ProceedToRuleActions(group.anyRule))
    }

    fun onRowClicked(
        group: ViewPayeeRuleGroup,
        row: ViewPayeeRuleRow,
        locale: Locale,
    ) {
        val rule = row.rule
        val range = rule.amountRange

        if (range == null) {
            // The plain rule is learned from the inbox, its pattern is edited from the menu.
            _events.tryEmit(Event.ProceedToRuleActions(rule))
            return
        }

        _rangeDraft.value = newDraft(group).copy(
            ruleId = rule.id,
            fromText = range.min?.let { editableAmountText(it, locale) }.orEmpty(),
            underText = range.max?.let { editableAmountText(it, locale) }.orEmpty(),
            target = targetOf(rule),
        )
    }

    /**
     * A plain number with the decimal separator of the [locale] and no grouping,
     * as the range editor hint shows it. The editor parser accepts both '.' and ','.
     */
    private fun editableAmountText(
        amount: java.math.BigDecimal,
        locale: Locale,
    ): String =
        amount.stripTrailingZeros().toPlainString()
            .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

    /**
     * Opens a new range for a payee, also one without rules yet (from an inbox card).
     */
    fun onAddRangeForPayeeRequested(
        payeePattern: String,
        displayPattern: String,
        currencyCode: String?,
        isIncome: Boolean,
        categoryOptions: List<ViewRangeTarget.Category>,
    ) {
        val existingGroup = groupList.value.firstOrNull { group ->
            group.matchType == PayeeRule.MatchType.Exact && group.anyRule.payeePattern == payeePattern
        }
        _rangeDraft.value = (existingGroup?.let(::newDraft)
            ?: ViewRangeDraft(
                ruleId = null,
                payeePattern = payeePattern,
                matchType = PayeeRule.MatchType.Exact,
                displayPattern = displayPattern,
                currencyCode = currencyCode,
                fromText = "",
                underText = "",
                target = null,
                categoryOptions = emptyList(),
                isIncome = isIncome,
            )).let { draft ->
            draft.copy(
                categoryOptions = (draft.categoryOptions + categoryOptions.map(::withoutArchivedSubcategory))
                    .distinct(),
            )
        }
    }

    /**
     * An option with an archived subcategory becomes its parent category.
     */
    private fun withoutArchivedSubcategory(option: ViewRangeTarget.Category): ViewRangeTarget.Category {
        val subcategoryId = option.subcategoryId
        if (subcategoryId == null || subcategoriesById[subcategoryId]?.isArchived != true) {
            return option
        }
        return option.copy(
            subcategoryId = null,
            title = categoriesById[option.categoryId]?.title
                ?: option.title.substringBefore(" · "),
        )
    }

    fun onAddRangeClicked(group: ViewPayeeRuleGroup) {
        _rangeDraft.value = newDraft(group)
    }

    private fun newDraft(group: ViewPayeeRuleGroup): ViewRangeDraft {
        val rules = group.rows.map(ViewPayeeRuleRow::rule)
        val options = rules
            .mapNotNull { targetOf(it, withArchivedSubcategory = false) }
            .filterIsInstance<ViewRangeTarget.Category>()
            .distinct()
        val isIncome = rules
            .firstNotNullOfOrNull { rule -> rule.categoryId?.let(categoriesById::get) }
            ?.isIncome == true

        return ViewRangeDraft(
            ruleId = null,
            payeePattern = group.anyRule.payeePattern,
            matchType = group.matchType,
            displayPattern = group.displayPattern,
            currencyCode = group.currencyCode,
            fromText = "",
            underText = "",
            target = null,
            categoryOptions = options,
            isIncome = isIncome,
        )
    }

    /**
     * @param withArchivedSubcategory whether an archived subcategory stays in the target.
     * Options never offer one: they get the parent category instead.
     */
    private fun targetOf(
        rule: PayeeRule,
        withArchivedSubcategory: Boolean = true,
    ): ViewRangeTarget? {
        if (rule.action == PayeeRule.Action.Ask) {
            return ViewRangeTarget.Ask
        }
        val category = rule.categoryId?.let(categoriesById::get)
            ?: return null
        val subcategory = rule.subcategoryId
            ?.let(subcategoriesById::get)
            ?.takeIf { withArchivedSubcategory || !it.isArchived }
        return ViewRangeTarget.Category(
            categoryId = category.id,
            subcategoryId = subcategory?.id,
            title =
                if (subcategory != null)
                    "${category.title} · ${subcategory.title}"
                else
                    category.title,
        )
    }

    fun onDraftFromChanged(text: String) =
        _rangeDraft.value?.let { _rangeDraft.value = it.copy(fromText = text, error = null) }

    fun onDraftUnderChanged(text: String) =
        _rangeDraft.value?.let { _rangeDraft.value = it.copy(underText = text, error = null) }

    fun onDraftTargetSelected(target: ViewRangeTarget) =
        _rangeDraft.value?.let { _rangeDraft.value = it.copy(target = target, error = null) }

    fun onDraftPickCategoryClicked() {
        val draft = _rangeDraft.value
            ?: return
        _events.tryEmit(Event.ProceedToCategorySelection(isIncome = draft.isIncome))
    }

    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        val category = result.selectedCounterparty as? TransferCounterparty.Category
            ?: return
        val draft = _rangeDraft.value
            ?: return

        val target = ViewRangeTarget.Category(
            categoryId = category.category.id,
            subcategoryId = category.subcategory?.id,
            title =
                if (category.subcategory != null)
                    "${category.category.title} · ${category.subcategory.title}"
                else
                    category.category.title,
        )
        _rangeDraft.value = draft.copy(
            target = target,
            categoryOptions = (draft.categoryOptions + target).distinct(),
            error = null,
        )
    }

    fun onDraftSaveClicked() {
        val draft = _rangeDraft.value
            ?: return

        val range = parseRangeDraft(draft.fromText, draft.underText)
            .getOrElse { error ->
                _rangeDraft.value = draft.copy(
                    error = (error as? RangeDraftException)
                        ?.let { ViewText.Res(it.textRes) }
                        ?: ViewText.Plain(error.message.orEmpty())
                )
                return
            }
        val target = draft.target
        if (target == null) {
            _rangeDraft.value = draft.copy(error = ViewText.Res(R.string.rules_error_no_target))
            return
        }

        _rangeDraft.value = null

        viewModelScope.launch {
            log.debug {
                "onDraftSaveClicked(): saving a range rule:" +
                        "\nruleId=${draft.ruleId}," +
                        "\nrange=$range," +
                        "\ntarget=$target"
            }

            payeeRuleRepository.saveRangeRule(
                ruleId = draft.ruleId,
                payeePattern = draft.payeePattern,
                matchType = draft.matchType,
                amountRange = range,
                action =
                    if (target is ViewRangeTarget.Ask)
                        PayeeRule.Action.Ask
                    else
                        PayeeRule.Action.Record,
                categoryId = (target as? ViewRangeTarget.Category)?.categoryId,
                subcategoryId = (target as? ViewRangeTarget.Category)?.subcategoryId,
            )
        }
    }

    fun onDraftDeleteClicked() {
        val ruleId = _rangeDraft.value?.ruleId
            ?: return
        _rangeDraft.value = null

        viewModelScope.launch {
            payeeRuleRepository.deleteRule(ruleId)
        }
    }

    fun onDraftDismissed() {
        _rangeDraft.value = null
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

        updateGroup(
            rule = rule,
            payeePattern = pattern,
            matchType = rule.matchType,
        )
    }

    fun onMatchTypeToggled(rule: PayeeRule) {
        updateGroup(
            rule = rule,
            payeePattern = rule.payeePattern,
            matchType =
                if (rule.matchType == PayeeRule.MatchType.Exact)
                    PayeeRule.MatchType.Contains
                else
                    PayeeRule.MatchType.Exact,
        )
    }

    /**
     * The pattern belongs to the payee, so all its rules (ranges too) change together.
     */
    private fun updateGroup(
        rule: PayeeRule,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
    ) {
        viewModelScope.launch {
            payeeRuleRepository
                .getRules()
                .filter { it.payeePattern == rule.payeePattern && it.matchType == rule.matchType }
                .forEach { groupRule ->
                    payeeRuleRepository.updateRule(
                        ruleId = groupRule.id,
                        payeePattern = payeePattern,
                        matchType = matchType,
                    )
                }
        }
    }

    fun onDeleteConfirmed(rule: PayeeRule) {
        viewModelScope.launch {
            payeeRuleRepository
                .getRules()
                .filter { it.payeePattern == rule.payeePattern && it.matchType == rule.matchType }
                .forEach { groupRule ->
                    payeeRuleRepository.deleteRule(groupRule.id)
                }
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

        /**
         * Pass the result to [onCounterpartySelected].
         */
        class ProceedToCategorySelection(
            val isIncome: Boolean,
        ) : Event

        object Close : Event
    }
}
