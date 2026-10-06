# Archived subcategories and smart payee "Remember" (F6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Respect `is_archived` on subcategories (stop the editor from un-archiving them, hide them where the user picks, aggregate them in category sheets, archive/unarchive in the editor) and let "Remember" create a `contains` payee rule from a chosen run of the payee's words with a history-based default.

**Architecture:** `Subcategory` gains `isArchived`, read through `DbSchema`. Repositories keep returning all subcategories (history and statistics need archived names); filtering happens in small pure helpers used by the pickers, sheets and suggesters. The subcategory write path is split into a pure planner (`SubcategoryWrites`, unit-tested) and SQL that binds the planned flag (UPDATE for existing rows, INSERT for new ones — no `INSERT OR REPLACE`). The smart remember is a pure word-selection model (`PayeeWordSelection`) plus a pure default suggester (`PayeeRulePatternSuggester`) fed with "known payees" (rule patterns + normalized transfer memos); the chosen pattern travels as `PayeeRememberChoice(pattern, matchType)` through `AcceptInboxSuggestionUseCase` / `CompleteInboxItemUseCase`, and `LearnedRule` finds the created rule by pattern and match type for the undo.

**Tech Stack:** Kotlin, Compose (Compose Unstyled + `uikit`, `MoneyTheme` tokens), Koin 4, PowerSync, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md`, section "F6. Archived subcategories and smart payee "Remember"".

## Global Constraints

- PUBLIC repo: no personal data (no real merchant names from the owner's notifications, card digits, the owner's category/account/subcategory names) in code, tests, docs or commits. Use invented payees like "MCDONALDS AKROPOLE RIG", "MCDONALDS ALFA RIGA", "EXAMPLE CAFE OLD TOWN", and generic categories like "Food", "Cafe", "Transport". Never commit `local.properties` / `app/local.properties`.
- Every new Kotlin file starts with the GPL header copied from a neighbouring file (same copyright line).
- Every new string exists in `app/src/main/res/values/strings.xml` AND `app/src/main/res/values-ru/strings.xml` (lint `MissingTranslation` is an error). Remove strings that become unused in both files.
- UI uses the redesign components only (`uikit/`: `MoneyChip`, `MoneyButton`, `MoneySwitch`, `ListRow`, …) and `MoneyTheme.colors/typography` tokens, `com.composeunstyled.Text`; no Material components.
- Privacy mode: add no amounts anywhere new except the existing category sheet amount formatting (the "Archived" row uses the same `formatOrPrivate` path as other subcategory rows).
- No server changes (no migrations, no sync-config changes). If one seems needed, stop and report.
- Build/test from Git Bash in the worktree root:
  `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'`
  then `./gradlew.bat testDebugUnitTest` (only `SternBrocotTreeSearchTest > extensiveTest` may fail), `./gradlew.bat assembleDebug`, `./gradlew.bat lintDebug`.
  A single test class: `./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.inbox.logic.PayeeWordSelectionTest'`.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PnZC8W3VU3YDKW6kKJsF7t
  ```

## Rulings (controller, owner not available mid-run)

1. **Write path:** existing subcategories are written with `UPDATE … WHERE id = ?` (title, currency, is_income, color scheme, icon, position, is_archived), new ones with a plain `INSERT` (is_archived bound, `false` for a new one). `INSERT OR REPLACE` is gone: a PowerSync PUT upserts every column, the UPDATE becomes a PATCH. The flag bound is the one the subcategory was read with, unless the user toggled it in the editor. The pure planner `SubcategoryWrites.plan` is the unit-tested part.
2. **Repositories return all subcategories** (incl. archived): history, transaction rows, Rules screen titles and statistics need the names. Filtering is done by the consumers listed in the spec through pure helpers.
3. **Transfer sheet:** a subcategory chip is shown when it is not archived or when it is the subcategory the sheet was opened with (an edited transfer or an inbox prefill). It stays visible even after the user unselects it, so it can be re-selected. Quick entry (widget) uses the same sheet, nothing extra.
4. **Suggestions and auto-booking:** a suggestion, alternative, "Payments to sort" button or auto-recorded expense that points to an archived subcategory falls back to its parent category without a subcategory (the category is still right; only the archived bucket is dropped). Rules pointing to an archived subcategory are not changed; the Rules screen keeps showing their target title.
5. **Payee rule target picker** (range editor options): options derived from existing rules or callers never offer an archived subcategory; such an option becomes its parent category.
6. **Category sheets:** both the Overview F2 sheet (`CategoryStatsCalculator`) and the Categories tab action sheet (`CategoryActionSheetViewModel`) fold archived subcategories into one "Archived" row, shown only when its sum is non-zero; it sorts like any other row in F2 (by amount) and right before the "no subcategory" bucket in the action sheet. Its key is `"archived"`, it uses the category icon like the no-subcategory bucket.
7. **Editor:** archived rows are listed after active ones (stable order within each part), dimmed (alpha 0.5) with an "Archived" label instead of the pencil. Archive/Unarchive is a button in the subcategory edit dialog, shown only for an existing (not new) subcategory; it sets the flag on the returned `SubcategoryToUpdate` and closes the dialog (no separate save needed). After every edit/move the list is re-partitioned (active first). Saving positions in list order is fine (positions of archived rows may change, harmless).
8. **PaymentQuestion ("Payments to sort" buttons) does not learn rules** (`rememberPayeePattern = null` today: the payee already has an Ask rule). It gets only the archived-subcategory fallback (ruling 4). No chips in a notification.
9. **Remember UI:** the on/off switch stays (the user must be able to not remember); when on, "Remember for:" with the word chips and the hint are shown below it. Chips show the normalized (lower-case) words — exactly what the rule matches. The old `transfers_remember_for` string is replaced.
10. **Selection gestures:** tap an unselected word → the run extends to include it (and everything between); tap the first or last selected word → it is removed unless it is the only one; tap an interior selected word → no change.
11. **Default selection:** `PayeeRulePatternSuggester.suggestWordCount` over known payees = (a) Record rules with a category: `(rule.payeePattern, rule.categoryId)`; (b) transfers of the recent history (the same 400-row page the Inbox already loads) with a memo: `(PayeeNormalizer.normalize(memo), categoryId)`. Done inbox items are not read separately: their transfers' memos carry the payee. The default is recomputed for the category being accepted: on cards when the user accepts an alternative without touching the chips, in the transfer sheet when the category changes while the chips are untouched.
12. **Insignificant tokens:** length ≤ 2, digit-only, or one of `sia, uab, ltd, llc, inc, gmbh, plc, ooo, the, www, com`. The shared run is trimmed of trailing insignificant tokens and must keep at least one significant token; otherwise no suggestion (whole payee).
13. **Inbox tab one-tap accept** has no chips (it is a one-tap row): with remember on, it uses the default selection for the suggested category. "Other" opens the transfer sheet where the chips are.
14. **Existing rules:** never deleted. A `contains` rule with the same pattern is re-pointed by `saveRuleForPayee` (existing behaviour), exact rules for covered payees stay. `LearnedRule.createdRuleId` finds a new plain rule with the same pattern AND match type; a re-pointed one is not "created" and survives the undo.
15. **Hint text:** EN "Will match every payee with «%1$s»", RU "Будет срабатывать для всех «%1$s»", where the argument is the selected words with "… " in front when words before are unselected and " …" after when words after are unselected (e.g. «mcdonalds …»).

## Review Focus

1. Saving a category twice in a row with archived subcategories (and adding a new one in between): archived flags survive, the new one is active. Test: `SubcategoryWritesTest` (Task 1).
2. Editing an old transfer whose subcategory is archived: its chip is visible and selected; after unselecting it is still visible. Test: `VisibleSubcategoriesTest.keepsTheInitialArchivedOneAfterUnselect` (Task 3).
3. Payees of one word, payees whose only shared prefix is a short/generic token ("SIA …"), and a known payee identical to the current one: whole payee (exact). Tests in `PayeeRulePatternSuggesterTest` (Task 6).
4. Undo after an accept that re-pointed an existing `contains` rule must not delete it; undo after an accept that created a `contains` rule deletes it. Tests in `CardRememberTest` / `LearnedRuleTest` (Task 7).
5. Category sheet where archived subcategories' amounts sum to zero, or only archived amounts exist: no "Archived" row / only the "Archived" row and total unchanged. Tests in `ArchivedSubcategoryAmountsTest` and `CategoryStatsCalculatorTest` (Task 4).

---

## File Structure

- `categories/data/Subcategory.kt` — add `isArchived`.
- `categories/data/SubcategoryToUpdate.kt` — add `isArchived`.
- `categories/data/SubcategoryWrites.kt` (new) — pure write planner.
- `categories/data/PowerSyncCategoryRepository.kt` — SQL binding the flag.
- `powersync/DbSchema.kt` — subcategory columns + mapper.
- `categories/logic/VisibleSubcategories.kt` (new) — pure: picker filter, editor partition, suggestion key fallback.
- `categories/logic/ArchivedSubcategoryAmounts.kt` (new) — pure: folds archived amounts.
- `categories/view/*` — editor list + subcategory dialog.
- `overview/logic/CategoryStatsCalculator.kt`, `overview/view/ViewCategoryStats.kt`, `categories/view/CategoryActionSheetViewModel.kt` — "Archived" row.
- `transfers/view/TransferSheetViewModel.kt`, `TransferSheet.kt` — chips filter, remember words.
- `inbox/logic/InboxCardSuggester.kt`, `InboxSuggestionLookup.kt`, `ProcessBankNotificationUseCase.kt`, `inbox/ask/PaymentQuestionNotifier.kt`, `inbox/view/RulesScreenViewModel.kt` — archived fallback.
- `inbox/logic/PayeeWordSelection.kt`, `PayeeRulePatternSuggester.kt`, `PayeeRememberChoice.kt` (new) — pure remember logic.
- `inbox/logic/GetKnownPayeesUseCase.kt` (new) — loads known payees for the transfer sheet.
- `inbox/logic/AcceptInboxSuggestionUseCase.kt`, `CompleteInboxItemUseCase.kt`, `LearnedRule.kt` — choice plumbing.
- `inbox/view/RememberPayeeWords.kt` (new) — shared chips composable + `ViewRememberPayee`.
- `inbox/view/InboxCardsViewModel.kt`, `InboxCardsScreen.kt`, `ViewInboxCard.kt`, `InboxScreenViewModel.kt` — UI wiring.

---

### Task 1: Subcategory archived flag and the safe write path (model: opus)

**Files:**
- Modify: `app/src/main/java/ua/com/radiokot/money/categories/data/Subcategory.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/categories/data/SubcategoryToUpdate.kt`
- Create: `app/src/main/java/ua/com/radiokot/money/categories/data/SubcategoryWrites.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/categories/data/PowerSyncCategoryRepository.kt` (`updateSubcategories`, SQL constants)
- Modify: `app/src/main/java/ua/com/radiokot/money/powersync/DbSchema.kt` (`SUBCATEGORY_SELECT_COLUMNS`, `toSubcategory`)
- Test: `app/src/test/java/ua/com/radiokot/money/categories/data/SubcategoryWritesTest.kt`

**Interfaces:**
- Produces: `Subcategory(title, position, categoryId, id = uuid, isArchived: Boolean = false)`; `SubcategoryToUpdate(id, title, isNew, isArchived: Boolean = false)` (the `Subcategory` constructor copies `isArchived`); `object SubcategoryWrites { data class Write(val id: String, val title: String, val position: Double, val isArchived: Boolean, val isInsert: Boolean); fun plan(subcategories: List<SubcategoryToUpdate>, newId: () -> String = { UUID.randomUUID().toString() }): List<Write> }`.

- [ ] **Step 1: Write the failing test** `SubcategoryWritesTest`:

```kotlin
class SubcategoryWritesTest {
    @Test
    fun keepsArchivedFlagsOfExistingSubcategories() {
        val writes = SubcategoryWrites.plan(
            listOf(
                SubcategoryToUpdate(id = "a", title = "Cafe", isNew = false, isArchived = false),
                SubcategoryToUpdate(id = "b", title = "Bakery", isNew = false, isArchived = true),
                SubcategoryToUpdate(id = "c", title = "Canteen", isNew = false, isArchived = true),
            )
        )
        Assert.assertEquals(listOf(false, true, true), writes.map { it.isArchived })
        Assert.assertEquals(listOf("a", "b", "c"), writes.map { it.id })
        Assert.assertTrue(writes.none { it.isInsert })
    }

    @Test
    fun newSubcategoryIsInsertedActiveWithAFreshId() {
        val writes = SubcategoryWrites.plan(
            listOf(
                SubcategoryToUpdate(id = "b", title = "Bakery", isNew = false, isArchived = true),
                SubcategoryToUpdate(id = "temp", title = "Snacks", isNew = true),
            ),
            newId = { "fresh" },
        )
        Assert.assertEquals(Pair("fresh", true), writes[1].id to writes[1].isInsert)
        Assert.assertFalse(writes[1].isArchived)
        Assert.assertTrue(writes[0].isArchived)
    }

    @Test
    fun positionsIncreaseInListOrder() {
        val writes = SubcategoryWrites.plan(
            (1..4).map { SubcategoryToUpdate(id = "$it", title = "S$it", isNew = false) }
        )
        Assert.assertEquals(writes.map { it.position }.sorted(), writes.map { it.position })
        Assert.assertEquals(4, writes.map { it.position }.distinct().size)
    }

    @Test
    fun copyFromSubcategoryKeepsTheFlag() {
        val sub = Subcategory(title = "Bakery", position = 1.0, categoryId = "food", id = "b", isArchived = true)
        Assert.assertTrue(SubcategoryToUpdate(sub).isArchived)
    }

    @Test
    fun twoSavesInARowKeepTheFlags() {
        val first = SubcategoryWrites.plan(
            listOf(SubcategoryToUpdate(id = "b", title = "Bakery", isNew = false, isArchived = true))
        )
        // What the editor would read back after the first save.
        val reread = first.map { Subcategory(it.title, it.position, "food", it.id, it.isArchived) }
        val second = SubcategoryWrites.plan(reread.map(::SubcategoryToUpdate))
        Assert.assertTrue(second.single().isArchived)
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure** (`isArchived` / `SubcategoryWrites` missing): `./gradlew.bat testDebugUnitTest --tests 'ua.com.radiokot.money.categories.data.SubcategoryWritesTest'`.

- [ ] **Step 3: Implement.**
  - `Subcategory`: add `val isArchived: Boolean = false` as the last constructor parameter; include it in `toString`. Keep `equals`/`hashCode` by id.
  - `SubcategoryToUpdate`: add `val isArchived: Boolean = false` (last, with default so JSON of old routes decodes); the secondary constructor copies `subcategory.isArchived`; `new()` unchanged (false).
  - `SubcategoryWrites.plan`: a `SternBrocotTreeSearch()`, `goRight()` before each item (exactly as today), `Write(id = if (isNew) newId() else id, title, position = tree.value, isArchived = item.isArchived, isInsert = item.isNew)`.
  - `DbSchema.SUBCATEGORY_SELECT_COLUMNS`: add `"$CATEGORIES_TABLE.$CATEGORY_IS_ARCHIVED as $CATEGORY_SELECTED_IS_ARCHIVED, "` (mind the trailing comma/space so the SQL stays valid). `toSubcategory`: `isArchived = getBooleanOptional(CATEGORY_SELECTED_IS_ARCHIVED) == true`. Note `SELECT_CATEGORIES_THEN_SUBCATEGORIES` uses `CATEGORY_SELECT_COLUMNS` (already has the column) and maps subcategory rows through `toSubcategory` — verify the column alias is the same.
  - `PowerSyncCategoryRepository.updateSubcategories`: iterate `SubcategoryWrites.plan(subcategories)`; `isInsert` → `INSERT_SUBCATEGORY` = `INSERT INTO categories (id, title, currency_id, parent_id, is_income, color_scheme, icon, position, is_archived) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)`; otherwise `UPDATE_SUBCATEGORY_BY_ID` = `UPDATE categories SET title = ?, currency_id = ?, is_income = ?, color_scheme = ?, icon = ?, position = ?, is_archived = ? WHERE id = ? AND parent_id = ?` (build with the `DbSchema` constants like the neighbouring SQL). Bind `write.isArchived` (Boolean, as `UPDATE_ARCHIVED_BY_ID` already does) and the position the same way the old code did. Delete `INSERT_OR_REPLACE_SUBCATEGORY`.
  - Code review every subcategory write: `grep -rn "CATEGORIES_TABLE" app/src/main/java` — the only writes must be `INSERT_CATEGORY` (top-level, `is_archived` 0 is right for a new category), the two subcategory statements, `UPDATE_CATEGORY_BY_ID` (no flag), `UPDATE_ARCHIVED_BY_ID`, `UPDATE_POSITION_BY_ID`. Write the result of this review into the commit message body.
  - The test fixture `app/src/test/java/ua/com/radiokot/money/inbox/InboxTestFixtures.kt` keeps compiling (default parameter).

- [ ] **Step 4: Run the test class, then the whole suite and `assembleDebug`.** Expected: PASS (only the known upstream failure in the suite).

- [ ] **Step 5: Commit** `Keep the archived flag of subcategories when a category is saved`.

---

### Task 2: Category editor shows and toggles archived subcategories (model: sonnet)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/categories/logic/VisibleSubcategories.kt` (editor part only here; Task 3 and 5 add functions to the same object)
- Modify: `categories/view/EditCategoryScreenViewModel.kt`, `EditCategoryScreen.kt`, `ViewSubcategoryToUpdateListItem.kt`, `EditSubcategoryScreen.kt`, `EditSubcategoryScreenViewModel.kt`
- Modify: `res/values/strings.xml`, `res/values-ru/strings.xml`
- Test: `app/src/test/java/ua/com/radiokot/money/categories/logic/VisibleSubcategoriesTest.kt`

**Interfaces:**
- Consumes: `SubcategoryToUpdate.isArchived` (Task 1).
- Produces: `object VisibleSubcategories { fun activeFirst(subcategories: List<SubcategoryToUpdate>): List<SubcategoryToUpdate> }` (stable partition: non-archived in order, then archived in order). `ViewSubcategoryToUpdateListItem.isArchived: Boolean`.

- [ ] **Step 1: Failing test** `VisibleSubcategoriesTest.activeFirstIsAStablePartition`: input `[a(arch), b, c(arch), d]` → ids `[b, d, a, c]`; an all-active list is unchanged.
- [ ] **Step 2: Run it, expect FAIL** (missing object).
- [ ] **Step 3: Implement.**
  - `activeFirst` = `filterNot { it.isArchived } + filter { it.isArchived }`.
  - VM: initial `_subcategories` = `activeFirst(sorted().map(::SubcategoryToUpdate))`; `onSubcategoryEdited` and `onSubcategoryItemMoved` emit `activeFirst(updatedList)`.
  - `ViewSubcategoryToUpdateListItem`: add `val isArchived: Boolean = false` (copied from the source in the secondary constructor; include in `equals`/`hashCode`).
  - `EditCategoryScreen` row: when `item.isArchived` the row content has `alpha(0.5f)` and the trailing pencil is replaced by a `Text(stringResource(R.string.categories_subcategory_archived), style = MoneyTheme.typography.small, color = MoneyTheme.colors.ink3)`. Still clickable (opens the dialog) and draggable.
  - `EditSubcategoryScreenViewModel`: `val isArchiveVisible = !subcategoryToUpdate.isNew`, `val isArchived = subcategoryToUpdate.isArchived`; `fun onArchiveClicked()` emits `Event.Done(subcategoryToUpdate.copy(title = _title.value.takeIf(String::isNotBlank) ?: subcategoryToUpdate.title, isArchived = !subcategoryToUpdate.isArchived))`.
  - `EditSubcategoryScreen`: below the text field, when `isArchiveVisible`, a full-width `MoneyButton(style = MoneyButtonStyle.Tonal (or the existing non-danger secondary style), icon if the API supports one: ic_tabler_archive)` with text `categories_subcategory_unarchive` / `categories_subcategory_archive`. Update the preview.
  - Strings (EN / RU): `categories_subcategory_archived` "Archived" / "В архиве"; `categories_subcategory_archive` "Archive" / "В архив"; `categories_subcategory_unarchive` "Unarchive" / "Вернуть из архива".
- [ ] **Step 4: Run the test, the suite, `assembleDebug`.** PASS.
- [ ] **Step 5: Commit** `Show archived subcategories in the category editor and let them be archived`.

---

### Task 3: Transfer sheet hides archived subcategory chips (model: sonnet)

**Files:**
- Modify: `app/src/main/java/ua/com/radiokot/money/categories/logic/VisibleSubcategories.kt`
- Modify: `app/src/main/java/ua/com/radiokot/money/transfers/view/TransferSheetViewModel.kt` (`subcategoryItemList`)
- Test: `app/src/test/java/ua/com/radiokot/money/categories/logic/VisibleSubcategoriesTest.kt`

**Interfaces:**
- Consumes: `Subcategory.isArchived` (Task 1).
- Produces: `VisibleSubcategories.forPicker(subcategories: List<Subcategory>, keepSubcategoryId: String?): List<Subcategory>` — keeps non-archived ones and the one with `id == keepSubcategoryId`, order unchanged.

- [ ] **Step 1: Failing tests:** `forPickerHidesArchived` (archived one dropped), `forPickerKeepsTheInitialArchivedOne` (`keepSubcategoryId` = archived id → kept), `keepsTheInitialArchivedOneAfterUnselect` (the VM passes the *initial* id, not the current selection: test that `forPicker(list, keepSubcategoryId = initialId)` keeps it regardless of a null current selection — document this in the test name/comment).
- [ ] **Step 2: Run, FAIL.**
- [ ] **Step 3: Implement.** In the VM capture once `private val initialSubcategoryId: String? = ((source as? Category) ?: (destination as? Category))?.subcategory?.id` from the initial counterparties (before any user change), and in `subcategoryItemList` apply `VisibleSubcategories.forPicker(subcategories.sorted(), initialSubcategoryId)`. Quick entry (`QuickTransferActivity`) uses this VM — nothing else to change; verify by reading.
- [ ] **Step 4: Run, PASS; suite; assembleDebug.**
- [ ] **Step 5: Commit** `Hide archived subcategories in the transfer sheet`.

---

### Task 4: Category sheets fold archived subcategories into one row (model: sonnet)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/categories/logic/ArchivedSubcategoryAmounts.kt`
- Modify: `overview/logic/CategoryStatsCalculator.kt`, `overview/view/ViewCategoryStats.kt`, `overview/view/CategoryStatsSheet.kt` (only if the row needs the icon logic), `categories/view/CategoryActionSheetViewModel.kt`, `categories/view/CategoryActionSheet.kt` (title of the archived row)
- Strings: `subcategories_archived_row` "Archived" / "Архивные"
- Test: `app/src/test/java/ua/com/radiokot/money/categories/logic/ArchivedSubcategoryAmountsTest.kt`, update `app/src/test/java/ua/com/radiokot/money/overview/logic/CategoryStatsCalculatorTest.kt`

**Interfaces:**
- Produces:
```kotlin
sealed interface SubcategoryAmountKey {
    data class Active(val subcategory: Subcategory) : SubcategoryAmountKey
    data object None : SubcategoryAmountKey      // transfers without a subcategory
    data object Archived : SubcategoryAmountKey  // all archived subcategories together
}
object ArchivedSubcategoryAmounts {
    /** Archived entry only when its sum is non-zero; None and Active entries kept as given (zero ones too). */
    fun fold(amountBySubcategory: Map<Subcategory?, BigInteger>): Map<SubcategoryAmountKey, BigInteger>
}
```
- `CategoryStatsCalculator.SubcategoryRow(key: SubcategoryAmountKey, amount, fraction)` (replaces `subcategory: Subcategory?`); `subcategoryRows(amountBySubcategory: Map<Subcategory?, BigInteger>)` keeps its input type and folds internally; ordering: amount desc, then Active by title, then None, then Archived.

- [ ] **Step 1: Failing tests:** fold sums two archived ones into `Archived`; archived summing to zero (e.g. +5 and −5) → no `Archived` key; only archived amounts → only `Archived`; total of all values before and after fold is equal. `CategoryStatsCalculatorTest`: an "archived" row with the right fraction; ties order Active < None < Archived. Use the existing test's style and invented titles.
- [ ] **Step 2: Run, FAIL.**
- [ ] **Step 3: Implement.** `ViewCategoryStats`: key `when (row.key) { is Active -> id; None -> "none"; Archived -> "archived" }`, title `Archived -> ViewText.Res(R.string.subcategories_archived_row)`, `isUncategorized = row.key !is Active` (so the archived row also shows the category icon and the secondary colour). `CategoryActionSheetViewModel.subcategoryAmounts`: fold first; keep the "only uncategorized → empty" rule (apply it to `None` only); sort Active by `Subcategory` order, then Archived, then None; title `null` for None (existing "Other"), and for Archived pass a distinct marker — change the pair to a small `@Immutable class ViewCategorySheetSubcategoryAmount(val title: String?, val isArchived: Boolean, val amount: ViewAmount)` and render `subcategories_archived_row` for it in `CategoryActionSheet` (secondary colour like "Other").
- [ ] **Step 4: Run, PASS; suite; assembleDebug.**
- [ ] **Step 5: Commit** `Show archived subcategories as one row in the category sheets`.

---

### Task 5: Suggestions, auto-booking and rule pickers skip archived subcategories (model: sonnet)

**Files:**
- Modify: `categories/logic/VisibleSubcategories.kt`
- Modify: `inbox/logic/InboxCardSuggester.kt`, `inbox/logic/InboxSuggestionLookup.kt`, `inbox/logic/ProcessBankNotificationUseCase.kt` (`getCategoryRef`), `inbox/ask/PaymentQuestionNotifier.kt`, `inbox/view/RulesScreenViewModel.kt` (range editor options)
- Test: `InboxCardSuggesterTest`, `ProcessBankNotificationUseCaseTest`, `VisibleSubcategoriesTest`

**Interfaces:**
- Produces: `VisibleSubcategories.withoutArchived(key: InboxCardSuggester.CategoryKey, isArchived: (subcategoryId: String) -> Boolean): InboxCardSuggester.CategoryKey` (subcategory dropped when archived). `InboxCardSuggester.suggest(..., mapKey: (CategoryKey) -> CategoryKey = { it })` — applied to every history key and to the rule key before `isUsable`.

- [ ] **Step 1: Failing tests:**
  - `InboxCardSuggesterTest.archivedSubcategoryFallsBackToTheCategory`: a rule to (food, bakery) with `mapKey` dropping bakery → suggestion `CategoryKey("food", null)`, reason still `Rule`; history entries to (food, bakery) rank as (food, null) and merge with plain food entries.
  - `ProcessBankNotificationUseCaseTest`: a rule to an archived subcategory auto-records to the parent category with `subcategoryId == null` (use `FakeCategoryRepository` with an archived `Subcategory`; follow the existing tests' setup).
  - `VisibleSubcategoriesTest.withoutArchived*`.
- [ ] **Step 2: Run, FAIL.**
- [ ] **Step 3: Implement.**
  - `InboxSuggestionLookup.suggest` passes `mapKey = { VisibleSubcategories.withoutArchived(it) { id -> subcategoriesById[id]?.isArchived == true } }`; also add `fun viewCategory` stays unchanged.
  - `ProcessBankNotificationUseCase.getCategoryRef`: `?.takeIf { categoryRepository.getSubcategory(it)?.let { s -> s.categoryId == category.id && !s.isArchived } == true }`; update the comment ("A deleted or archived subcategory falls back to the parent category").
  - `PaymentQuestionNotifier`: read `categoryRepository.getSubcategoriesByCategoriesFlow().first()` once, build archived id set, pass `mapKey` the same way.
  - `RulesScreenViewModel`: `targetOf(rule)` used for *options* maps an archived subcategory to the category-only target (title = category title) — keep the group row display (the `toViewGroup` title code) showing the archived subcategory name. Also apply the same mapping to the `categoryOptions` passed into the range editor by callers.
- [ ] **Step 4: Run, PASS; suite; assembleDebug.**
- [ ] **Step 5: Commit** `Do not suggest or auto-book archived subcategories`.

---

### Task 6: Pure smart-remember logic (model: opus)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/logic/PayeeRememberChoice.kt`, `PayeeWordSelection.kt`, `PayeeRulePatternSuggester.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/PayeeWordSelectionTest.kt`, `PayeeRulePatternSuggesterTest.kt`

**Interfaces:**
- Produces:
```kotlin
/** What "Remember" learns: the rule pattern (normalized) and how it matches. */
data class PayeeRememberChoice(val pattern: String, val matchType: PayeeRule.MatchType)

/** A contiguous run of at least one word of a normalized payee. */
data class PayeeWordSelection(val words: List<String>, val first: Int, val last: Int) {
    val isWhole: Boolean
    fun isSelected(index: Int): Boolean
    fun toggle(index: Int): PayeeWordSelection   // ruling 10
    /** «mcdonalds …» argument of the hint, null when the whole payee is selected. */
    val hintPattern: String?
    fun toChoice(): PayeeRememberChoice  // whole → Exact(words.joinToString(" ")); else Contains(normalizePattern(selected))
    companion object {
        /** null for an empty payee; leadingWordCount is clamped to 1..words.size. */
        fun leading(normalizedPayee: String, leadingWordCount: Int): PayeeWordSelection?
        fun whole(normalizedPayee: String): PayeeWordSelection?
    }
}

object PayeeRulePatternSuggester {
    data class KnownPayee(val normalizedPayee: String, val categoryId: String)
    /** @return how many leading words to preselect, 1..word count (= whole payee); 0 for an empty payee. */
    fun suggestWordCount(normalizedPayee: String, categoryId: String, known: Collection<KnownPayee>): Int
    /** Record rules with a category, plus history entries with a non-empty memo; distinct. */
    fun knownPayees(rules: List<PayeeRule>, history: List<InboxCardSuggester.HistoryEntry>): List<KnownPayee>
    /** Convenience: the default selection for remembering [normalizedPayee] into [categoryId]. */
    fun defaultSelection(normalizedPayee: String, categoryId: String, known: Collection<KnownPayee>): PayeeWordSelection?
}
```

- [ ] **Step 1: Failing tests** (`PayeeWordSelectionTest`):
```kotlin
private fun sel(first: Int, last: Int) = PayeeWordSelection(listOf("mcdonalds", "akropole", "rig"), first, last)

@Test fun wholeIsExact() = Assert.assertEquals(
    PayeeRememberChoice("mcdonalds akropole rig", PayeeRule.MatchType.Exact), sel(0, 2).toChoice())
@Test fun partIsContains() = Assert.assertEquals(
    PayeeRememberChoice("mcdonalds", PayeeRule.MatchType.Contains), sel(0, 0).toChoice())
@Test fun tapLastSelectedRemovesIt() = Assert.assertEquals(sel(0, 1), sel(0, 2).toggle(2))
@Test fun tapFirstSelectedRemovesIt() = Assert.assertEquals(sel(1, 2), sel(0, 2).toggle(0))
@Test fun cannotRemoveTheOnlyWord() = Assert.assertEquals(sel(1, 1), sel(1, 1).toggle(1))
@Test fun interiorTapDoesNothing() = Assert.assertEquals(sel(0, 2), sel(0, 2).toggle(1))
@Test fun tapUnselectedExtendsTheRun() = Assert.assertEquals(sel(0, 2), sel(0, 0).toggle(2))
@Test fun outOfRangeTapDoesNothing() = Assert.assertEquals(sel(0, 0), sel(0, 0).toggle(7))
@Test fun hintPatterns() {
    Assert.assertNull(sel(0, 2).hintPattern)
    Assert.assertEquals("mcdonalds …", sel(0, 0).hintPattern)
    Assert.assertEquals("… akropole …", sel(1, 1).hintPattern)
    Assert.assertEquals("… akropole rig", sel(1, 2).hintPattern)
}
@Test fun leadingClampsAndHandlesEmpty() {
    Assert.assertNull(PayeeWordSelection.leading("", 1))
    Assert.assertEquals(sel(0, 2), PayeeWordSelection.leading("mcdonalds akropole rig", 9))
    Assert.assertEquals(sel(0, 0), PayeeWordSelection.leading("mcdonalds akropole rig", 0))
}
```
  `PayeeRulePatternSuggesterTest`:
```kotlin
private val known = listOf(
    KnownPayee("mcdonalds alfa", "food"),
    KnownPayee("mcdonalds giftcard shop", "gifts"),
    KnownPayee("sia example cafe", "food"),
    KnownPayee("example cafe old town", "cafe"),
)
@Test fun sharedLeadingWordInTheSameCategory() =
    Assert.assertEquals(1, PayeeRulePatternSuggester.suggestWordCount("mcdonalds akropole", "food", known))
@Test fun otherCategoryDoesNotCount() =
    Assert.assertEquals(2, PayeeRulePatternSuggester.suggestWordCount("mcdonalds akropole", "transport", known))
@Test fun longestSharedRunWins() = Assert.assertEquals(2, PayeeRulePatternSuggester.suggestWordCount(
    "example cafe new town", "cafe", known + KnownPayee("example bar", "cafe")))
@Test fun genericLeadingTokenAloneIsIgnored() = Assert.assertEquals(3, PayeeRulePatternSuggester.suggestWordCount(
    "sia other shop", "food", known))          // shares only "sia"
@Test fun genericTokenFollowedBySignificantOneIsKept() = Assert.assertEquals(2,
    PayeeRulePatternSuggester.suggestWordCount("sia example bakery", "food", known)) // "sia example"
@Test fun shortAndDigitTokensAreTrimmedFromTheEnd() = Assert.assertEquals(1,
    PayeeRulePatternSuggester.suggestWordCount("bolt ab 22 x", "transport",
        listOf(KnownPayee("bolt ab 22 y", "transport"))))   // "bolt ab 22" → "bolt"
@Test fun samePayeeIsNotEvidence() = Assert.assertEquals(2,
    PayeeRulePatternSuggester.suggestWordCount("mcdonalds alfa", "food", known))
@Test fun singleWordPayeeIsWhole() = Assert.assertEquals(1,
    PayeeRulePatternSuggester.suggestWordCount("mcdonalds", "food", known))
@Test fun emptyPayee() = Assert.assertEquals(0, PayeeRulePatternSuggester.suggestWordCount("", "food", known))
@Test fun containsRulePatternIsEvidence() = Assert.assertEquals(1,
    PayeeRulePatternSuggester.suggestWordCount("taxi example 12", "transport",
        listOf(KnownPayee("taxi", "transport"))))
@Test fun knownPayeesFromRulesAndHistory() { /* Record rule with category → included; Ask rule (categoryId null) → excluded;
    history entry with empty memo → excluded; duplicates collapsed */ }
```
  (Write `knownPayeesFromRulesAndHistory` fully with `PayeeRule(...)` and `InboxCardSuggester.HistoryEntry(...)` values.)
- [ ] **Step 2: Run both classes, FAIL.**
- [ ] **Step 3: Implement** per ruling 12: words = `normalizedPayee.split(' ').filter(String::isNotEmpty)`; for each known payee with the same category and a different normalized payee, common leading word count; trim trailing insignificant tokens; require one significant token in the run; best = max; return `best` if `best in 1 until words.size`, else `words.size`. `toChoice` for a part uses `PayeeNormalizer.normalizePattern(words.subList(first, last + 1).joinToString(" "))`.
- [ ] **Step 4: Run, PASS; suite.**
- [ ] **Step 5: Commit** `Add the payee word selection and the remember pattern suggester`.

---

### Task 7: Remember choice through accept, complete and undo (model: sonnet)

**Files:**
- Modify: `inbox/logic/CompleteInboxItemUseCase.kt`, `inbox/logic/AcceptInboxSuggestionUseCase.kt`, `inbox/logic/LearnedRule.kt`, `inbox/logic/InboxSuggestionLookup.kt`
- Create: `inbox/logic/GetKnownPayeesUseCase.kt`; register in `inbox/InboxModule.kt` (same scope as `CompleteInboxItemUseCase`)
- Modify callers: `transfers/view/TransferSheetViewModel.kt` (temporary: whole-payee exact choice, Task 8 adds the UI), `inbox/view/InboxCardsViewModel.kt`, `inbox/view/InboxScreenViewModel.kt`, `inbox/ask/PaymentQuestionReceiver.kt`
- Tests: `CompleteInboxItemUseCaseTest`, `CardRememberTest`, new `LearnedRuleTest`

**Interfaces:**
- Consumes: `PayeeRememberChoice`, `PayeeWordSelection`, `PayeeRulePatternSuggester` (Task 6).
- Produces:
  - `CompleteInboxItemUseCase.invoke(itemId, transferId, remember: PayeeRememberChoice?, sourceId, destinationId)` — saves `saveRuleForPayee(choice.pattern, choice.matchType, …)`.
  - `AcceptInboxSuggestionUseCase.invoke(item, categoryId, subcategoryId, remember: PayeeRememberChoice?)`; it waits for a rule with `pattern == remember.pattern && matchType == remember.matchType && amountRange == null`.
  - `LearnedRule.createdRuleId(rulesBefore, rulesAfter, choice: PayeeRememberChoice): String?`.
  - `InboxSuggestionLookup.knownPayees: List<PayeeRulePatternSuggester.KnownPayee>` (lazy, from `rules` and both history directions) and `fun defaultRememberSelection(item: InboxItem, categoryId: String): PayeeWordSelection?`.
  - `class GetKnownPayeesUseCase(payeeRuleRepository, transferHistoryRepository) { suspend operator fun invoke(): List<KnownPayee> }` — rules + one `getTransferHistoryPage(cursor = null, limit = 400, withinPeriod = HistoryPeriod.Since70th, counterpartyIds = null)`; memo of either category side; errors → rules only (logged).

- [ ] **Step 1: Failing tests:**
  - `LearnedRuleTest`: created contains rule found; an exact rule with the same pattern is not mistaken for the contains one; a re-pointed (pre-existing id) contains rule → null.
  - `CardRememberTest`: accept with `PayeeRememberChoice("mcdonalds", Contains)` creates a contains rule, its id is returned, `forgetLearnedRule` removes it and an older exact rule `"mcdonalds akropole"` survives; accept when a contains rule `"mcdonalds"` already existed → `learnedRuleId == null` and the rule survives undo. Follow the existing test file's fakes.
  - `CompleteInboxItemUseCaseTest`: contains choice saved with `MatchType.Contains`.
- [ ] **Step 2: Run, FAIL.**
- [ ] **Step 3: Implement** and update every caller:
  - Inbox cards (temporary until Task 8): `remember = if (isRememberOn) lookup.defaultRememberSelection(item, categoryKey.categoryId)?.toChoice() else null` — the lookup must be reachable at record time (keep the latest lookup in a field or a StateFlow).
  - Inbox tab (final, ruling 13): same with the suggested category.
  - `PaymentQuestionReceiver`: `remember = null`.
  - Transfer sheet (temporary): `PayeeWordSelection.whole(rememberPayee)?.toChoice()` when enabled.
- [ ] **Step 4: Run, PASS; suite; assembleDebug.**
- [ ] **Step 5: Commit** `Learn exact or contains rules from a remember choice`.

---

### Task 8: "Remember for:" word chips in the transfer sheet and inbox cards (model: sonnet)

**Files:**
- Create: `app/src/main/java/ua/com/radiokot/money/inbox/view/RememberPayeeWords.kt`
- Modify: `transfers/view/TransferSheetViewModel.kt`, `transfers/view/TransferSheet.kt`, `transfers/TransfersModule.kt` (inject `GetKnownPayeesUseCase`), `inbox/view/InboxCardsViewModel.kt`, `inbox/view/InboxCardsScreen.kt`, `inbox/view/ViewInboxCard.kt`
- Strings: `remember_payee_for` "Remember for:" / "Запомнить для:"; `remember_payee_contains_hint` "Will match every payee with «%1$s»" / "Будет срабатывать для всех «%1$s»"; `remember_payee_exact_hint` "Only this exact payee" / "Только этот получатель"; remove `transfers_remember_for` (both files) if it became unused.

**Interfaces:**
- Consumes: Task 6 and Task 7 APIs.
- Produces:
```kotlin
@Immutable
data class ViewRememberPayee(val words: List<String>, val first: Int, val last: Int, val hintPattern: String?) {
    constructor(selection: PayeeWordSelection) : this(selection.words, selection.first, selection.last, selection.hintPattern)
}

/** "Remember for:" + word chips + hint; no switch (callers keep their switch). */
@Composable
fun RememberPayeeWords(remember: ViewRememberPayee, onWordClicked: (index: Int) -> Unit, modifier: Modifier = Modifier)
```
  Layout: `Text(remember_payee_for, typography.small, ink3)`, a `FlowRow(horizontalArrangement = spacedBy(6.dp), verticalArrangement = spacedBy(6.dp))` of `MoneyChip(text = word, isSelected = index in first..last, onClick = { onWordClicked(index) })`, then a hint `Text` (`small`, `ink3`): contains hint with `hintPattern`, or the exact hint when `hintPattern == null`.

- [ ] **Step 1: Transfer sheet VM.** `private val rememberSelection = MutableStateFlow(PayeeWordSelection.whole(rememberPayee))`, `private var isRememberSelectionTouched = false`, `private val knownPayees = MutableStateFlow<List<KnownPayee>?>(null)` loaded once in `init` when `rememberPayee != null` via `getKnownPayeesUseCase()`. A collector of `combine(knownPayees.filterNotNull(), category id flow)` sets `rememberSelection` to `PayeeRulePatternSuggester.defaultSelection(rememberPayee, categoryId, known)` while not touched (category id = the category side of the current counterparties; skip when none). `fun onRememberWordClicked(index: Int)` toggles and marks touched. `val rememberWords: StateFlow<ViewRememberPayee?>`. On save: `remember = rememberSelection.value?.takeIf { _isRememberPayeeEnabled.value }?.toChoice()`.
- [ ] **Step 2: Transfer sheet UI.** The switch row keeps its look; its label becomes the existing `stringResource(R.string.inbox_cards_remember)` ("Remember for this payee" / RU already present) instead of `transfers_remember_for` (then delete `transfers_remember_for` from both string files and the now unused `rememberPayeeDisplayName` plumbing only if nothing else reads it). When on and `rememberWords != null`, render `RememberPayeeWords` under it (8 dp top padding). Keep the IME/keypad layout working (the block is inside the existing column above the keypad; keep it compact).
- [ ] **Step 3: Inbox cards.** `ViewInboxCard.rememberWords: ViewRememberPayee? = null` (set when `isRememberOn == true`). VM: `rememberSelectionOverrides: MutableStateFlow<Map<String, PayeeWordSelection>>` combined into the cards flow; default = `lookup.defaultRememberSelection(item, suggestion.categoryId)` (whole when no suggestion). `fun onRememberWordClicked(card, index)` toggles the current selection and stores the override. `record(...)`: when remember is on, use the override if present, else `lookup.defaultRememberSelection(item, categoryKey.categoryId)` for the accepted category (ruling 11). Screen: under the Remember switch row, when on, `RememberPayeeWords(card.rememberWords, onWordClicked = { onRememberWordClicked(card, it) })`; add the callback through the screen like `onRememberToggled`. Update previews with invented payees.
- [ ] **Step 4: Run the suite, `assembleDebug`, `lintDebug`.** PASS (lint: no `MissingTranslation`, no new errors).
- [ ] **Step 5: Commit** `Choose which payee words to remember in the transfer sheet and inbox cards`.

---

### Task 9: Docs and the Rules screen check (model: haiku for transcription; controller verifies)

**Files:** `docs/redesign/PROGRESS.md` (F6 row, F6 device checklist), `docs/HANDOFF.md` (F6 row in "What exists").

- [ ] Verify the Rules screen shows `contains` rules (read `RulesScreenViewModel` grouping by pattern + match type and the "contains" label at line ~147); note the result in the PR body. No code change expected.
- [ ] Add the F6 row and checklist (from the PR body device checklist) to `PROGRESS.md`, and an F6 line to the HANDOFF table. No personal data.
- [ ] Commit `Document F6`.
