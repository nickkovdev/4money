# Bank Notifications, Payee Rules and Inbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A SEB Latvia card-payment push notification becomes an expense automatically when a payee rule matches, or a pending inbox item otherwise; the Inbox screen turns pending items into expenses through the regular transfer sheet and teaches rules on the way.

**Architecture:** A `NotificationListenerService` filters by package name, hands the text to a pure-Kotlin parser, normalizer, matcher and resolver, and writes locally through PowerSync (no network in the listener). Two new synced tables, `payee_rules` and `inbox_items`, follow the existing per-user/RLS/`atomic_crud` upload path. Auto-created expenses go through the existing `TransferFundsUseCase`, so balances and the `transfer` RPC upload behave exactly like manual entries. The PowerSync stream is tied to app visibility so the always-bound listener process does not keep a sync connection open.

**Tech Stack:** Kotlin 2.4, Jetpack Compose foundation + Compose Unstyled (`com.composables:core`), Koin 4.2 (session scope), PowerSync Kotlin 1.15.2, kotlinx-datetime, Supabase Postgres (`money` schema), PowerSync service 1.26 sync rules, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` — sections "C. Notification listener", "D. Rules and inbox", "C/D decisions (2026-10-02)".

## Global Constraints

- Only `se.seb.latvia` is a source. `com.google.android.apps.walletnfcrel` and every other package return immediately, before any Koin lookup or allocation beyond the package-name check.
- Listener: no network calls, no wakelock, no foreground service, no polling. All work is a local PowerSync SQLite write.
- Amount in SEB text uses a decimal comma; currency is an ISO code; payee is truncated by the bank.
- The transfer time is the notification post time converted to local wall-clock time (`TimeZone.currentSystemDefault()`), never the date/time inside the text.
- Card hint `karte...NNNN` → account via a card mapping preference; default is the most used account.
- Foreign currency (payment currency ≠ resolved account currency) is never auto-created; it is always a pending inbox item showing the original amount.
- An SEB notification matching no template becomes a pending inbox item with the raw text; the parser never throws.
- Auto-created expenses carry the payee as memo and can be undone from the inbox.
- Public repository: no real card digits, names, IBANs or other personal data in fixtures or code. Card digits in tests are `0000`/`1111`; merchant names are the spec sample (`DEEPSEERWEA`) or invented ones.
- New main-source Kotlin files start with the project's GPL-3 license header (copy lines 1–18 of any existing `.kt` file, e.g. `powersync/DbSchema.kt`). Code blocks in this plan omit the header for brevity; test files in `app/src/test` have no header, matching the existing tests.
- Package for all new client code: `ua.com.radiokot.money.inbox` (subpackages `data`, `logic`, `listener`, `view`).
- Build/test on this Windows machine, from Git Bash in `E:\projects\4money`:
  ```bash
  export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'
  ./gradlew.bat testDebugUnitTest
  ./gradlew.bat assembleDebug
  ```
  Every "Run:" line below assumes these two variables are exported in the same shell.
- Server changes go into a NEW migration `supabase/migrations/20261002000000_money_rules_inbox.sql`; never edit `20261001000000_money_schema.sql`.

## Review Focus

1. **Bank re-posts or updates the same payment notification** (Android delivers `onNotificationPosted` again with a new `postTime`, sometimes a new notification id) → exactly one inbox item / one expense. The spec says "hash of (package, notification key, posted time, text)", but post time changes on every re-post and the key may change with the id, which would defeat dedup. This plan hashes `(package, title, text)` for recognized payments (the text already carries the payment minute, amount, card and payee) and adds `postTime` only for unrecognized texts (which carry no timestamp of their own). Pinned in Task 5 tests and Task 7 use-case test `duplicateIsSkipped`. Flag to the user as a deliberate spec deviation.
2. **Unicode variants of the SEB text** — decomposed `ū`/`ā` (NFD), non-breaking or narrow no-break spaces, `…` (U+2026) instead of `...` → still parsed. Pinned in Task 4 parser tests.
3. **Amounts that are not the sampled shape** — integer `15 EUR`, thousands `1 234,56 EUR`, more decimals than the account currency precision → parsed correctly, or routed to pending (never rounded silently). Pinned in Task 4 and Task 6 tests.
4. **Rule pointing to an archived/deleted category or account, or no accounts synced yet** (fresh install, listener fires before the first sync) → pending item, no crash. Pinned in Task 6 resolver tests and Task 7 use-case test `archivedRuleCategoryGoesToPending`.
5. **Notification while signed out or before the session scope exists, or a burst of two notifications at once** → silently ignored when there is no session; serialized by a mutex so the dedup check and insert cannot race. Pinned in Task 7 (`duplicateIsSkipped` exercises the dedup path inside the mutex); the no-session early return is code in Task 9, verified manually in Task 15.

---

## File Structure

Server:
- Create `supabase/migrations/20261002000000_money_rules_inbox.sql` — tables, RLS, grants, publication, `atomic_crud` whitelist.
- Modify `deploy/powersync/sync-config.yaml` — two queries in `user_data`.

Client, pure logic (unit-tested, no Android, no logging):
- `inbox/data/IncomingBankNotification.kt` — what the listener extracted from a `StatusBarNotification`.
- `inbox/data/ParsedBankNotification.kt` — parser output.
- `inbox/data/PayeeRule.kt`, `inbox/data/InboxItem.kt` — domain models.
- `inbox/logic/BankNotificationParser.kt`, `inbox/logic/SebLatviaNotificationParser.kt` — templates.
- `inbox/logic/PayeeNormalizer.kt` — case/whitespace/trailing IDs.
- `inbox/logic/BankNotificationDedupHash.kt` — SHA-256 dedup key.
- `inbox/logic/PayeeRuleMatcher.kt` — exact, then longest contains.
- `inbox/logic/AutoExpenseResolver.kt` — decides create vs pending, converts to minor units.
- `inbox/logic/ProcessBankNotificationUseCase.kt` — orchestrates parse → dedup → match → transfer/inbox.
- `inbox/logic/InboxTransferPrefill.kt` — builds the prefilled `TransferSheetRoute`.

Client, data (PowerSync/SharedPreferences):
- Modify `powersync/DbSchema.kt` — two tables.
- `inbox/data/InboxRepository.kt`, `inbox/data/PowerSyncInboxRepository.kt`.
- `inbox/data/PayeeRuleRepository.kt`, `inbox/data/PowerSyncPayeeRuleRepository.kt`.
- `inbox/data/CardAccountPreferences.kt`, `inbox/data/CardAccountPreferencesOnPrefs.kt`.
- `inbox/data/MostUsedAccountSource.kt`, `inbox/data/PowerSyncMostUsedAccountSource.kt`.
- `inbox/logic/CardAccountResolver.kt` — interface + `DefaultCardAccountResolver`.
- `inbox/logic/CompleteInboxItemUseCase.kt`, `inbox/logic/UndoInboxItemUseCase.kt`.
- `inbox/InboxModule.kt` — Koin.

Client, Android:
- `inbox/listener/BankNotificationListenerService.kt`, `inbox/listener/NotificationAccess.kt`.
- Modify `AndroidManifest.xml`, `res/values/strings.xml`.
- `powersync/PowerSyncConnection.kt`; modify `powersync/PowerSyncModule.kt`, `powersync/BackgroundPowerSyncWorker.kt`, `MoneyAppActivity.kt`, `MoneyApp.kt`.

Client, UI:
- Modify `transfers/logic/TransferFundsUseCase.kt`, `transfers/logic/PowerSyncTransferFundsUseCase.kt` (caller-supplied transfer ID).
- Modify `transfers/view/TransferSheetNavigation.kt`, `TransferSheetViewModel.kt`, `TransferSheet.kt`, `transfers/TransfersModule.kt` (inbox mode, "remember for" toggle).
- `inbox/view/InboxActivity.kt`, `InboxScreen.kt`, `InboxScreenNavigation.kt`, `InboxScreenViewModel.kt`, `ViewInboxItem.kt`.
- `inbox/view/RulesScreen.kt`, `RulesScreenNavigation.kt`, `RulesScreenViewModel.kt`, `ViewPayeeRuleItem.kt`.
- Modify `preferences/view/PreferencesScreen*.kt`, `preferences/PreferencesModule.kt`, `home/view/HomeViewModel.kt`, `home/view/HomeActivity.kt`, `home/HomeModule.kt`.

Tests (`app/src/test/java/ua/com/radiokot/money/inbox/...`):
- `logic/PayeeNormalizerTest.kt`, `logic/SebLatviaNotificationParserTest.kt`, `logic/BankNotificationDedupHashTest.kt`, `logic/PayeeRuleMatcherTest.kt`, `logic/AutoExpenseResolverTest.kt`, `logic/ProcessBankNotificationUseCaseTest.kt`, `logic/InboxTransferPrefillTest.kt`, `InboxTestFixtures.kt`.

All client paths below are relative to `app/src/main/java/ua/com/radiokot/money/` unless they start with `app/`, `supabase/` or `deploy/`.

---

### Task 1: Server tables, RLS, upload whitelist and sync rules

**Files:**
- Create: `supabase/migrations/20261002000000_money_rules_inbox.sql`
- Modify: `deploy/powersync/sync-config.yaml`

**Interfaces:**
- Produces: tables `money.payee_rules(id, user_id, payee_pattern, match_type, category_id, subcategory_id, account_id, hits, last_used_at)` and `money.inbox_items(id, user_id, received_at, source_package, raw_text, amount, currency_code, payee, card_last4, account_id, status, transfer_id, dedup_hash)`; synced to the client under the same table and column names. Client Task 2 must use exactly these names.

- [ ] **Step 1: Write the migration**

Create `supabase/migrations/20261002000000_money_rules_inbox.sql`:

```sql
-- 4Money: payee → category rules and the bank notification inbox (sub-projects C + D).
-- Per-user tables like accounts/categories/transfers: user_id defaults to auth.uid(), RLS on.
-- No foreign keys to categories/accounts: transfers have none either, and offline-first
-- uploads must not fail on ordering.

create table money.payee_rules
(
    id             uuid primary key,
    user_id        uuid    not null default auth.uid() references auth.users (id) on delete cascade,
    -- Normalized payee (lower case, collapsed whitespace, no trailing terminal IDs).
    payee_pattern  text    not null check (payee_pattern <> ''),
    match_type     text    not null default 'exact' check (match_type in ('exact', 'contains')),
    category_id    uuid    not null,
    subcategory_id uuid,
    -- Optional account the rule was learned with; a card mapping takes precedence.
    account_id     uuid,
    hits           integer not null default 0,
    -- Local wall-clock time, no time zone, like transfers.time.
    last_used_at   timestamp
);
create index payee_rules_user_idx on money.payee_rules (user_id);

create table money.inbox_items
(
    id             uuid primary key,
    user_id        uuid      not null default auth.uid() references auth.users (id) on delete cascade,
    -- Notification post time as local wall-clock time.
    received_at    timestamp not null,
    source_package text      not null,
    raw_text       text      not null,
    -- Decimal amount as parsed (not minor units: the currency may be unknown to the app).
    amount         numeric,
    currency_code  text,
    payee          text,
    card_last4     text,
    account_id     uuid,
    status         text      not null default 'pending' check (status in ('pending', 'done', 'dismissed')),
    transfer_id    uuid,
    dedup_hash     text      not null
);
create index inbox_items_user_status_idx on money.inbox_items (user_id, status, received_at desc);
create index inbox_items_user_dedup_idx on money.inbox_items (user_id, dedup_hash);

alter table money.payee_rules enable row level security;
alter table money.inbox_items enable row level security;

create policy payee_rules_own on money.payee_rules
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy inbox_items_own on money.inbox_items
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select, insert, update, delete on money.payee_rules, money.inbox_items to authenticated;
grant all on money.payee_rules, money.inbox_items to service_role;
grant select on money.payee_rules, money.inbox_items to powersync_role;

alter publication powersync add table money.payee_rules, money.inbox_items;

-- Same body as in 20261001000000_money_schema.sql, only the writable table list is extended.
-- CREATE OR REPLACE keeps the existing grants (execute to authenticated only).
create or replace function money.atomic_crud(operations jsonb) returns void
    language plpgsql
    set search_path = money
as
$$
declare
    operation      jsonb;
    table_name     text;
    row_id         text;
    op             text;
    op_data        jsonb;
    column_data    jsonb;
    column_name    text;
    column_value   text;
    insert_columns text;
    insert_values  text;
    upsert_sets    text;
    update_sets    text;
    comma          text;
begin
    for operation in select jsonb_array_elements(operations)
        loop
            table_name := operation ->> 't';
            row_id := operation ->> 'id';
            op := operation ->> 'o';
            op_data := operation -> 'd';

            if table_name is null or row_id is null or op is null then
                raise exception 'Invalid operation: t (table), id, and o (operation) are required fields';
            end if;

            if table_name not in ('accounts', 'categories', 'transfers', 'payee_rules', 'inbox_items') then
                raise exception insufficient_privilege using message = 'Table ' || table_name || ' is not writable';
            end if;

            if op not in ('I', 'U', 'D') then
                raise exception 'Invalid operation type: %. Must be I (upsert), U (update), or D (delete)', op;
            end if;

            if op in ('I', 'U') and op_data is null then
                raise exception 'd (data) is required for I (upsert) and U (update) operations';
            end if;

            if op = 'D' then
                execute format('delete from money.%I where id = %L', table_name, row_id);
                continue;
            end if;

            insert_columns := '';
            insert_values := '';
            upsert_sets := '';
            update_sets := '';
            comma := '';

            if op = 'I' then
                insert_columns := 'id';
                insert_values := quote_literal(row_id);
                upsert_sets := 'id = excluded.id';
                comma := ',';
            end if;

            for column_data in select jsonb_array_elements(op_data)
                loop
                    column_name := quote_ident(column_data ->> 0);
                    column_value := case
                                        when jsonb_typeof(column_data -> 1) = 'null' then 'NULL'
                                        when jsonb_typeof(column_data -> 1) = 'string'
                                            then quote_literal(column_data ->> 1)
                                        else column_data ->> 1
                        end;

                    if column_name is null then
                        raise exception 'Column name cannot be null';
                    end if;

                    if op = 'I' then
                        insert_columns := insert_columns || comma || column_name;
                        insert_values := insert_values || comma || column_value;
                        upsert_sets := upsert_sets || comma || column_name || ' = excluded.' || column_name;
                    else
                        update_sets := update_sets || comma || column_name || ' = ' || column_value;
                    end if;

                    comma := ',';
                end loop;

            if op = 'I' then
                execute format(
                        'insert into money.%I (%s) values (%s) on conflict (id) do update set %s',
                        table_name, insert_columns, insert_values, upsert_sets
                        );
            elsif update_sets <> '' then
                execute format(
                        'update money.%I set %s where id = %L',
                        table_name, update_sets, row_id
                        );
            end if;
        end loop;
end;
$$;
```

- [ ] **Step 2: Add the sync rules**

In `deploy/powersync/sync-config.yaml`, append two entries to `bucket_definitions.user_data.data` (after the `sync_errors` query, same indentation):

```yaml
      - >-
        SELECT id, payee_pattern, match_type, category_id, subcategory_id, account_id, hits, last_used_at
        FROM money.payee_rules AS payee_rules
        WHERE user_id = bucket.user_id
      - >-
        SELECT id, received_at, source_package, raw_text, amount, currency_code, payee, card_last4,
               account_id, status, transfer_id, dedup_hash
        FROM money.inbox_items AS inbox_items
        WHERE user_id = bucket.user_id
```

- [ ] **Step 3: Apply on the VM and verify**

Apply the migration the same way as `20261001000000_money_schema.sql` (as `postgres` into the shared Supabase DB; with the default self-hosting compose the container is `supabase-db`):

```bash
docker exec -i supabase-db psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/migrations/20261002000000_money_rules_inbox.sql
```

Then verify, replacing `<USER_UUID>` with the app user's id (Preferences → User → Identifier) and `<CATEGORY_UUID>` with any of their category ids:

```bash
docker exec -i supabase-db psql -U postgres -d postgres -v ON_ERROR_STOP=1 <<'SQL'
select tablename from pg_publication_tables where pubname = 'powersync' order by 1;
begin;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"<USER_UUID>","role":"authenticated"}', true);
select money.atomic_crud('[{"t":"payee_rules","id":"7a0c9d44-0000-4000-8000-000000000001","o":"I","d":[["payee_pattern","test payee"],["match_type","exact"],["category_id","<CATEGORY_UUID>"],["hits","0"]]},{"t":"inbox_items","id":"7a0c9d44-0000-4000-8000-000000000002","o":"I","d":[["received_at","2026-10-02 08:06:00"],["source_package","se.seb.latvia"],["raw_text","x"],["amount","2.12"],["currency_code","USD"],["status","pending"],["dedup_hash","h"]]}]');
select payee_pattern, user_id = '<USER_UUID>'::uuid as own from money.payee_rules;
select amount, status from money.inbox_items;
rollback;
SQL
```

Expected: the publication lists `accounts, categories, currencies, inbox_items, payee_rules, sync_errors, transfers`; the two selects return one row each with `own = t`, `amount = 2.12`, `status = pending`; nothing persists (rollback).

- [ ] **Step 4: Reload PowerSync**

Copy the updated `sync-config.yaml` to the VM's PowerSync folder and restart: `docker compose -f deploy/powersync/docker-compose.yml restart`. Expected in `docker logs 4money-powersync`: the new sync rules are deployed and replication of `money.payee_rules`/`money.inbox_items` starts, no errors.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/20261002000000_money_rules_inbox.sql deploy/powersync/sync-config.yaml
git commit -m "Add payee rules and inbox tables to the money schema"
```

---

### Task 2: Client PowerSync tables and domain models

**Files:**
- Modify: `powersync/DbSchema.kt` (the `getPowerSyncSchema()` list near line 55, new constants/tables before `fun LocalDateTime.toDbString()` near line 376)
- Create: `inbox/data/PayeeRule.kt`, `inbox/data/InboxItem.kt`, `inbox/data/IncomingBankNotification.kt`, `inbox/data/ParsedBankNotification.kt`

**Interfaces:**
- Consumes: column names from Task 1.
- Produces: `DbSchema.PAYEE_RULES_TABLE`, `DbSchema.PAYEE_RULE_*`, `DbSchema.INBOX_ITEMS_TABLE`, `DbSchema.INBOX_ITEM_*` constants; `data class PayeeRule`, `enum PayeeRule.MatchType { Exact, Contains }` with `slug`/`fromSlug`; `data class InboxItem`, `enum InboxItem.Status { Pending, Done, Dismissed }` with `slug`/`fromSlug`; `data class IncomingBankNotification(packageName, postTimeMillis, title, text)`; `sealed interface ParsedBankNotification { data class CardPayment(amount: BigDecimal, currencyCode: String, cardLast4: String?, payee: String); data object Unrecognized }`.

- [ ] **Step 1: Register the tables in `DbSchema`**

In `getPowerSyncSchema()` extend the list:

```kotlin
    fun getPowerSyncSchema() = Schema(
        // All the tables have 'id' column by default.
        listOf(
            getPowerSyncCurrencyTable(),
            getPowerSyncAccountsTable(),
            getPowerSyncCategoriesTable(),
            getPowerSyncTransfersTable(),
            getPowerSyncSyncErrorsTable(),
            getPowerSyncPayeeRulesTable(),
            getPowerSyncInboxItemsTable(),
        )
    )
```

Insert right before `fun LocalDateTime.toDbString() =`:

```kotlin
    const val PAYEE_RULES_TABLE = "payee_rules"
    const val PAYEE_RULE_PATTERN = "payee_pattern"
    const val PAYEE_RULE_MATCH_TYPE = "match_type"
    const val PAYEE_RULE_CATEGORY_ID = "category_id"
    const val PAYEE_RULE_SUBCATEGORY_ID = "subcategory_id"
    const val PAYEE_RULE_ACCOUNT_ID = "account_id"
    const val PAYEE_RULE_HITS = "hits"
    const val PAYEE_RULE_LAST_USED_AT = "last_used_at"

    private fun getPowerSyncPayeeRulesTable() = Table(
        name = PAYEE_RULES_TABLE,
        columns = listOf(
            Column.text(PAYEE_RULE_PATTERN),
            Column.text(PAYEE_RULE_MATCH_TYPE),
            Column.text(PAYEE_RULE_CATEGORY_ID),
            Column.text(PAYEE_RULE_SUBCATEGORY_ID),
            Column.text(PAYEE_RULE_ACCOUNT_ID),
            Column.integer(PAYEE_RULE_HITS),
            Column.text(PAYEE_RULE_LAST_USED_AT),
        ),
        ignoreEmptyUpdates = true,
    )

    const val INBOX_ITEMS_TABLE = "inbox_items"
    const val INBOX_ITEM_RECEIVED_AT = "received_at"
    const val INBOX_ITEM_SOURCE_PACKAGE = "source_package"
    const val INBOX_ITEM_RAW_TEXT = "raw_text"
    const val INBOX_ITEM_AMOUNT = "amount"
    const val INBOX_ITEM_CURRENCY_CODE = "currency_code"
    const val INBOX_ITEM_PAYEE = "payee"
    const val INBOX_ITEM_CARD_LAST4 = "card_last4"
    const val INBOX_ITEM_ACCOUNT_ID = "account_id"
    const val INBOX_ITEM_STATUS = "status"
    const val INBOX_ITEM_TRANSFER_ID = "transfer_id"
    const val INBOX_ITEM_DEDUP_HASH = "dedup_hash"

    private fun getPowerSyncInboxItemsTable() = Table(
        name = INBOX_ITEMS_TABLE,
        columns = listOf(
            Column.text(INBOX_ITEM_RECEIVED_AT),
            Column.text(INBOX_ITEM_SOURCE_PACKAGE),
            Column.text(INBOX_ITEM_RAW_TEXT),
            // Decimal string, numeric on the server.
            Column.text(INBOX_ITEM_AMOUNT),
            Column.text(INBOX_ITEM_CURRENCY_CODE),
            Column.text(INBOX_ITEM_PAYEE),
            Column.text(INBOX_ITEM_CARD_LAST4),
            Column.text(INBOX_ITEM_ACCOUNT_ID),
            Column.text(INBOX_ITEM_STATUS),
            Column.text(INBOX_ITEM_TRANSFER_ID),
            Column.text(INBOX_ITEM_DEDUP_HASH),
        ),
        indexes = listOf(
            Index(
                name = "inbox-status-idx",
                columns = listOf(
                    IndexedColumn.ascending(INBOX_ITEM_STATUS),
                    IndexedColumn.descending(INBOX_ITEM_RECEIVED_AT),
                )
            ),
            Index(
                name = "inbox-dedup-idx",
                columns = listOf(
                    IndexedColumn.ascending(INBOX_ITEM_DEDUP_HASH),
                )
            ),
        ),
        ignoreEmptyUpdates = true,
    )
```

- [ ] **Step 2: Create the domain models**

`inbox/data/PayeeRule.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import kotlinx.datetime.LocalDateTime
import java.util.UUID

/**
 * Maps a normalized payee to a category (and optionally an account).
 *
 * @param payeePattern normalized, see [ua.com.radiokot.money.inbox.logic.PayeeNormalizer]
 */
data class PayeeRule(
    val payeePattern: String,
    val matchType: MatchType,
    val categoryId: String,
    val subcategoryId: String?,
    val accountId: String?,
    val hits: Long,
    val lastUsedAt: LocalDateTime?,
    val id: String = UUID.randomUUID().toString(),
) {
    enum class MatchType(val slug: String) {
        Exact("exact"),
        Contains("contains"),
        ;

        companion object {
            fun fromSlug(slug: String): MatchType =
                entries.firstOrNull { it.slug == slug }
                    ?: throw IllegalArgumentException("Unknown match type slug '$slug'")
        }
    }
}
```

`inbox/data/InboxItem.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

/**
 * A bank notification kept for review or as a record of an auto-created expense.
 *
 * @param receivedAt notification post time, local wall-clock
 * @param amount decimal amount as written by the bank, null if not parsed
 * @param accountId account resolved from the card hint at receive time
 * @param transferId the expense created from this item, if [status] is [Status.Done]
 */
data class InboxItem(
    val id: String,
    val receivedAt: LocalDateTime,
    val sourcePackage: String,
    val rawText: String,
    val amount: BigDecimal?,
    val currencyCode: String?,
    val payee: String?,
    val cardLast4: String?,
    val accountId: String?,
    val status: Status,
    val transferId: String?,
    val dedupHash: String,
) {
    enum class Status(val slug: String) {
        Pending("pending"),
        Done("done"),
        Dismissed("dismissed"),
        ;

        companion object {
            fun fromSlug(slug: String): Status =
                entries.firstOrNull { it.slug == slug }
                    ?: throw IllegalArgumentException("Unknown inbox status slug '$slug'")
        }
    }
}
```

`inbox/data/IncomingBankNotification.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

/**
 * What the listener extracted from a posted notification.
 *
 * @param postTimeMillis `StatusBarNotification.postTime`, epoch millis
 */
data class IncomingBankNotification(
    val packageName: String,
    val postTimeMillis: Long,
    val title: String?,
    val text: String,
)
```

`inbox/data/ParsedBankNotification.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import java.math.BigDecimal

sealed interface ParsedBankNotification {

    /**
     * @param amount positive decimal amount
     * @param currencyCode upper-case ISO 4217 code
     * @param cardLast4 last 4 card digits, if the text has them
     * @param payee merchant name as written by the bank (possibly truncated), trimmed
     */
    data class CardPayment(
        val amount: BigDecimal,
        val currencyCode: String,
        val cardLast4: String?,
        val payee: String,
    ) : ParsedBankNotification

    /**
     * A notification of a source bank matching no known template.
     */
    data object Unrecognized : ParsedBankNotification
}
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/powersync/DbSchema.kt app/src/main/java/ua/com/radiokot/money/inbox/data
git commit -m "Add payee rules and inbox items to the client schema"
```

---

### Task 3: Payee normalizer

**Files:**
- Create: `inbox/logic/PayeeNormalizer.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/PayeeNormalizerTest.kt`

**Interfaces:**
- Produces: `object PayeeNormalizer { fun displayName(rawPayee: String): String; fun normalize(rawPayee: String): String; fun normalizePattern(input: String): String }`.
  - `displayName` — NFC, NBSP→space, collapsed whitespace, trailing `.`/`,`/`;` removed, case kept (memo text).
  - `normalize` — `displayName`, lower case (`Locale.ROOT`), trailing tokens with digits or a known city/country token removed while more than one token remains (rule key).
  - `normalizePattern` — `displayName` + lower case only (for patterns typed in the Rules screen; keeps digits).

- [ ] **Step 1: Write the failing test**

```kotlin
package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PayeeNormalizerTest {

    @Test
    fun sebSampleTrailingDotIsRemoved() {
        assertEquals("deepseerwea", PayeeNormalizer.normalize("DEEPSEERWEA ."))
        assertEquals("DEEPSEERWEA", PayeeNormalizer.displayName("DEEPSEERWEA ."))
    }

    @Test
    fun whitespaceIsCollapsedAndNbspIsSpace() {
        assertEquals("cafe brivibas", PayeeNormalizer.normalize("  CAFE\u00A0\u00A0 BRIVIBAS  "))
    }

    @Test
    fun trailingTerminalIdsAndCityAreRemoved() {
        assertEquals("rimi hyper", PayeeNormalizer.normalize("RIMI HYPER 0123 RIGA"))
        assertEquals("narvesen", PayeeNormalizer.normalize("NARVESEN 45 LV"))
    }

    @Test
    fun singleTokenIsNeverRemoved() {
        assertEquals("shop24", PayeeNormalizer.normalize("SHOP24"))
        assertEquals("riga", PayeeNormalizer.normalize("RIGA"))
    }

    @Test
    fun decomposedDiacriticsAreComposed() {
        val decomposed = "KAFEJNI\u0304CA" // I + combining macron
        assertEquals("kafejnīca", PayeeNormalizer.normalize(decomposed))
    }

    @Test
    fun patternKeepsDigits() {
        assertEquals("shop 24", PayeeNormalizer.normalizePattern(" Shop  24 "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.PayeeNormalizerTest"`
Expected: FAIL, compilation error `Unresolved reference 'PayeeNormalizer'`.

- [ ] **Step 3: Write the implementation**

`inbox/logic/PayeeNormalizer.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import java.text.Normalizer
import java.util.Locale

object PayeeNormalizer {

    private val whitespaceRegex = Regex("\\s+")
    private val trailingTokens = setOf("riga", "rīga", "lv", "lva", "latvia")

    /**
     * @return the payee for display and memo: composed Unicode, single spaces,
     * no trailing punctuation, case kept.
     */
    fun displayName(rawPayee: String): String =
        Normalizer.normalize(rawPayee, Normalizer.Form.NFC)
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(whitespaceRegex, " ")
            .trim()
            .trimEnd('.', ',', ';', ' ')

    /**
     * @return the rule key: [displayName] in lower case without trailing
     * terminal IDs (tokens with digits) and city/country tokens.
     * The first token is always kept.
     */
    fun normalize(rawPayee: String): String {
        val tokens = displayName(rawPayee)
            .lowercase(Locale.ROOT)
            .split(' ')
            .filter(String::isNotEmpty)
            .toMutableList()

        while (tokens.size > 1) {
            val last = tokens.last()
            if (last.any(Char::isDigit) || last in trailingTokens) {
                tokens.removeAt(tokens.lastIndex)
            } else {
                break
            }
        }

        return tokens.joinToString(" ")
    }

    /**
     * @return a user-typed pattern in the same form as [normalize] output,
     * but without removing any tokens.
     */
    fun normalizePattern(input: String): String =
        displayName(input).lowercase(Locale.ROOT)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.PayeeNormalizerTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox/logic/PayeeNormalizer.kt app/src/test/java/ua/com/radiokot/money/inbox/logic/PayeeNormalizerTest.kt
git commit -m "Add payee normalization"
```

---

### Task 4: SEB Latvia parser

**Files:**
- Create: `inbox/logic/BankNotificationParser.kt`, `inbox/logic/SebLatviaNotificationParser.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/SebLatviaNotificationParserTest.kt`

**Interfaces:**
- Consumes: `ParsedBankNotification` (Task 2).
- Produces: `interface BankNotificationParser { val packageName: String; fun parse(title: String?, text: String): ParsedBankNotification }`; `class SebLatviaNotificationParser : BankNotificationParser` with `companion object { const val PACKAGE_NAME = "se.seb.latvia" }`; `object BankNotificationSources { val packageNames: Set<String> }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import java.math.BigDecimal
import java.text.Normalizer

class SebLatviaNotificationParserTest {

    private val parser = SebLatviaNotificationParser()
    private val title = "Jauna rezervācija"
    private val sample = "Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA ."

    private fun parsePayment(text: String) =
        parser.parse(title, text) as ParsedBankNotification.CardPayment

    @Test
    fun sampledTemplate() {
        assertEquals(
            ParsedBankNotification.CardPayment(
                amount = BigDecimal("2.12"),
                currencyCode = "USD",
                cardLast4 = "0000",
                payee = "DEEPSEERWEA",
            ),
            parser.parse(title, sample),
        )
    }

    @Test
    fun euroIntegerAmount() {
        val payment = parsePayment("Jūs samaksājāt 15 EUR par 03/10/2026 18:40 karte...1111 CAFE EXAMPLE .")
        assertEquals(BigDecimal("15"), payment.amount)
        assertEquals("EUR", payment.currencyCode)
        assertEquals("1111", payment.cardLast4)
        assertEquals("CAFE EXAMPLE", payment.payee)
    }

    @Test
    fun thousandsSeparatorSpaceAndNbsp() {
        assertEquals(
            BigDecimal("1234.56"),
            parsePayment("Jūs samaksājāt 1 234,56 EUR par 03/10/2026 18:40 karte...0000 SHOP .").amount,
        )
        assertEquals(
            BigDecimal("1234.56"),
            parsePayment("Jūs samaksājāt 1\u00A0234,56\u00A0EUR par 03/10/2026 18:40 karte...0000 SHOP .").amount,
        )
    }

    @Test
    fun decomposedUnicodeIsParsed() {
        val decomposed = Normalizer.normalize(sample, Normalizer.Form.NFD)
        assertEquals("DEEPSEERWEA", parsePayment(decomposed).payee)
    }

    @Test
    fun ellipsisCharacterAndNoTrailingDot() {
        val payment = parsePayment("Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte…0000 DEEPSEERWEA")
        assertEquals("0000", payment.cardLast4)
        assertEquals("DEEPSEERWEA", payment.payee)
    }

    @Test
    fun lowerCaseCurrencyIsUpperCased() {
        assertEquals("USD", parsePayment(sample.replace("USD", "usd")).currencyCode)
    }

    @Test
    fun unknownTemplatesAreUnrecognized() {
        listOf(
            "Ienākošs maksājums 10,00 EUR no EXAMPLE SIA",
            "Jums ir jauns ziņojums internetbankā",
            "",
            "Jūs samaksājāt abc USD par karte...0000 X",
        ).forEach { text ->
            assertSame(text, ParsedBankNotification.Unrecognized, parser.parse(title, text))
        }
    }

    @Test
    fun sourcePackagesAreSebOnly() {
        assertEquals(setOf("se.seb.latvia"), BankNotificationSources.packageNames)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.SebLatviaNotificationParserTest"`
Expected: FAIL, `Unresolved reference 'SebLatviaNotificationParser'`.

- [ ] **Step 3: Write the implementation**

`inbox/logic/BankNotificationParser.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.ParsedBankNotification

interface BankNotificationParser {

    /**
     * The bank app package this parser handles.
     */
    val packageName: String

    /**
     * Must never throw: anything not matching a template is [ParsedBankNotification.Unrecognized].
     */
    fun parse(title: String?, text: String): ParsedBankNotification
}

/**
 * Packages the listener reacts to. Everything else (incl. Google Wallet duplicates) is ignored.
 */
object BankNotificationSources {
    val packageNames: Set<String> = setOf(
        SebLatviaNotificationParser.PACKAGE_NAME,
    )
}
```

`inbox/logic/SebLatviaNotificationParser.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/**
 * SEB Latvia push notifications.
 *
 * Sampled card payment (title / text):
 * ```
 * Jauna rezervācija
 * Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .
 * ```
 * The date/time in the text is not local time and is ignored.
 */
class SebLatviaNotificationParser : BankNotificationParser {

    override val packageName: String = PACKAGE_NAME

    override fun parse(title: String?, text: String): ParsedBankNotification {
        val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .trim()

        val match = CARD_PAYMENT_REGEX.matchEntire(normalizedText)
            ?: return ParsedBankNotification.Unrecognized

        val (amountString, currencyCode, cardLast4, payee) = match.destructured

        val amount = parseAmount(amountString)
            ?.takeIf { it.signum() > 0 }
            ?: return ParsedBankNotification.Unrecognized

        return ParsedBankNotification.CardPayment(
            amount = amount,
            currencyCode = currencyCode.uppercase(Locale.ROOT),
            cardLast4 = cardLast4,
            payee = payee.trim(),
        )
    }

    private fun parseAmount(amountString: String): BigDecimal? =
        amountString
            .replace(" ", "")
            .replace(',', '.')
            .toBigDecimalOrNull()

    companion object {
        const val PACKAGE_NAME = "se.seb.latvia"

        // 1: amount with decimal comma and optional space thousands separators,
        // 2: ISO currency, 3: card last 4, 4: payee (lazy, without the trailing " .").
        private val CARD_PAYMENT_REGEX = Regex(
            "^Jūs samaksājāt\\s+(\\d{1,3}(?: \\d{3})+(?:,\\d+)?|\\d+(?:,\\d+)?)\\s+([A-Za-z]{3})" +
                    "\\s+par\\s+\\S+\\s+\\S+\\s+karte\\s*(?:\\.{2,}|…)\\s*(\\d{4})\\s+(.+?)\\s*\\.?$",
            RegexOption.IGNORE_CASE,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.SebLatviaNotificationParserTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox/logic/BankNotificationParser.kt app/src/main/java/ua/com/radiokot/money/inbox/logic/SebLatviaNotificationParser.kt app/src/test/java/ua/com/radiokot/money/inbox/logic/SebLatviaNotificationParserTest.kt
git commit -m "Add SEB Latvia card payment notification parser"
```

---

### Task 5: Dedup hash

**Files:**
- Create: `inbox/logic/BankNotificationDedupHash.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/BankNotificationDedupHashTest.kt`

**Interfaces:**
- Consumes: `IncomingBankNotification` (Task 2).
- Produces: `object BankNotificationDedupHash { fun compute(notification: IncomingBankNotification, includePostTime: Boolean): String }` — 64 lower-case hex chars. Task 7 calls it with `includePostTime = parsed !is CardPayment`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.data.IncomingBankNotification

class BankNotificationDedupHashTest {

    private val notification = IncomingBankNotification(
        packageName = "se.seb.latvia",
        postTimeMillis = 1_790_000_000_000,
        title = "Jauna rezervācija",
        text = "Jūs samaksājāt 2,12 USD par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .",
    )

    @Test
    fun isStableHex() {
        val hash = BankNotificationDedupHash.compute(notification, includePostTime = false)
        assertEquals(hash, BankNotificationDedupHash.compute(notification.copy(), includePostTime = false))
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun repostWithNewPostTimeIsSameWhenPostTimeExcluded() {
        assertEquals(
            BankNotificationDedupHash.compute(notification, includePostTime = false),
            BankNotificationDedupHash.compute(notification.copy(postTimeMillis = 1_790_000_005_000), includePostTime = false),
        )
    }

    @Test
    fun postTimeMattersWhenIncluded() {
        assertNotEquals(
            BankNotificationDedupHash.compute(notification, includePostTime = true),
            BankNotificationDedupHash.compute(notification.copy(postTimeMillis = 1_790_000_005_000), includePostTime = true),
        )
    }

    @Test
    fun anyFieldChangeChangesHash() {
        val base = BankNotificationDedupHash.compute(notification, includePostTime = false)
        listOf(
            notification.copy(packageName = "other.bank"),
            notification.copy(title = null),
            notification.copy(text = notification.text.replace("2,12", "2,13")),
        ).forEach { changed ->
            assertNotEquals(base, BankNotificationDedupHash.compute(changed, includePostTime = false))
        }
    }

    @Test
    fun fieldBoundariesAreUnambiguous() {
        assertNotEquals(
            BankNotificationDedupHash.compute(notification.copy(title = "ab", text = "c"), includePostTime = false),
            BankNotificationDedupHash.compute(notification.copy(title = "a", text = "bc"), includePostTime = false),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.BankNotificationDedupHashTest"`
Expected: FAIL, `Unresolved reference 'BankNotificationDedupHash'`.

- [ ] **Step 3: Write the implementation**

`inbox/logic/BankNotificationDedupHash.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Banks re-post and update notifications; the same payment must not be recorded twice.
 *
 * Recognized payments carry their own minute, amount, card and payee in the text,
 * so (package, title, text) identifies them across re-posts with a new post time or ID.
 * Unrecognized texts carry no timestamp, so the post time is added to keep
 * repeated identical texts on different occasions apart.
 */
object BankNotificationDedupHash {

    fun compute(
        notification: IncomingBankNotification,
        includePostTime: Boolean,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")

        listOf(
            notification.packageName,
            notification.title ?: "",
            notification.text,
            if (includePostTime) notification.postTimeMillis.toString() else "",
        ).forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            // Length prefix keeps ("ab", "c") and ("a", "bc") apart.
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }

        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.BankNotificationDedupHashTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox/logic/BankNotificationDedupHash.kt app/src/test/java/ua/com/radiokot/money/inbox/logic/BankNotificationDedupHashTest.kt
git commit -m "Add bank notification dedup hash"
```

---

### Task 6: Rule matcher and auto-expense resolver

**Files:**
- Create: `inbox/logic/PayeeRuleMatcher.kt`, `inbox/logic/AutoExpenseResolver.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/PayeeRuleMatcherTest.kt`, `app/src/test/java/ua/com/radiokot/money/inbox/logic/AutoExpenseResolverTest.kt`

**Interfaces:**
- Consumes: `PayeeRule`, `ParsedBankNotification.CardPayment` (Task 2).
- Produces:
  - `object PayeeRuleMatcher { fun match(normalizedPayee: String, rules: List<PayeeRule>): PayeeRule? }` — exact (most hits wins) before contains (longest pattern, then most hits).
  - `object AutoExpenseResolver` with `data class AccountRef(id: String, currencyCode: String, precision: Int)`, `data class CategoryRef(categoryId: String, subcategoryId: String?, currencyCode: String, precision: Int)`, `enum class PendingReason { NotParsed, NoRule, NoAccount, ForeignCurrency, CategoryMissing, CategoryCurrencyMismatch, UnsupportedPrecision }`, `sealed interface Resolution { data class Create(rule: PayeeRule, account: AccountRef, category: CategoryRef, sourceAmount: BigInteger, destinationAmount: BigInteger); data class Pending(reason: PendingReason) }`, `fun resolve(payment: CardPayment?, rule: PayeeRule?, account: AccountRef?, category: CategoryRef?): Resolution`, `fun toMinorUnits(amount: BigDecimal, precision: Int): BigInteger?`.

- [ ] **Step 1: Write the failing matcher test**

```kotlin
package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.PayeeRule

class PayeeRuleMatcherTest {

    private fun rule(
        pattern: String,
        matchType: PayeeRule.MatchType,
        hits: Long = 0,
        id: String = pattern + matchType,
    ) = PayeeRule(
        payeePattern = pattern,
        matchType = matchType,
        categoryId = "cat-$id",
        subcategoryId = null,
        accountId = null,
        hits = hits,
        lastUsedAt = null,
        id = id,
    )

    @Test
    fun exactBeatsContains() {
        val exact = rule("rimi hyper", PayeeRule.MatchType.Exact)
        val contains = rule("rimi", PayeeRule.MatchType.Contains, hits = 100)
        assertEquals(exact, PayeeRuleMatcher.match("rimi hyper", listOf(contains, exact)))
    }

    @Test
    fun longestContainsWins() {
        val short = rule("wolt", PayeeRule.MatchType.Contains, hits = 50)
        val long = rule("wolt market", PayeeRule.MatchType.Contains)
        assertEquals(long, PayeeRuleMatcher.match("wolt market riga centrs", listOf(short, long)))
        assertEquals(short, PayeeRuleMatcher.match("wolt food", listOf(short, long)))
    }

    @Test
    fun exactTieBreaksByHits() {
        val a = rule("cafe", PayeeRule.MatchType.Exact, hits = 1, id = "a")
        val b = rule("cafe", PayeeRule.MatchType.Exact, hits = 5, id = "b")
        assertEquals(b, PayeeRuleMatcher.match("cafe", listOf(a, b)))
    }

    @Test
    fun noMatch() {
        val rules = listOf(
            rule("rimi", PayeeRule.MatchType.Exact),
            rule("maxima", PayeeRule.MatchType.Contains),
        )
        assertNull(PayeeRuleMatcher.match("rimi hyper", rules))
        assertNull(PayeeRuleMatcher.match("", rules))
        assertNull(PayeeRuleMatcher.match("anything", emptyList()))
    }
}
```

- [ ] **Step 2: Write the failing resolver test**

```kotlin
package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.AccountRef
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.CategoryRef
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.PendingReason
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.Resolution
import java.math.BigDecimal
import java.math.BigInteger

class AutoExpenseResolverTest {

    private val payment = ParsedBankNotification.CardPayment(
        amount = BigDecimal("2.12"),
        currencyCode = "EUR",
        cardLast4 = "0000",
        payee = "DEEPSEERWEA",
    )
    private val rule = PayeeRule(
        payeePattern = "deepseerwea",
        matchType = PayeeRule.MatchType.Exact,
        categoryId = "cat",
        subcategoryId = "sub",
        accountId = null,
        hits = 0,
        lastUsedAt = null,
        id = "rule",
    )
    private val eurAccount = AccountRef(id = "acc", currencyCode = "EUR", precision = 2)
    private val eurCategory = CategoryRef(categoryId = "cat", subcategoryId = "sub", currencyCode = "EUR", precision = 2)

    @Test
    fun createsInMinorUnits() {
        assertEquals(
            Resolution.Create(
                rule = rule,
                account = eurAccount,
                category = eurCategory,
                sourceAmount = BigInteger("212"),
                destinationAmount = BigInteger("212"),
            ),
            AutoExpenseResolver.resolve(payment, rule, eurAccount, eurCategory),
        )
    }

    @Test
    fun pendingReasons() {
        assertEquals(Resolution.Pending(PendingReason.NotParsed), AutoExpenseResolver.resolve(null, rule, eurAccount, eurCategory))
        assertEquals(Resolution.Pending(PendingReason.NoRule), AutoExpenseResolver.resolve(payment, null, eurAccount, null))
        assertEquals(Resolution.Pending(PendingReason.NoAccount), AutoExpenseResolver.resolve(payment, rule, null, eurCategory))
        assertEquals(Resolution.Pending(PendingReason.CategoryMissing), AutoExpenseResolver.resolve(payment, rule, eurAccount, null))
    }

    @Test
    fun foreignCurrencyIsAlwaysPending() {
        val usdPayment = payment.copy(currencyCode = "USD")
        assertEquals(
            Resolution.Pending(PendingReason.ForeignCurrency),
            AutoExpenseResolver.resolve(usdPayment, rule, eurAccount, eurCategory),
        )
    }

    @Test
    fun categoryInOtherCurrencyIsPending() {
        assertEquals(
            Resolution.Pending(PendingReason.CategoryCurrencyMismatch),
            AutoExpenseResolver.resolve(payment, rule, eurAccount, eurCategory.copy(currencyCode = "USD")),
        )
    }

    @Test
    fun tooManyDecimalsIsPendingNotRounded() {
        assertEquals(
            Resolution.Pending(PendingReason.UnsupportedPrecision),
            AutoExpenseResolver.resolve(payment.copy(amount = BigDecimal("2.125")), rule, eurAccount, eurCategory),
        )
    }

    @Test
    fun minorUnits() {
        assertEquals(BigInteger("1500"), AutoExpenseResolver.toMinorUnits(BigDecimal("15"), 2))
        assertEquals(BigInteger("210"), AutoExpenseResolver.toMinorUnits(BigDecimal("2.1"), 2))
        assertEquals(BigInteger("123456"), AutoExpenseResolver.toMinorUnits(BigDecimal("1234.56"), 2))
        assertNull(AutoExpenseResolver.toMinorUnits(BigDecimal("2.125"), 2))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.PayeeRuleMatcherTest" --tests "ua.com.radiokot.money.inbox.logic.AutoExpenseResolverTest"`
Expected: FAIL, `Unresolved reference 'PayeeRuleMatcher'` / `'AutoExpenseResolver'`.

- [ ] **Step 4: Write the implementations**

`inbox/logic/PayeeRuleMatcher.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.PayeeRule

object PayeeRuleMatcher {

    /**
     * @param normalizedPayee output of [PayeeNormalizer.normalize]
     *
     * @return the best rule: an exact match (most hits first),
     * otherwise the longest "contains" pattern (then most hits), otherwise null.
     */
    fun match(
        normalizedPayee: String,
        rules: List<PayeeRule>,
    ): PayeeRule? {
        if (normalizedPayee.isEmpty()) {
            return null
        }

        val exactMatch = rules
            .filter { rule ->
                rule.matchType == PayeeRule.MatchType.Exact
                        && rule.payeePattern == normalizedPayee
            }
            .maxByOrNull(PayeeRule::hits)

        if (exactMatch != null) {
            return exactMatch
        }

        return rules
            .filter { rule ->
                rule.matchType == PayeeRule.MatchType.Contains
                        && rule.payeePattern.isNotEmpty()
                        && normalizedPayee.contains(rule.payeePattern)
            }
            .maxWithOrNull(
                compareBy<PayeeRule> { it.payeePattern.length }
                    .thenBy(PayeeRule::hits)
            )
    }
}
```

`inbox/logic/AutoExpenseResolver.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Decides whether a parsed payment can become an expense without the user.
 */
object AutoExpenseResolver {

    data class AccountRef(
        val id: String,
        val currencyCode: String,
        val precision: Int,
    )

    data class CategoryRef(
        val categoryId: String,
        val subcategoryId: String?,
        val currencyCode: String,
        val precision: Int,
    )

    enum class PendingReason {
        NotParsed,
        NoRule,
        NoAccount,
        ForeignCurrency,
        CategoryMissing,
        CategoryCurrencyMismatch,
        UnsupportedPrecision,
    }

    sealed interface Resolution {

        data class Create(
            val rule: PayeeRule,
            val account: AccountRef,
            val category: CategoryRef,
            val sourceAmount: BigInteger,
            val destinationAmount: BigInteger,
        ) : Resolution

        data class Pending(
            val reason: PendingReason,
        ) : Resolution
    }

    /**
     * @param payment null if the notification was not recognized
     * @param rule matched rule, if any
     * @param account resolved, existing, non-archived account, if any
     * @param category resolved, existing, non-archived rule category, if any
     */
    fun resolve(
        payment: ParsedBankNotification.CardPayment?,
        rule: PayeeRule?,
        account: AccountRef?,
        category: CategoryRef?,
    ): Resolution {
        if (payment == null || payment.amount.signum() <= 0) {
            return Resolution.Pending(PendingReason.NotParsed)
        }
        if (rule == null) {
            return Resolution.Pending(PendingReason.NoRule)
        }
        if (account == null) {
            return Resolution.Pending(PendingReason.NoAccount)
        }
        if (!payment.currencyCode.equals(account.currencyCode, ignoreCase = true)) {
            return Resolution.Pending(PendingReason.ForeignCurrency)
        }
        if (category == null) {
            return Resolution.Pending(PendingReason.CategoryMissing)
        }
        if (!category.currencyCode.equals(account.currencyCode, ignoreCase = true)) {
            return Resolution.Pending(PendingReason.CategoryCurrencyMismatch)
        }

        val sourceAmount = toMinorUnits(payment.amount, account.precision)
        val destinationAmount = toMinorUnits(payment.amount, category.precision)
        if (sourceAmount == null || destinationAmount == null) {
            return Resolution.Pending(PendingReason.UnsupportedPrecision)
        }

        return Resolution.Create(
            rule = rule,
            account = account,
            category = category,
            sourceAmount = sourceAmount,
            destinationAmount = destinationAmount,
        )
    }

    /**
     * @return [amount] in minor units of a currency with [precision],
     * or null if it has more decimals than the currency allows.
     */
    fun toMinorUnits(
        amount: BigDecimal,
        precision: Int,
    ): BigInteger? =
        try {
            amount.movePointRight(precision).toBigIntegerExact()
        } catch (_: ArithmeticException) {
            null
        }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.PayeeRuleMatcherTest" --tests "ua.com.radiokot.money.inbox.logic.AutoExpenseResolverTest"`
Expected: PASS (4 + 6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox/logic/PayeeRuleMatcher.kt app/src/main/java/ua/com/radiokot/money/inbox/logic/AutoExpenseResolver.kt app/src/test/java/ua/com/radiokot/money/inbox/logic/PayeeRuleMatcherTest.kt app/src/test/java/ua/com/radiokot/money/inbox/logic/AutoExpenseResolverTest.kt
git commit -m "Add payee rule matching and auto-expense resolution"
```

---
### Task 7: Processing use case (parse → dedup → match → expense or inbox)

**Files:**
- Modify: `transfers/logic/TransferFundsUseCase.kt`, `transfers/logic/PowerSyncTransferFundsUseCase.kt`
- Create: `inbox/data/InboxRepository.kt`, `inbox/data/PayeeRuleRepository.kt`, `inbox/logic/CardAccountResolver.kt`, `inbox/logic/ProcessBankNotificationUseCase.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/InboxTestFixtures.kt`, `app/src/test/java/ua/com/radiokot/money/inbox/logic/ProcessBankNotificationUseCaseTest.kt`

**Interfaces:**
- Consumes: Tasks 2–6 (`InboxItem`, `PayeeRule`, `IncomingBankNotification`, `ParsedBankNotification`, `BankNotificationParser`, `PayeeNormalizer`, `BankNotificationDedupHash`, `PayeeRuleMatcher`, `AutoExpenseResolver`); existing `AccountRepository`, `CategoryRepository`, `TransferFundsUseCase`, `TransferCounterpartyId`.
- Produces:
  - `TransferFundsUseCase.invoke(sourceId, sourceAmount, destinationId, destinationAmount, memo, dateTime, transferId: String = UUID.randomUUID().toString()): Result<Unit>` — existing callers compile unchanged.
  - `interface InboxRepository { suspend fun existsWithDedupHash(dedupHash: String): Boolean; suspend fun addItem(item: InboxItem); suspend fun getItem(itemId: String): InboxItem?; fun getPendingItemsFlow(): Flow<List<InboxItem>>; fun getRecentDoneItemsFlow(limit: Int): Flow<List<InboxItem>>; fun getPendingCountFlow(): Flow<Long>; fun getKnownCardLast4Flow(): Flow<List<String>>; suspend fun markDone(itemId: String, transferId: String); suspend fun markPending(itemId: String); suspend fun dismiss(itemId: String) }`
  - `interface PayeeRuleRepository { suspend fun getRules(): List<PayeeRule>; fun getRulesFlow(): Flow<List<PayeeRule>>; suspend fun saveRuleForPayee(payeePattern: String, matchType: PayeeRule.MatchType, categoryId: String, subcategoryId: String?, accountId: String?); suspend fun updateRule(ruleId: String, payeePattern: String, matchType: PayeeRule.MatchType); suspend fun deleteRule(ruleId: String); suspend fun recordHit(ruleId: String, at: LocalDateTime) }`
  - `interface CardAccountResolver { suspend fun resolve(cardLast4: String?, ruleAccountId: String?): String? }`
  - `class ProcessBankNotificationUseCase(parsers, inboxRepository, payeeRuleRepository, accountRepository, categoryRepository, cardAccountResolver, transferFundsUseCase, timeZone = TimeZone.currentSystemDefault(), newId = { UUID }) { suspend operator fun invoke(notification: IncomingBankNotification): Result<Outcome> }` with `sealed interface Outcome { Ignored; Duplicate; AutoRecorded(itemId, transferId, ruleId); Pending(itemId, reason) }`. Must be bound `scoped` (one instance per session) so its mutex is shared.

- [ ] **Step 1: Let callers choose the transfer ID**

Replace the `invoke` declaration in `transfers/logic/TransferFundsUseCase.kt`:

```kotlin
package ua.com.radiokot.money.transfers.logic

import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigInteger
import java.util.UUID

interface TransferFundsUseCase {

    /**
     * Executes the transfer and adds it to the history.
     *
     * @param sourceId source of the funds
     * @param sourceAmount what amount is consumed with this transfer from the source.
     * If [sourceId] is an account, the amount is deducted from its balance
     * @param destinationId destination of the funds
     * @param destinationAmount what amount is produced with this transfer for the destination.
     * If [destinationId] is an account, the amount is added to its balance
     * @param memo a text note to add to the transfer
     * @param dateTime a local date and time at which the transfer occurred
     * @param transferId ID of the new transfer, for callers that need to reference it
     */
    suspend operator fun invoke(
        sourceId: TransferCounterpartyId,
        sourceAmount: BigInteger,
        destinationId: TransferCounterpartyId,
        destinationAmount: BigInteger,
        memo: String?,
        dateTime: LocalDateTime,
        transferId: String = UUID.randomUUID().toString(),
    ): Result<Unit>
}
```

In `transfers/logic/PowerSyncTransferFundsUseCase.kt` add the parameter to the override (no default value on overrides) and pass it on:

```kotlin
    override suspend fun invoke(
        sourceId: TransferCounterpartyId,
        sourceAmount: BigInteger,
        destinationId: TransferCounterpartyId,
        destinationAmount: BigInteger,
        memo: String?,
        dateTime: LocalDateTime,
        transferId: String,
    ): Result<Unit> = runCatching {
```

and in the `transferHistoryRepository.addOrUpdateTransfer(` call add the last argument:

```kotlin
                metadata = AtomicCrudSupabaseConnector.SPECIAL_TRANSACTION_TRANSFER,
                transaction = transaction,
                transferId = transferId,
            )
```

- [ ] **Step 2: Create the repository and resolver interfaces**

`inbox/data/InboxRepository.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import kotlinx.coroutines.flow.Flow

interface InboxRepository {

    suspend fun existsWithDedupHash(dedupHash: String): Boolean

    suspend fun addItem(item: InboxItem)

    suspend fun getItem(itemId: String): InboxItem?

    /**
     * @return pending items, newest first.
     */
    fun getPendingItemsFlow(): Flow<List<InboxItem>>

    /**
     * @return at most [limit] done items, newest first.
     */
    fun getRecentDoneItemsFlow(limit: Int): Flow<List<InboxItem>>

    fun getPendingCountFlow(): Flow<Long>

    /**
     * @return distinct card last 4 digits seen in the inbox, sorted.
     */
    fun getKnownCardLast4Flow(): Flow<List<String>>

    suspend fun markDone(itemId: String, transferId: String)

    /**
     * Returns an item to pending and forgets its transfer, e.g. after undo.
     */
    suspend fun markPending(itemId: String)

    suspend fun dismiss(itemId: String)
}
```

`inbox/data/PayeeRuleRepository.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface PayeeRuleRepository {

    /**
     * @return all rules from the in-memory cache, refreshed on every rule change.
     */
    suspend fun getRules(): List<PayeeRule>

    fun getRulesFlow(): Flow<List<PayeeRule>>

    /**
     * Creates a rule or, if one with the same pattern and match type exists,
     * points it to the new category/account.
     */
    suspend fun saveRuleForPayee(
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        categoryId: String,
        subcategoryId: String?,
        accountId: String?,
    )

    suspend fun updateRule(
        ruleId: String,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
    )

    suspend fun deleteRule(ruleId: String)

    suspend fun recordHit(
        ruleId: String,
        at: LocalDateTime,
    )
}
```

`inbox/logic/CardAccountResolver.kt` (the default implementation is added in Task 8):

```kotlin
package ua.com.radiokot.money.inbox.logic

interface CardAccountResolver {

    /**
     * @return the account to use: the account mapped to [cardLast4] in settings,
     * otherwise [ruleAccountId], otherwise the most used account.
     * The caller must check the account still exists and is not archived.
     */
    suspend fun resolve(
        cardLast4: String?,
        ruleAccountId: String?,
    ): String?
}
```

- [ ] **Step 3: Write the test fixtures**

`app/src/test/java/ua/com/radiokot/money/inbox/InboxTestFixtures.kt`:

```kotlin
package ua.com.radiokot.money.inbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.categories.data.Subcategory
import ua.com.radiokot.money.colors.data.ItemColorScheme
import ua.com.radiokot.money.currency.data.Amount
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import java.math.BigInteger

val EUR = Currency(code = "EUR", symbol = "€", precision = 2, id = "cur-eur")
val USD = Currency(code = "USD", symbol = "$", precision = 2, id = "cur-usd")
private val colorScheme = ItemColorScheme(name = "Blue1", primary = 0, onPrimary = 0)

fun testAccount(
    id: String,
    currency: Currency = EUR,
    isArchived: Boolean = false,
) = Account(
    title = "Account $id",
    balance = Amount(value = BigInteger.ZERO, currency = currency),
    position = 0.0,
    colorScheme = colorScheme,
    icon = null,
    type = Account.Type.Regular,
    isArchived = isArchived,
    id = id,
)

fun testCategory(
    id: String,
    currency: Currency = EUR,
    isArchived: Boolean = false,
) = Category(
    title = "Category $id",
    currency = currency,
    isIncome = false,
    colorScheme = colorScheme,
    icon = null,
    isArchived = isArchived,
    position = 0.0,
    id = id,
)

class FakeInboxRepository : InboxRepository {
    val items = MutableStateFlow<List<InboxItem>>(emptyList())

    override suspend fun existsWithDedupHash(dedupHash: String) =
        items.value.any { it.dedupHash == dedupHash }

    override suspend fun addItem(item: InboxItem) {
        items.value += item
    }

    override suspend fun getItem(itemId: String) =
        items.value.find { it.id == itemId }

    override fun getPendingItemsFlow(): Flow<List<InboxItem>> =
        items.map { list -> list.filter { it.status == InboxItem.Status.Pending } }

    override fun getRecentDoneItemsFlow(limit: Int): Flow<List<InboxItem>> =
        items.map { list -> list.filter { it.status == InboxItem.Status.Done }.take(limit) }

    override fun getPendingCountFlow(): Flow<Long> =
        getPendingItemsFlow().map { it.size.toLong() }

    override fun getKnownCardLast4Flow(): Flow<List<String>> =
        items.map { list -> list.mapNotNull(InboxItem::cardLast4).distinct().sorted() }

    override suspend fun markDone(itemId: String, transferId: String) =
        update(itemId) { it.copy(status = InboxItem.Status.Done, transferId = transferId) }

    override suspend fun markPending(itemId: String) =
        update(itemId) { it.copy(status = InboxItem.Status.Pending, transferId = null) }

    override suspend fun dismiss(itemId: String) =
        update(itemId) { it.copy(status = InboxItem.Status.Dismissed) }

    private fun update(itemId: String, transform: (InboxItem) -> InboxItem) {
        items.value = items.value.map { if (it.id == itemId) transform(it) else it }
    }
}

class FakePayeeRuleRepository(
    initialRules: List<PayeeRule> = emptyList(),
) : PayeeRuleRepository {
    val rules = MutableStateFlow(initialRules)
    val hits = mutableListOf<Pair<String, LocalDateTime>>()

    override suspend fun getRules() = rules.value

    override fun getRulesFlow(): Flow<List<PayeeRule>> = rules

    override suspend fun saveRuleForPayee(
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        categoryId: String,
        subcategoryId: String?,
        accountId: String?,
    ) {
        val existing = rules.value.find { it.payeePattern == payeePattern && it.matchType == matchType }
        rules.value =
            if (existing != null)
                rules.value.map {
                    if (it == existing)
                        it.copy(categoryId = categoryId, subcategoryId = subcategoryId, accountId = accountId)
                    else
                        it
                }
            else
                rules.value + PayeeRule(
                    payeePattern = payeePattern,
                    matchType = matchType,
                    categoryId = categoryId,
                    subcategoryId = subcategoryId,
                    accountId = accountId,
                    hits = 0,
                    lastUsedAt = null,
                )
    }

    override suspend fun updateRule(ruleId: String, payeePattern: String, matchType: PayeeRule.MatchType) {
        rules.value = rules.value.map {
            if (it.id == ruleId) it.copy(payeePattern = payeePattern, matchType = matchType) else it
        }
    }

    override suspend fun deleteRule(ruleId: String) {
        rules.value = rules.value.filterNot { it.id == ruleId }
    }

    override suspend fun recordHit(ruleId: String, at: LocalDateTime) {
        hits += ruleId to at
    }
}

class FakeAccountRepository(
    private val accounts: List<Account>,
) : AccountRepository {
    override suspend fun getAccounts() = accounts
    override fun getAccountsFlow(): Flow<List<Account>> = MutableStateFlow(accounts)
    override suspend fun getAccount(accountId: String) = accounts.find { it.id == accountId }
    override fun getAccountFlow(accountId: String): Flow<Account> =
        MutableStateFlow(accounts.first { it.id == accountId })

    override suspend fun updateBalance(accountId: String, newValue: BigInteger) = error("Not used")
    override suspend fun archive(accountId: String) = error("Not used")
    override suspend fun unarchive(accountId: String, newPosition: Double) = error("Not used")
}

class FakeCategoryRepository(
    private val categories: List<Category>,
    private val subcategories: List<Subcategory> = emptyList(),
) : CategoryRepository {
    override suspend fun getCategories(isIncome: Boolean) = categories.filter { it.isIncome == isIncome }
    override fun getCategoriesFlow(isIncome: Boolean): Flow<List<Category>> =
        MutableStateFlow(categories.filter { it.isIncome == isIncome })

    override suspend fun getCategory(categoryId: String) = categories.find { it.id == categoryId }
    override suspend fun getSubcategory(subcategoryId: String) = subcategories.find { it.id == subcategoryId }
    override fun getSubcategoriesFlow(categoryId: String): Flow<List<Subcategory>> =
        MutableStateFlow(subcategories.filter { it.categoryId == categoryId })

    override fun getSubcategoriesByCategoriesFlow(): Flow<Map<Category, List<Subcategory>>> =
        MutableStateFlow(categories.associateWith { category -> subcategories.filter { it.categoryId == category.id } })

    override suspend fun archiveCategory(categoryId: String) = error("Not used")
    override suspend fun unarchiveCategory(categoryId: String, newPosition: Double) = error("Not used")
}

class RecordingTransferFundsUseCase : TransferFundsUseCase {

    data class Call(
        val sourceId: TransferCounterpartyId,
        val sourceAmount: BigInteger,
        val destinationId: TransferCounterpartyId,
        val destinationAmount: BigInteger,
        val memo: String?,
        val dateTime: LocalDateTime,
        val transferId: String,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        sourceId: TransferCounterpartyId,
        sourceAmount: BigInteger,
        destinationId: TransferCounterpartyId,
        destinationAmount: BigInteger,
        memo: String?,
        dateTime: LocalDateTime,
        transferId: String,
    ): Result<Unit> {
        calls += Call(sourceId, sourceAmount, destinationId, destinationAmount, memo, dateTime, transferId)
        return Result.success(Unit)
    }
}
```

- [ ] **Step 4: Write the failing use case test**

`app/src/test/java/ua/com/radiokot/money/inbox/logic/ProcessBankNotificationUseCaseTest.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.inbox.FakeAccountRepository
import ua.com.radiokot.money.inbox.FakeCategoryRepository
import ua.com.radiokot.money.inbox.FakeInboxRepository
import ua.com.radiokot.money.inbox.FakePayeeRuleRepository
import ua.com.radiokot.money.inbox.RecordingTransferFundsUseCase
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.AutoExpenseResolver.PendingReason
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase.Outcome
import ua.com.radiokot.money.inbox.testAccount
import ua.com.radiokot.money.inbox.testCategory
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProcessBankNotificationUseCaseTest {

    private val postTime = Instant.parse("2026-10-02T08:06:00Z").toEpochMilliseconds()
    private val eurPayment = IncomingBankNotification(
        packageName = "se.seb.latvia",
        postTimeMillis = postTime,
        title = "Jauna rezervācija",
        text = "Jūs samaksājāt 2,12 EUR par 02/10/2026 05:06 karte...0000 DEEPSEERWEA .",
    )
    private val rule = PayeeRule(
        payeePattern = "deepseerwea",
        matchType = PayeeRule.MatchType.Exact,
        categoryId = "cat-food",
        subcategoryId = null,
        accountId = null,
        hits = 3,
        lastUsedAt = null,
        id = "rule-1",
    )

    private val inbox = FakeInboxRepository()
    private val transfers = RecordingTransferFundsUseCase()
    private var nextId = 0

    private fun useCase(
        rules: List<PayeeRule> = listOf(rule),
        categories: List<Category> = listOf(testCategory("cat-food")),
        ruleRepository: FakePayeeRuleRepository = FakePayeeRuleRepository(rules),
    ) = ProcessBankNotificationUseCase(
        parsers = listOf(SebLatviaNotificationParser()),
        inboxRepository = inbox,
        payeeRuleRepository = ruleRepository,
        accountRepository = FakeAccountRepository(listOf(testAccount("acc-main"))),
        categoryRepository = FakeCategoryRepository(categories),
        cardAccountResolver = object : CardAccountResolver {
            override suspend fun resolve(cardLast4: String?, ruleAccountId: String?) =
                ruleAccountId ?: "acc-main"
        },
        transferFundsUseCase = transfers,
        timeZone = TimeZone.UTC,
        newId = { "id-${nextId++}" },
    )

    @Test
    fun autoRecordsWhenRuleMatches() = runBlocking {
        val ruleRepository = FakePayeeRuleRepository(listOf(rule))

        val outcome = useCase(ruleRepository = ruleRepository).invoke(eurPayment).getOrThrow()

        val call = transfers.calls.single()
        assertEquals(Outcome.AutoRecorded(itemId = "id-0", transferId = call.transferId, ruleId = "rule-1"), outcome)
        assertEquals(TransferCounterpartyId.Account("acc-main"), call.sourceId)
        assertEquals(TransferCounterpartyId.Category("cat-food", null), call.destinationId)
        assertEquals(BigInteger("212"), call.sourceAmount)
        assertEquals(BigInteger("212"), call.destinationAmount)
        assertEquals("DEEPSEERWEA", call.memo)
        // Post time, not the time in the text.
        assertEquals(LocalDateTime(2026, 10, 2, 8, 6), call.dateTime)

        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Done, item.status)
        assertEquals(call.transferId, item.transferId)
        assertEquals("rule-1" to LocalDateTime(2026, 10, 2, 8, 6), ruleRepository.hits.single())
    }

    @Test
    fun pendingWithoutRule() = runBlocking {
        val outcome = useCase(rules = emptyList()).invoke(eurPayment).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.NoRule), outcome)
        assertTrue(transfers.calls.isEmpty())
        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Pending, item.status)
        assertEquals(BigDecimal("2.12"), item.amount)
        assertEquals("EUR", item.currencyCode)
        assertEquals("DEEPSEERWEA", item.payee)
        assertEquals("0000", item.cardLast4)
        assertEquals("acc-main", item.accountId)
        assertNull(item.transferId)
    }

    @Test
    fun foreignCurrencyGoesToPending() = runBlocking {
        val usdPayment = eurPayment.copy(text = eurPayment.text.replace("EUR", "USD"))

        val outcome = useCase().invoke(usdPayment).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.ForeignCurrency), outcome)
        assertTrue(transfers.calls.isEmpty())
        assertEquals("USD", inbox.items.value.single().currencyCode)
    }

    @Test
    fun unrecognizedSebTextIsKeptRaw() = runBlocking {
        val other = eurPayment.copy(title = "SEB", text = "Jums ir jauns ziņojums internetbankā")

        val outcome = useCase().invoke(other).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.NotParsed), outcome)
        val item = inbox.items.value.single()
        assertEquals("SEB\nJums ir jauns ziņojums internetbankā", item.rawText)
        assertNull(item.amount)
        assertNull(item.payee)
    }

    @Test
    fun duplicateIsSkipped() = runBlocking {
        val processor = useCase()

        processor(eurPayment).getOrThrow()
        val repost = processor(eurPayment.copy(postTimeMillis = postTime + 5_000)).getOrThrow()

        assertEquals(Outcome.Duplicate, repost)
        assertEquals(1, inbox.items.value.size)
        assertEquals(1, transfers.calls.size)
    }

    @Test
    fun otherPackagesAreIgnored() = runBlocking {
        val wallet = eurPayment.copy(packageName = "com.google.android.apps.walletnfcrel")

        assertEquals(Outcome.Ignored, useCase().invoke(wallet).getOrThrow())
        assertTrue(inbox.items.value.isEmpty())
    }

    @Test
    fun archivedRuleCategoryGoesToPending() = runBlocking {
        val outcome = useCase(categories = listOf(testCategory("cat-food", isArchived = true)))
            .invoke(eurPayment).getOrThrow()

        assertEquals(Outcome.Pending(itemId = "id-0", reason = PendingReason.CategoryMissing), outcome)
        assertTrue(transfers.calls.isEmpty())
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCaseTest"`
Expected: FAIL, `Unresolved reference 'ProcessBankNotificationUseCase'`.

- [ ] **Step 6: Write the use case**

`inbox/logic/ProcessBankNotificationUseCase.kt` (no logging here on purpose: it stays a plain JVM unit; the listener logs the outcome):

```kotlin
package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.categories.data.CategoryRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.data.ParsedBankNotification
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.logic.TransferFundsUseCase
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Turns a bank notification into an expense (rule matched) or a pending inbox item.
 * Local writes only. Bind as a single instance per session: [mutex] serializes
 * the dedup check and the writes for notifications arriving at once.
 */
class ProcessBankNotificationUseCase(
    private val parsers: List<BankNotificationParser>,
    private val inboxRepository: InboxRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val cardAccountResolver: CardAccountResolver,
    private val transferFundsUseCase: TransferFundsUseCase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    sealed interface Outcome {
        data object Ignored : Outcome
        data object Duplicate : Outcome

        data class AutoRecorded(
            val itemId: String,
            val transferId: String,
            val ruleId: String,
        ) : Outcome

        data class Pending(
            val itemId: String,
            val reason: AutoExpenseResolver.PendingReason,
        ) : Outcome
    }

    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        notification: IncomingBankNotification,
    ): Result<Outcome> = runCatching {

        val parser = parsers.firstOrNull { it.packageName == notification.packageName }
            ?: return@runCatching Outcome.Ignored

        val payment = parser.parse(notification.title, notification.text)
                as? ParsedBankNotification.CardPayment
        val dedupHash = BankNotificationDedupHash.compute(
            notification = notification,
            includePostTime = payment == null,
        )

        mutex.withLock {
            if (inboxRepository.existsWithDedupHash(dedupHash)) {
                return@withLock Outcome.Duplicate
            }

            val receivedAt = Instant
                .fromEpochMilliseconds(notification.postTimeMillis)
                .toLocalDateTime(timeZone)

            val accountsById = accountRepository
                .getAccounts()
                .filterNot(Account::isArchived)
                .associateBy(Account::id)

            val rule: PayeeRule? = payment
                ?.payee
                ?.let(PayeeNormalizer::normalize)
                ?.let { normalizedPayee ->
                    PayeeRuleMatcher.match(normalizedPayee, payeeRuleRepository.getRules())
                }

            val account: Account? = cardAccountResolver
                .resolve(
                    cardLast4 = payment?.cardLast4,
                    ruleAccountId = rule?.accountId,
                )
                ?.let(accountsById::get)

            val resolution = AutoExpenseResolver.resolve(
                payment = payment,
                rule = rule,
                account = account?.let { acc ->
                    AutoExpenseResolver.AccountRef(
                        id = acc.id,
                        currencyCode = acc.currency.code,
                        precision = acc.currency.precision,
                    )
                },
                category = rule?.let { getCategoryRef(it) },
            )

            val item = InboxItem(
                id = newId(),
                receivedAt = receivedAt,
                sourcePackage = notification.packageName,
                rawText = listOfNotNull(notification.title, notification.text).joinToString("\n"),
                amount = payment?.amount,
                currencyCode = payment?.currencyCode,
                payee = payment?.payee,
                cardLast4 = payment?.cardLast4,
                accountId = account?.id,
                status = InboxItem.Status.Pending,
                transferId = null,
                dedupHash = dedupHash,
            )

            when (resolution) {
                is AutoExpenseResolver.Resolution.Create -> {
                    val transferId = newId()

                    // A separate PowerSync transaction on purpose: the connector uploads
                    // a transaction containing a transfer through the `transfer` RPC
                    // and drops its other rows, so the inbox item must not share it.
                    transferFundsUseCase(
                        sourceId = TransferCounterpartyId.Account(resolution.account.id),
                        sourceAmount = resolution.sourceAmount,
                        destinationId = TransferCounterpartyId.Category(
                            categoryId = resolution.category.categoryId,
                            subcategoryId = resolution.category.subcategoryId,
                        ),
                        destinationAmount = resolution.destinationAmount,
                        memo = PayeeNormalizer.displayName(requireNotNull(payment).payee),
                        dateTime = receivedAt,
                        transferId = transferId,
                    ).getOrThrow()

                    inboxRepository.addItem(
                        item.copy(
                            status = InboxItem.Status.Done,
                            transferId = transferId,
                        )
                    )
                    payeeRuleRepository.recordHit(resolution.rule.id, receivedAt)

                    Outcome.AutoRecorded(
                        itemId = item.id,
                        transferId = transferId,
                        ruleId = resolution.rule.id,
                    )
                }

                is AutoExpenseResolver.Resolution.Pending -> {
                    inboxRepository.addItem(item)

                    Outcome.Pending(
                        itemId = item.id,
                        reason = resolution.reason,
                    )
                }
            }
        }
    }

    private suspend fun getCategoryRef(rule: PayeeRule): AutoExpenseResolver.CategoryRef? {
        val category = categoryRepository
            .getCategory(rule.categoryId)
            ?.takeUnless { it.isArchived }
            ?: return null

        // A deleted subcategory falls back to the parent category.
        val subcategoryId = rule.subcategoryId
            ?.takeIf { categoryRepository.getSubcategory(it)?.categoryId == category.id }

        return AutoExpenseResolver.CategoryRef(
            categoryId = category.id,
            subcategoryId = subcategoryId,
            currencyCode = category.currency.code,
            precision = category.currency.precision,
        )
    }
}
```

- [ ] **Step 7: Run tests to verify they pass, then the whole suite**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCaseTest"`
Expected: PASS (7 tests).

Run: `./gradlew.bat testDebugUnitTest`
Expected: PASS, including the pre-existing tests (the `TransferFundsUseCase` change must not break anything).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/transfers/logic app/src/main/java/ua/com/radiokot/money/inbox app/src/test/java/ua/com/radiokot/money/inbox
git commit -m "Process bank notifications into expenses or inbox items"
```

---

### Task 8: PowerSync repositories, card mapping and Koin module

**Files:**
- Create: `inbox/data/PowerSyncInboxRepository.kt`, `inbox/data/PowerSyncPayeeRuleRepository.kt`, `inbox/data/CardAccountPreferences.kt`, `inbox/data/CardAccountPreferencesOnPrefs.kt`, `inbox/data/MostUsedAccountSource.kt`, `inbox/data/PowerSyncMostUsedAccountSource.kt`, `inbox/InboxModule.kt`
- Modify: `inbox/logic/CardAccountResolver.kt` (add `DefaultCardAccountResolver`), `home/HomeModule.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/DefaultCardAccountResolverTest.kt`

**Interfaces:**
- Consumes: `InboxRepository`, `PayeeRuleRepository`, `CardAccountResolver`, `ProcessBankNotificationUseCase` (Task 7); `DbSchema` constants (Task 2); `DbSchema.toDbString`/`fromDbString`.
- Produces: `interface CardAccountPreferences { fun getAccountIdForCard(cardLast4: String): String?; fun setAccountIdForCard(cardLast4: String, accountId: String); fun getCardAccountsFlow(): Flow<Map<String, String>> }`; `interface MostUsedAccountSource { suspend fun getMostUsedAccountId(): String? }`; `class DefaultCardAccountResolver(cardAccountPreferences, mostUsedAccountSource) : CardAccountResolver`; Koin `val inboxModule` providing, in the session scope: `InboxRepository`, `PayeeRuleRepository`, `MostUsedAccountSource`, `CardAccountResolver`, `ProcessBankNotificationUseCase` (scoped); and `CardAccountPreferences` as a root `single`.

- [ ] **Step 1: Write the failing resolver test**

```kotlin
package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource

class DefaultCardAccountResolverTest {

    private val preferences = object : CardAccountPreferences {
        val map = MutableStateFlow(mapOf("0000" to "acc-card"))
        override fun getAccountIdForCard(cardLast4: String) = map.value[cardLast4]
        override fun setAccountIdForCard(cardLast4: String, accountId: String) {
            map.value += cardLast4 to accountId
        }
        override fun getCardAccountsFlow(): Flow<Map<String, String>> = map
    }
    private val resolver = DefaultCardAccountResolver(
        cardAccountPreferences = preferences,
        mostUsedAccountSource = object : MostUsedAccountSource {
            override suspend fun getMostUsedAccountId() = "acc-most-used"
        },
    )

    @Test
    fun precedence() = runBlocking {
        assertEquals("acc-card", resolver.resolve(cardLast4 = "0000", ruleAccountId = "acc-rule"))
        assertEquals("acc-rule", resolver.resolve(cardLast4 = "1111", ruleAccountId = "acc-rule"))
        assertEquals("acc-most-used", resolver.resolve(cardLast4 = "1111", ruleAccountId = null))
        assertEquals("acc-most-used", resolver.resolve(cardLast4 = null, ruleAccountId = null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.DefaultCardAccountResolverTest"`
Expected: FAIL, `Unresolved reference 'CardAccountPreferences'`.

- [ ] **Step 3: Write the preferences, the source and the resolver**

`inbox/data/CardAccountPreferences.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import kotlinx.coroutines.flow.Flow

/**
 * Card last 4 digits → account ID. Device-local, not synced.
 */
interface CardAccountPreferences {

    fun getAccountIdForCard(cardLast4: String): String?

    fun setAccountIdForCard(cardLast4: String, accountId: String)

    fun getCardAccountsFlow(): Flow<Map<String, String>>
}
```

`inbox/data/CardAccountPreferencesOnPrefs.kt` (same approach as `TransferPreferencesOnPrefs`):

```kotlin
package ua.com.radiokot.money.inbox.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CardAccountPreferencesOnPrefs(
    private val preferences: SharedPreferences,
) : CardAccountPreferences {

    private val knownCardsKey = "known_cards"
    private val cardAccountsStateFlow = MutableStateFlow(readCardAccounts())

    private fun getKey(cardLast4: String) =
        "card_account_$cardLast4"

    override fun getAccountIdForCard(cardLast4: String): String? =
        preferences.getString(getKey(cardLast4), null)

    override fun setAccountIdForCard(cardLast4: String, accountId: String) {
        preferences.edit {
            putStringSet(
                knownCardsKey,
                preferences.getStringSet(knownCardsKey, emptySet())!! + cardLast4
            )
            putString(getKey(cardLast4), accountId)
        }
        cardAccountsStateFlow.update { it + (cardLast4 to accountId) }
    }

    override fun getCardAccountsFlow(): Flow<Map<String, String>> =
        cardAccountsStateFlow.asStateFlow()

    private fun readCardAccounts(): Map<String, String> = buildMap {
        preferences
            .getStringSet(knownCardsKey, emptySet())!!
            .forEach { cardLast4 ->
                getAccountIdForCard(cardLast4)?.also { put(cardLast4, it) }
            }
    }
}
```

`inbox/data/MostUsedAccountSource.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

interface MostUsedAccountSource {

    /**
     * @return the non-archived account that is the source of most transfers,
     * or any non-archived account if there are no transfers, or null if there are no accounts.
     */
    suspend fun getMostUsedAccountId(): String?
}
```

`inbox/data/PowerSyncMostUsedAccountSource.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import com.powersync.PowerSyncDatabase
import ua.com.radiokot.money.powersync.DbSchema

class PowerSyncMostUsedAccountSource(
    private val database: PowerSyncDatabase,
) : MostUsedAccountSource {

    override suspend fun getMostUsedAccountId(): String? =
        database.getOptional(
            sql = SELECT_MOST_USED_ACCOUNT_ID,
            parameters = listOf(),
            mapper = { cursor -> cursor.getString(0)!! },
        )
}

private const val SELECT_MOST_USED_ACCOUNT_ID =
    "SELECT a.${DbSchema.ID} " +
            "FROM ${DbSchema.ACCOUNTS_TABLE} a " +
            "LEFT JOIN ${DbSchema.TRANSFERS_TABLE} t ON t.${DbSchema.TRANSFER_SOURCE_ID} = a.${DbSchema.ID} " +
            "WHERE IFNULL(a.${DbSchema.ACCOUNT_IS_ARCHIVED}, 0) = 0 " +
            "GROUP BY a.${DbSchema.ID} " +
            "ORDER BY COUNT(t.${DbSchema.ID}) DESC, CAST(a.${DbSchema.ACCOUNT_POSITION} AS REAL) DESC " +
            "LIMIT 1"
```

Append to `inbox/logic/CardAccountResolver.kt`:

```kotlin
class DefaultCardAccountResolver(
    private val cardAccountPreferences: CardAccountPreferences,
    private val mostUsedAccountSource: MostUsedAccountSource,
) : CardAccountResolver {

    override suspend fun resolve(
        cardLast4: String?,
        ruleAccountId: String?,
    ): String? =
        cardLast4?.let(cardAccountPreferences::getAccountIdForCard)
            ?: ruleAccountId
            ?: mostUsedAccountSource.getMostUsedAccountId()
}
```

with imports `ua.com.radiokot.money.inbox.data.CardAccountPreferences` and `ua.com.radiokot.money.inbox.data.MostUsedAccountSource` at the top of the file.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.DefaultCardAccountResolverTest"`
Expected: PASS.

- [ ] **Step 5: Write `PowerSyncInboxRepository`**

`inbox/data/PowerSyncInboxRepository.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.powersync.db.getString
import com.powersync.db.getStringOptional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.powersync.DbSchema
import ua.com.radiokot.money.powersync.DbSchema.fromDbString
import ua.com.radiokot.money.powersync.DbSchema.toDbString

class PowerSyncInboxRepository(
    private val database: PowerSyncDatabase,
) : InboxRepository {

    private val log by lazyLogger("PowerSyncInboxRepo")

    override suspend fun existsWithDedupHash(dedupHash: String): Boolean =
        database.getOptional(
            sql = "SELECT ${DbSchema.ID} FROM ${DbSchema.INBOX_ITEMS_TABLE} " +
                    "WHERE ${DbSchema.INBOX_ITEM_DEDUP_HASH} = ? LIMIT 1",
            parameters = listOf(dedupHash),
            mapper = { cursor -> cursor.getString(0)!! },
        ) != null

    override suspend fun addItem(item: InboxItem) {
        log.debug {
            "addItem(): adding:" +
                    "\nitem=$item"
        }

        database.execute(
            sql = INSERT_ITEM,
            parameters = listOf(
                item.id,
                item.receivedAt.toDbString(),
                item.sourcePackage,
                item.rawText,
                item.amount?.toPlainString(),
                item.currencyCode,
                item.payee,
                item.cardLast4,
                item.accountId,
                item.status.slug,
                item.transferId,
                item.dedupHash,
            ),
        )
    }

    override suspend fun getItem(itemId: String): InboxItem? =
        database.getOptional(
            sql = "$SELECT_ITEMS WHERE ${DbSchema.ID} = ?",
            parameters = listOf(itemId),
            mapper = ::toInboxItem,
        )

    override fun getPendingItemsFlow(): Flow<List<InboxItem>> =
        database
            .watch(
                sql = "$SELECT_ITEMS WHERE ${DbSchema.INBOX_ITEM_STATUS} = ? " +
                        "ORDER BY $RECEIVED_AT_DATETIME DESC",
                parameters = listOf(InboxItem.Status.Pending.slug),
                mapper = ::toInboxItem,
            )
            .flowOn(Dispatchers.Default)

    override fun getRecentDoneItemsFlow(limit: Int): Flow<List<InboxItem>> =
        database
            .watch(
                sql = "$SELECT_ITEMS WHERE ${DbSchema.INBOX_ITEM_STATUS} = ? " +
                        "ORDER BY $RECEIVED_AT_DATETIME DESC LIMIT ?",
                parameters = listOf(InboxItem.Status.Done.slug, limit.toLong()),
                mapper = ::toInboxItem,
            )
            .flowOn(Dispatchers.Default)

    override fun getPendingCountFlow(): Flow<Long> =
        database
            .watch(
                sql = "SELECT COUNT(*) FROM ${DbSchema.INBOX_ITEMS_TABLE} " +
                        "WHERE ${DbSchema.INBOX_ITEM_STATUS} = ?",
                parameters = listOf(InboxItem.Status.Pending.slug),
                mapper = { cursor -> cursor.getLong(0)!! },
            )
            .map { it.first() }

    override fun getKnownCardLast4Flow(): Flow<List<String>> =
        database.watch(
            sql = "SELECT DISTINCT ${DbSchema.INBOX_ITEM_CARD_LAST4} FROM ${DbSchema.INBOX_ITEMS_TABLE} " +
                    "WHERE ${DbSchema.INBOX_ITEM_CARD_LAST4} IS NOT NULL " +
                    "ORDER BY ${DbSchema.INBOX_ITEM_CARD_LAST4}",
            mapper = { cursor -> cursor.getString(0)!! },
        )

    override suspend fun markDone(itemId: String, transferId: String) =
        updateStatus(itemId, InboxItem.Status.Done, transferId)

    override suspend fun markPending(itemId: String) =
        updateStatus(itemId, InboxItem.Status.Pending, null)

    override suspend fun dismiss(itemId: String) =
        updateStatus(itemId, InboxItem.Status.Dismissed, null)

    private suspend fun updateStatus(
        itemId: String,
        status: InboxItem.Status,
        transferId: String?,
    ) {
        log.debug {
            "updateStatus(): updating:" +
                    "\nitemId=$itemId," +
                    "\nstatus=$status," +
                    "\ntransferId=$transferId"
        }

        database.execute(
            sql = "UPDATE ${DbSchema.INBOX_ITEMS_TABLE} SET " +
                    "${DbSchema.INBOX_ITEM_STATUS} = ?, " +
                    "${DbSchema.INBOX_ITEM_TRANSFER_ID} = ? " +
                    "WHERE ${DbSchema.ID} = ?",
            parameters = listOf(status.slug, transferId, itemId),
        )
    }

    private fun toInboxItem(cursor: SqlCursor): InboxItem = with(cursor) {
        InboxItem(
            id = getString(DbSchema.ID),
            receivedAt = LocalDateTime.fromDbString(getString(RECEIVED_AT_SELECTED)),
            sourcePackage = getString(DbSchema.INBOX_ITEM_SOURCE_PACKAGE),
            rawText = getString(DbSchema.INBOX_ITEM_RAW_TEXT),
            amount = getStringOptional(DbSchema.INBOX_ITEM_AMOUNT)?.trim()?.toBigDecimalOrNull(),
            currencyCode = getStringOptional(DbSchema.INBOX_ITEM_CURRENCY_CODE)?.trim(),
            payee = getStringOptional(DbSchema.INBOX_ITEM_PAYEE),
            cardLast4 = getStringOptional(DbSchema.INBOX_ITEM_CARD_LAST4)?.trim(),
            accountId = getStringOptional(DbSchema.INBOX_ITEM_ACCOUNT_ID)?.trim(),
            status = InboxItem.Status.fromSlug(getString(DbSchema.INBOX_ITEM_STATUS).trim()),
            transferId = getStringOptional(DbSchema.INBOX_ITEM_TRANSFER_ID)?.trim(),
            dedupHash = getString(DbSchema.INBOX_ITEM_DEDUP_HASH),
        )
    }
}

// Server timestamps may arrive as "2026-10-02T08:06:00", local ones as "2026-10-02 08:06:00".
private const val RECEIVED_AT_DATETIME = "datetime(${DbSchema.INBOX_ITEM_RECEIVED_AT})"
private const val RECEIVED_AT_SELECTED = "received_at_datetime"

private const val SELECT_ITEMS =
    "SELECT ${DbSchema.ID}, " +
            "$RECEIVED_AT_DATETIME AS $RECEIVED_AT_SELECTED, " +
            "${DbSchema.INBOX_ITEM_SOURCE_PACKAGE}, " +
            "${DbSchema.INBOX_ITEM_RAW_TEXT}, " +
            "${DbSchema.INBOX_ITEM_AMOUNT}, " +
            "${DbSchema.INBOX_ITEM_CURRENCY_CODE}, " +
            "${DbSchema.INBOX_ITEM_PAYEE}, " +
            "${DbSchema.INBOX_ITEM_CARD_LAST4}, " +
            "${DbSchema.INBOX_ITEM_ACCOUNT_ID}, " +
            "${DbSchema.INBOX_ITEM_STATUS}, " +
            "${DbSchema.INBOX_ITEM_TRANSFER_ID}, " +
            "${DbSchema.INBOX_ITEM_DEDUP_HASH} " +
            "FROM ${DbSchema.INBOX_ITEMS_TABLE}"

/**
 * Params: ID, received at, source package, raw text, amount, currency code,
 * payee, card last 4, account ID, status, transfer ID, dedup hash.
 */
private const val INSERT_ITEM =
    "INSERT INTO ${DbSchema.INBOX_ITEMS_TABLE} (" +
            "${DbSchema.ID}, " +
            "${DbSchema.INBOX_ITEM_RECEIVED_AT}, " +
            "${DbSchema.INBOX_ITEM_SOURCE_PACKAGE}, " +
            "${DbSchema.INBOX_ITEM_RAW_TEXT}, " +
            "${DbSchema.INBOX_ITEM_AMOUNT}, " +
            "${DbSchema.INBOX_ITEM_CURRENCY_CODE}, " +
            "${DbSchema.INBOX_ITEM_PAYEE}, " +
            "${DbSchema.INBOX_ITEM_CARD_LAST4}, " +
            "${DbSchema.INBOX_ITEM_ACCOUNT_ID}, " +
            "${DbSchema.INBOX_ITEM_STATUS}, " +
            "${DbSchema.INBOX_ITEM_TRANSFER_ID}, " +
            "${DbSchema.INBOX_ITEM_DEDUP_HASH}" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
```

- [ ] **Step 6: Write `PowerSyncPayeeRuleRepository`**

`inbox/data/PowerSyncPayeeRuleRepository.kt`:

```kotlin
package ua.com.radiokot.money.inbox.data

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.powersync.db.getLongOptional
import com.powersync.db.getString
import com.powersync.db.getStringOptional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.powersync.DbSchema
import ua.com.radiokot.money.powersync.DbSchema.fromDbString
import ua.com.radiokot.money.powersync.DbSchema.toDbString
import java.util.UUID

class PowerSyncPayeeRuleRepository(
    private val database: PowerSyncDatabase,
) : PayeeRuleRepository {

    private val log by lazyLogger("PowerSyncPayeeRuleRepo")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The in-memory rule cache: loaded once, re-emitted by PowerSync on every table change.
    private val rulesSharedFlow = database
        .watch(
            sql = SELECT_RULES,
            mapper = ::toPayeeRule,
        )
        .flowOn(Dispatchers.Default)
        .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)

    override suspend fun getRules(): List<PayeeRule> =
        rulesSharedFlow.first()

    override fun getRulesFlow(): Flow<List<PayeeRule>> =
        rulesSharedFlow

    override suspend fun saveRuleForPayee(
        payeePattern: String,
        matchType: PayeeRule.MatchType,
        categoryId: String,
        subcategoryId: String?,
        accountId: String?,
    ) {
        log.debug {
            "saveRuleForPayee(): saving:" +
                    "\npayeePattern=$payeePattern," +
                    "\nmatchType=$matchType," +
                    "\ncategoryId=$categoryId," +
                    "\nsubcategoryId=$subcategoryId," +
                    "\naccountId=$accountId"
        }

        database.writeTransaction { transaction ->
            val existingRuleId: String? = transaction.getOptional(
                sql = "SELECT ${DbSchema.ID} FROM ${DbSchema.PAYEE_RULES_TABLE} " +
                        "WHERE ${DbSchema.PAYEE_RULE_PATTERN} = ? AND ${DbSchema.PAYEE_RULE_MATCH_TYPE} = ? " +
                        "LIMIT 1",
                parameters = listOf(payeePattern, matchType.slug),
                mapper = { cursor -> cursor.getString(0)!! },
            )

            if (existingRuleId != null) {
                transaction.execute(
                    sql = "UPDATE ${DbSchema.PAYEE_RULES_TABLE} SET " +
                            "${DbSchema.PAYEE_RULE_CATEGORY_ID} = ?, " +
                            "${DbSchema.PAYEE_RULE_SUBCATEGORY_ID} = ?, " +
                            "${DbSchema.PAYEE_RULE_ACCOUNT_ID} = ? " +
                            "WHERE ${DbSchema.ID} = ?",
                    parameters = listOf(categoryId, subcategoryId, accountId, existingRuleId),
                )
            } else {
                transaction.execute(
                    sql = "INSERT INTO ${DbSchema.PAYEE_RULES_TABLE} (" +
                            "${DbSchema.ID}, " +
                            "${DbSchema.PAYEE_RULE_PATTERN}, " +
                            "${DbSchema.PAYEE_RULE_MATCH_TYPE}, " +
                            "${DbSchema.PAYEE_RULE_CATEGORY_ID}, " +
                            "${DbSchema.PAYEE_RULE_SUBCATEGORY_ID}, " +
                            "${DbSchema.PAYEE_RULE_ACCOUNT_ID}, " +
                            "${DbSchema.PAYEE_RULE_HITS}" +
                            ") VALUES (?, ?, ?, ?, ?, ?, 0)",
                    parameters = listOf(
                        UUID.randomUUID().toString(),
                        payeePattern,
                        matchType.slug,
                        categoryId,
                        subcategoryId,
                        accountId,
                    ),
                )
            }
        }
    }

    override suspend fun updateRule(
        ruleId: String,
        payeePattern: String,
        matchType: PayeeRule.MatchType,
    ) {
        database.execute(
            sql = "UPDATE ${DbSchema.PAYEE_RULES_TABLE} SET " +
                    "${DbSchema.PAYEE_RULE_PATTERN} = ?, " +
                    "${DbSchema.PAYEE_RULE_MATCH_TYPE} = ? " +
                    "WHERE ${DbSchema.ID} = ?",
            parameters = listOf(payeePattern, matchType.slug, ruleId),
        )
    }

    override suspend fun deleteRule(ruleId: String) {
        database.execute(
            sql = "DELETE FROM ${DbSchema.PAYEE_RULES_TABLE} WHERE ${DbSchema.ID} = ?",
            parameters = listOf(ruleId),
        )
    }

    override suspend fun recordHit(
        ruleId: String,
        at: LocalDateTime,
    ) {
        database.execute(
            sql = "UPDATE ${DbSchema.PAYEE_RULES_TABLE} SET " +
                    "${DbSchema.PAYEE_RULE_HITS} = IFNULL(${DbSchema.PAYEE_RULE_HITS}, 0) + 1, " +
                    "${DbSchema.PAYEE_RULE_LAST_USED_AT} = ? " +
                    "WHERE ${DbSchema.ID} = ?",
            parameters = listOf(at.toDbString(), ruleId),
        )
    }

    private fun toPayeeRule(cursor: SqlCursor): PayeeRule = with(cursor) {
        PayeeRule(
            id = getString(DbSchema.ID),
            payeePattern = getString(DbSchema.PAYEE_RULE_PATTERN),
            matchType = PayeeRule.MatchType.fromSlug(getString(DbSchema.PAYEE_RULE_MATCH_TYPE).trim()),
            categoryId = getString(DbSchema.PAYEE_RULE_CATEGORY_ID).trim(),
            subcategoryId = getStringOptional(DbSchema.PAYEE_RULE_SUBCATEGORY_ID)?.trim(),
            accountId = getStringOptional(DbSchema.PAYEE_RULE_ACCOUNT_ID)?.trim(),
            hits = getLongOptional(DbSchema.PAYEE_RULE_HITS) ?: 0L,
            lastUsedAt = getStringOptional(LAST_USED_AT_SELECTED)?.let { LocalDateTime.fromDbString(it) },
        )
    }
}

private const val LAST_USED_AT_SELECTED = "last_used_at_datetime"

private const val SELECT_RULES =
    "SELECT ${DbSchema.ID}, " +
            "${DbSchema.PAYEE_RULE_PATTERN}, " +
            "${DbSchema.PAYEE_RULE_MATCH_TYPE}, " +
            "${DbSchema.PAYEE_RULE_CATEGORY_ID}, " +
            "${DbSchema.PAYEE_RULE_SUBCATEGORY_ID}, " +
            "${DbSchema.PAYEE_RULE_ACCOUNT_ID}, " +
            "${DbSchema.PAYEE_RULE_HITS}, " +
            "datetime(${DbSchema.PAYEE_RULE_LAST_USED_AT}) AS $LAST_USED_AT_SELECTED " +
            "FROM ${DbSchema.PAYEE_RULES_TABLE}"
```


- [ ] **Step 7: Create the Koin module and load it**

`inbox/InboxModule.kt`:

```kotlin
package ua.com.radiokot.money.inbox

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.money.auth.logic.sessionScope
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.CardAccountPreferencesOnPrefs
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.MostUsedAccountSource
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.inbox.data.PowerSyncInboxRepository
import ua.com.radiokot.money.inbox.data.PowerSyncMostUsedAccountSource
import ua.com.radiokot.money.inbox.data.PowerSyncPayeeRuleRepository
import ua.com.radiokot.money.inbox.logic.CardAccountResolver
import ua.com.radiokot.money.inbox.logic.DefaultCardAccountResolver
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase
import ua.com.radiokot.money.inbox.logic.SebLatviaNotificationParser
import ua.com.radiokot.money.transfers.transfersModule

val inboxModule = module {
    includes(
        transfersModule,
    )

    single {
        CardAccountPreferencesOnPrefs(
            preferences = androidContext().getSharedPreferences(
                "bank_cards",
                Context.MODE_PRIVATE,
            )
        )
    } bind CardAccountPreferences::class

    sessionScope {

        scoped {
            PowerSyncInboxRepository(
                database = get(),
            )
        } bind InboxRepository::class

        scoped {
            PowerSyncPayeeRuleRepository(
                database = get(),
            )
        } bind PayeeRuleRepository::class

        scoped {
            PowerSyncMostUsedAccountSource(
                database = get(),
            )
        } bind MostUsedAccountSource::class

        scoped {
            DefaultCardAccountResolver(
                cardAccountPreferences = get(),
                mostUsedAccountSource = get(),
            )
        } bind CardAccountResolver::class

        // Scoped, not factory: the instance mutex must be shared.
        scoped {
            ProcessBankNotificationUseCase(
                parsers = listOf(
                    SebLatviaNotificationParser(),
                ),
                inboxRepository = get(),
                payeeRuleRepository = get(),
                accountRepository = get(),
                categoryRepository = get(),
                cardAccountResolver = get(),
                transferFundsUseCase = get(),
            )
        } bind ProcessBankNotificationUseCase::class
    }
}
```

In `home/HomeModule.kt` add `inboxModule` to `includes(...)` (after `transfersModule`) with `import ua.com.radiokot.money.inbox.inboxModule`.

- [ ] **Step 8: Build and run all tests**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox app/src/main/java/ua/com/radiokot/money/home/HomeModule.kt app/src/test/java/ua/com/radiokot/money/inbox
git commit -m "Add PowerSync inbox and payee rule repositories"
```

---

### Task 9: Notification listener service

**Files:**
- Create: `inbox/listener/BankNotificationListenerService.kt`, `inbox/listener/NotificationAccess.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `BankNotificationSources.packageNames` (Task 4), `IncomingBankNotification` (Task 2), `ProcessBankNotificationUseCase` (Task 7, bound in Task 8), `DI_SCOPE_SESSION`.
- Produces: `object NotificationAccess { fun isGranted(context: Context): Boolean; fun getSettingsIntent(context: Context): Intent; fun getFallbackSettingsIntent(): Intent }` — used by Task 14.

- [ ] **Step 1: Write the service**

`inbox/listener/BankNotificationListenerService.kt`:

```kotlin
package ua.com.radiokot.money.inbox.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION
import ua.com.radiokot.money.inbox.data.IncomingBankNotification
import ua.com.radiokot.money.inbox.logic.BankNotificationSources
import ua.com.radiokot.money.inbox.logic.ProcessBankNotificationUseCase
import ua.com.radiokot.money.lazyLogger

/**
 * Bound by the system and called only on notifications:
 * no foreground service, no wakelock, no polling, no network.
 * Everything not from a source bank returns before any other work.
 */
class BankNotificationListenerService :
    NotificationListenerService(),
    KoinComponent {

    private val log by lazyLogger("BankNotificationListener")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in BankNotificationSources.packageNames) {
            return
        }

        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            return
        }

        val extras = notification.extras
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: return

        val incoming = IncomingBankNotification(
            packageName = sbn.packageName,
            postTimeMillis = sbn.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = text,
        )

        val sessionScope = getKoin().getScopeOrNull(DI_SCOPE_SESSION)
        if (sessionScope == null) {
            log.debug {
                "onNotificationPosted(): skipping, there is no session"
            }
            return
        }

        coroutineScope.launch {
            runCatching {
                sessionScope
                    .get<ProcessBankNotificationUseCase>()
                    .invoke(incoming)
                    .getOrThrow()
            }
                .onSuccess { outcome ->
                    log.info {
                        "Bank notification processed: $outcome"
                    }
                }
                .onFailure { error ->
                    log.error(error) {
                        "onNotificationPosted(): failed to process:" +
                                "\nincoming=$incoming"
                    }
                }
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 2: Write the access helper**

`inbox/listener/NotificationAccess.kt`:

```kotlin
package ua.com.radiokot.money.inbox.listener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccess {

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

    /**
     * @return the settings screen of this app's listener where available,
     * otherwise the listener list.
     */
    fun getSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, BankNotificationListenerService::class.java)
                        .flattenToString(),
                )
        else
            getFallbackSettingsIntent()

    fun getFallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
```

- [ ] **Step 3: Declare the service**

In `app/src/main/res/values/strings.xml` add inside `<resources>`:

```xml
    <string name="bank_notification_listener_label">Bank payments to expenses</string>
```

In `app/src/main/AndroidManifest.xml`, inside `<application>` after the last `<activity>`:

```xml
        <service
            android:name=".inbox.listener.BankNotificationListenerService"
            android:exported="false"
            android:label="@string/bank_notification_listener_label"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">

            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: Build**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`. Check the merged manifest contains the service: `grep -n "BankNotificationListenerService" app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` (path may be `merged_manifest/debug/...` depending on AGP; search `app/build/intermediates` if not found). Expected: one match.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox/listener app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "Add bank notification listener service"
```

---

### Task 10: Keep the sync connection only while the app is visible

Why: the system keeps the listener bound, so the app process now lives indefinitely. Today the PowerSync stream connects when the session-scoped `PowerSyncDatabase` is first created (`PowerSyncModule`) and never disconnects — with a persistent process that is a permanently open sync stream, against the battery requirement. After this task the stream is open while an activity is visible, during the 30-minute `BackgroundPowerSyncWorker` run, and otherwise closed; listener writes wait locally and are uploaded on the next app open or worker run (as the spec intends).

**Files:**
- Create: `powersync/PowerSyncConnection.kt`
- Modify: `powersync/PowerSyncModule.kt`, `powersync/BackgroundPowerSyncWorker.kt`, `MoneyAppActivity.kt`, `MoneyApp.kt`

**Interfaces:**
- Consumes: `PowerSyncDatabase`, `AtomicCrudSupabaseConnector`.
- Produces: session-scoped `class PowerSyncConnection { fun connect(); fun disconnectWhenIdle(maxWait: Duration = 30.seconds); suspend fun awaitSyncedAndUploaded(since: Instant) }`.

- [ ] **Step 1: Write the connection holder**

`powersync/PowerSyncConnection.kt`:

```kotlin
package ua.com.radiokot.money.powersync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.sync.SyncOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.radiokot.money.BuildConfig
import ua.com.radiokot.money.lazyLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Owns the PowerSync stream lifecycle, so it is open only when needed.
 */
@OptIn(ExperimentalPowerSyncAPI::class, ExperimentalTime::class)
class PowerSyncConnection(
    private val database: PowerSyncDatabase,
    private val connector: PowerSyncBackendConnector,
) {
    private val log by lazyLogger("PowerSyncConnection")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var isConnected = false
    private var disconnectJob: Job? = null

    fun connect() {
        disconnectJob?.cancel()
        disconnectJob = null

        coroutineScope.launch {
            mutex.withLock {
                if (isConnected) {
                    return@withLock
                }

                log.debug {
                    "connect(): connecting"
                }

                database.connect(
                    connector = connector,
                    options = SyncOptions(
                        userAgent = "4Money/${BuildConfig.VERSION_NAME}",
                    ),
                    appMetadata = mapOf(
                        "v" to BuildConfig.VERSION_NAME,
                        "debug" to BuildConfig.DEBUG.toString(),
                    ),
                )
                isConnected = true
            }
        }
    }

    /**
     * Disconnects once pending local changes are uploaded, or after [maxWait].
     */
    fun disconnectWhenIdle(maxWait: Duration = 30.seconds) {
        disconnectJob?.cancel()
        disconnectJob = coroutineScope.launch {
            withTimeoutOrNull(maxWait) {
                database.currentStatus.asFlow().first { status ->
                    !status.uploading && database.getNextCrudTransaction() == null
                }
            }

            mutex.withLock {
                if (!isConnected) {
                    return@withLock
                }

                log.debug {
                    "disconnectWhenIdle(): disconnecting"
                }

                database.disconnect()
                isConnected = false
            }
        }
    }

    /**
     * Suspends until a full sync completed at or after [since]
     * and the local upload queue is empty.
     */
    suspend fun awaitSyncedAndUploaded(since: Instant) {
        database.currentStatus.asFlow().first { status ->
            val lastSyncedAt = status.lastSyncedAt
                ?: return@first false

            status.connected
                    && !status.uploading
                    && lastSyncedAt >= since
                    && database.getNextCrudTransaction() == null
        }
    }
}
```

- [ ] **Step 2: Stop auto-connecting on creation**

Replace the body of `powerSyncModule` in `powersync/PowerSyncModule.kt` (remove the now unused `GlobalScope`, `launch`, `SyncOptions`, `DelicateCoroutinesApi`, `ExperimentalPowerSyncAPI` imports):

```kotlin
val powerSyncModule = module {
    includes(authModule)

    singleOf(DbSchema::getPowerSyncSchema) bind Schema::class

    sessionScope {

        // Not connected on creation: see PowerSyncConnection.
        scoped {
            PowerSyncDatabase(
                factory = DatabaseDriverFactory(androidApplication()),
                schema = get(),
                logger = Logger.withTag("PowerSync"),
            )
        } bind Queries::class

        scoped {
            PowerSyncConnection(
                database = get(),
                connector = AtomicCrudSupabaseConnector(
                    supabaseClient = get(),
                    powerSyncEndpoint = BuildConfig.POWERSYNC_URL,
                ),
            )
        } bind PowerSyncConnection::class
    }
}
```

- [ ] **Step 3: Connect when an activity starts, disconnect when the app goes to background**

In `MoneyAppActivity.kt` add (with `import ua.com.radiokot.money.powersync.PowerSyncConnection`):

```kotlin
    override fun onStart() {
        super.onStart()

        if (hasSession) {
            scope.getOrNull<PowerSyncConnection>()?.connect()
        }
    }
```

In `MoneyApp.kt` call `initSyncConnectionLifecycle()` at the end of `onCreate()` (after `initLock()`), and add (imports `org.koin.android.ext.android.getKoin`, `ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION`, `ua.com.radiokot.money.powersync.PowerSyncConnection`):

```kotlin
    private fun initSyncConnectionLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStop(owner: LifecycleOwner) {
                getKoin()
                    .getScopeOrNull(DI_SCOPE_SESSION)
                    ?.getOrNull<PowerSyncConnection>()
                    ?.disconnectWhenIdle()
            }
        })
    }
```

- [ ] **Step 4: Make the worker connect and disconnect explicitly**

Replace the `syncDuration = measureTime { ... }` block in `BackgroundPowerSyncWorker.doWork()`:

```kotlin
            val syncDuration = measureTime {

                log.info {
                    "Background sync started"
                }

                val connection = sessionScope.get<PowerSyncConnection>()
                // lastSyncedAt may be truncated to seconds.
                val startedAt = Clock.System.now() - 2.seconds

                connection.connect()
                try {
                    log.debug {
                        "doWork(): waiting for PowerSync sync and upload"
                    }

                    connection.awaitSyncedAndUploaded(since = startedAt)
                } finally {
                    val isAppVisible = withContext(NonCancellable + Dispatchers.Main) {
                        ProcessLifecycleOwner.get().lifecycle.currentState
                            .isAtLeast(Lifecycle.State.STARTED)
                    }
                    if (!isAppVisible) {
                        connection.disconnectWhenIdle()
                    }
                }
            }
```

Add the `@OptIn(ExperimentalTime::class)` annotation to the class and these imports (drop the now unused `com.powersync.PowerSyncDatabase` import):

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
```

- [ ] **Step 5: Build and run tests**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual check on the phone**

Install the debug APK (`adb install -r app/build/outputs/apk/debug/*.apk`), then with `adb logcat | grep -E "PowerSyncConnection|BackgroundPowerSyncWorker"`:
1. Open the app → `connect(): connecting`; Accounts load and changes sync as before.
2. Add an expense, press Home → within ~1 s after upload `disconnectWhenIdle(): disconnecting`.
3. Re-open → connects again, the expense is on the server (check `money.transfers` in psql).
4. Leave the app in background for at least 30 minutes with network on, then check the log: `Background sync started`, `Background sync done in ...`, then `disconnectWhenIdle(): disconnecting`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/powersync app/src/main/java/ua/com/radiokot/money/MoneyAppActivity.kt app/src/main/java/ua/com/radiokot/money/MoneyApp.kt
git commit -m "Keep the PowerSync stream open only while the app is visible"
```

---
### Task 11: Transfer sheet inbox mode and "remember for <payee>"

**Files:**
- Create: `inbox/logic/CompleteInboxItemUseCase.kt`, `inbox/logic/InboxTransferPrefill.kt`
- Modify: `transfers/view/TransferSheetNavigation.kt`, `transfers/view/TransferSheetViewModel.kt`, `transfers/view/TransferSheet.kt`, `transfers/TransfersModule.kt`, `inbox/InboxModule.kt`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/CompleteInboxItemUseCaseTest.kt`, `app/src/test/java/ua/com/radiokot/money/inbox/logic/InboxTransferPrefillTest.kt`

**Interfaces:**
- Consumes: `InboxRepository`, `PayeeRuleRepository` (Task 7), `PayeeNormalizer` (Task 3), `AutoExpenseResolver.toMinorUnits` (Task 6), `TransferFundsUseCase(..., transferId)` (Task 7), test fixtures (Task 7).
- Produces:
  - `TransferSheetRoute` gains `val inboxItemId: String? = null`, `val rememberPayee: String? = null` and a constructor `(sourceId, destinationId, sourceAmount: BigInteger?, destinationAmount: BigInteger?, memo: String?, dateTime: LocalDateTime, inboxItemId: String, rememberPayee: String?)`.
  - `TransferSheetViewModel.Parameters` gains `inboxItemId: String? = null`, `rememberPayee: String? = null`; the VM gains `rememberPayee: String?`, `isRememberPayeeEnabled: StateFlow<Boolean>`, `onRememberPayeeToggled(Boolean)`.
  - `class CompleteInboxItemUseCase(inboxRepository, payeeRuleRepository) { suspend operator fun invoke(itemId: String, transferId: String, rememberPayeePattern: String?, sourceId: TransferCounterpartyId, destinationId: TransferCounterpartyId): Result<Unit> }`.
  - `object InboxTransferPrefill { fun buildRoute(item: InboxItem, account: Account, category: Category): TransferSheetRoute }`.

- [ ] **Step 1: Extend the route**

In `transfers/view/TransferSheetNavigation.kt`, add two defaulted properties at the end of the `TransferSheetRoute` primary constructor (after `private val dateTimeString: String?,`):

```kotlin
    val inboxItemId: String? = null,
    val rememberPayee: String? = null,
```

Add a third secondary constructor after the `constructor(transferToEdit: Transfer)` one:

```kotlin
    /**
     * A new transfer prefilled from an inbox item.
     *
     * @param rememberPayee normalized payee to offer a rule for, if any
     */
    constructor(
        sourceId: TransferCounterpartyId,
        destinationId: TransferCounterpartyId,
        sourceAmount: BigInteger?,
        destinationAmount: BigInteger?,
        memo: String?,
        dateTime: LocalDateTime,
        inboxItemId: String,
        rememberPayee: String?,
    ) : this(
        sourceIdJson = Json.encodeToString(sourceId),
        destinationIdJson = Json.encodeToString(destinationId),
        transferToEditId = null,
        sourceAmountString = sourceAmount?.toString(),
        destinationAmountString = destinationAmount?.toString(),
        memo = memo,
        dateTimeString = dateTime.toString(),
        inboxItemId = inboxItemId,
        rememberPayee = rememberPayee,
    )
```

In `NavGraphBuilder.transferSheet`, pass the new values into the parameters:

```kotlin
            TransferSheetViewModel.Parameters(
                sourceId = route.sourceId,
                destinationId = route.destinationId,
                transferToEditId = route.transferToEditId,
                sourceAmount = route.sourceAmount,
                destinationAmount = route.destinationAmount,
                memo = route.memo,
                dateTime = route.dateTime,
                inboxItemId = route.inboxItemId,
                rememberPayee = route.rememberPayee,
            )
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/ua/com/radiokot/money/inbox/logic/CompleteInboxItemUseCaseTest.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.com.radiokot.money.inbox.FakeInboxRepository
import ua.com.radiokot.money.inbox.FakePayeeRuleRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal

class CompleteInboxItemUseCaseTest {

    private val inbox = FakeInboxRepository()
    private val rules = FakePayeeRuleRepository()
    private val useCase = CompleteInboxItemUseCase(inbox, rules)
    private val account = TransferCounterpartyId.Account("acc")
    private val category = TransferCounterpartyId.Category("cat", "sub")

    private fun addPendingItem() = runBlocking {
        inbox.addItem(
            InboxItem(
                id = "item",
                receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
                sourcePackage = "se.seb.latvia",
                rawText = "x",
                amount = BigDecimal("2.12"),
                currencyCode = "EUR",
                payee = "DEEPSEERWEA",
                cardLast4 = "0000",
                accountId = "acc",
                status = InboxItem.Status.Pending,
                transferId = null,
                dedupHash = "h",
            )
        )
    }

    @Test
    fun marksDoneAndLearnsRule() = runBlocking {
        addPendingItem()

        useCase("item", "tr", "deepseerwea", account, category).getOrThrow()

        val item = inbox.items.value.single()
        assertEquals(InboxItem.Status.Done, item.status)
        assertEquals("tr", item.transferId)
        val rule = rules.rules.value.single()
        assertEquals("deepseerwea", rule.payeePattern)
        assertEquals(PayeeRule.MatchType.Exact, rule.matchType)
        assertEquals("cat", rule.categoryId)
        assertEquals("sub", rule.subcategoryId)
        assertEquals("acc", rule.accountId)
    }

    @Test
    fun relearningUpdatesTheSameRule() = runBlocking {
        addPendingItem()

        useCase("item", "tr1", "deepseerwea", account, category).getOrThrow()
        useCase("item", "tr2", "deepseerwea", account, TransferCounterpartyId.Category("cat2", null)).getOrThrow()

        assertEquals("cat2", rules.rules.value.single().categoryId)
    }

    @Test
    fun noRuleWhenNotRememberedOrNotAnExpense() = runBlocking {
        addPendingItem()

        useCase("item", "tr", null, account, category).getOrThrow()
        useCase("item", "tr", "deepseerwea", account, TransferCounterpartyId.Account("acc2")).getOrThrow()

        assertTrue(rules.rules.value.isEmpty())
    }
}
```

`app/src/test/java/ua/com/radiokot/money/inbox/logic/InboxTransferPrefillTest.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.EUR
import ua.com.radiokot.money.inbox.USD
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.testAccount
import ua.com.radiokot.money.inbox.testCategory
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import java.math.BigDecimal
import java.math.BigInteger

class InboxTransferPrefillTest {

    private val item = InboxItem(
        id = "item",
        receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
        sourcePackage = "se.seb.latvia",
        rawText = "x",
        amount = BigDecimal("2.12"),
        currencyCode = "EUR",
        payee = "DEEPSEERWEA ",
        cardLast4 = "0000",
        accountId = "acc",
        status = InboxItem.Status.Pending,
        transferId = null,
        dedupHash = "h",
    )

    @Test
    fun sameCurrencyPrefillsAmounts() {
        val route = InboxTransferPrefill.buildRoute(item, testAccount("acc", EUR), testCategory("cat", EUR))

        assertEquals(TransferCounterpartyId.Account("acc"), route.sourceId)
        assertEquals(TransferCounterpartyId.Category("cat", null), route.destinationId)
        assertEquals(BigInteger("212"), route.sourceAmount)
        assertEquals(BigInteger("212"), route.destinationAmount)
        assertEquals("DEEPSEERWEA", route.memo)
        assertEquals(LocalDateTime(2026, 10, 2, 8, 6), route.dateTime)
        assertEquals("item", route.inboxItemId)
        assertEquals("deepseerwea", route.rememberPayee)
    }

    @Test
    fun foreignCurrencyLeavesAmountsEmptyAndShowsOriginal() {
        val route = InboxTransferPrefill.buildRoute(
            item.copy(currencyCode = "USD"),
            testAccount("acc", EUR),
            testCategory("cat", EUR),
        )

        assertNull(route.sourceAmount)
        assertNull(route.destinationAmount)
        assertEquals("DEEPSEERWEA · 2,12 USD", route.memo)
    }

    @Test
    fun categoryInOtherCurrencyPrefillsSourceOnly() {
        val route = InboxTransferPrefill.buildRoute(item, testAccount("acc", EUR), testCategory("cat", USD))

        assertEquals(BigInteger("212"), route.sourceAmount)
        assertNull(route.destinationAmount)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCaseTest" --tests "ua.com.radiokot.money.inbox.logic.InboxTransferPrefillTest"`
Expected: FAIL, `Unresolved reference 'CompleteInboxItemUseCase'` / `'InboxTransferPrefill'`.

- [ ] **Step 4: Write the use case and the prefill**

`inbox/logic/CompleteInboxItemUseCase.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.data.PayeeRuleRepository
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId

/**
 * Called after the user saved the transfer opened from an inbox item.
 */
class CompleteInboxItemUseCase(
    private val inboxRepository: InboxRepository,
    private val payeeRuleRepository: PayeeRuleRepository,
) {

    /**
     * @param rememberPayeePattern normalized payee to learn an exact rule for, null to not learn.
     * A rule is learned only for an account → category expense.
     */
    suspend operator fun invoke(
        itemId: String,
        transferId: String,
        rememberPayeePattern: String?,
        sourceId: TransferCounterpartyId,
        destinationId: TransferCounterpartyId,
    ): Result<Unit> = runCatching {

        inboxRepository.markDone(
            itemId = itemId,
            transferId = transferId,
        )

        if (!rememberPayeePattern.isNullOrEmpty()
            && sourceId is TransferCounterpartyId.Account
            && destinationId is TransferCounterpartyId.Category
        ) {
            payeeRuleRepository.saveRuleForPayee(
                payeePattern = rememberPayeePattern,
                matchType = PayeeRule.MatchType.Exact,
                categoryId = destinationId.categoryId,
                subcategoryId = destinationId.subcategoryId,
                accountId = sourceId.accountId,
            )
        }
    }
}
```

`inbox/logic/InboxTransferPrefill.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.categories.data.Category
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferSheetRoute

object InboxTransferPrefill {

    /**
     * @return the regular transfer sheet route for [item] paid from [account] to [category].
     * Amounts are prefilled only when the item is in the account currency;
     * a foreign amount is shown in the memo for the user to convert.
     */
    fun buildRoute(
        item: InboxItem,
        account: Account,
        category: Category,
    ): TransferSheetRoute {
        val isInAccountCurrency = item.currencyCode != null
                && item.currencyCode.equals(account.currency.code, ignoreCase = true)

        val sourceAmount = item.amount
            ?.takeIf { isInAccountCurrency }
            ?.let { AutoExpenseResolver.toMinorUnits(it, account.currency.precision) }

        val destinationAmount = sourceAmount
            ?.takeIf { category.currency == account.currency }

        val payee = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?.takeIf(String::isNotEmpty)

        val memo =
            if (isInAccountCurrency || item.amount == null || item.currencyCode == null)
                payee
            else
                listOfNotNull(
                    payee,
                    "${item.amount.toPlainString().replace('.', ',')} ${item.currencyCode}",
                ).joinToString(" · ")

        return TransferSheetRoute(
            sourceId = TransferCounterpartyId.Account(account.id),
            destinationId = TransferCounterparty.Category(category).id,
            sourceAmount = sourceAmount,
            destinationAmount = destinationAmount,
            memo = memo,
            dateTime = item.receivedAt,
            inboxItemId = item.id,
            rememberPayee = item.payee
                ?.let(PayeeNormalizer::normalize)
                ?.takeIf(String::isNotEmpty),
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCaseTest" --tests "ua.com.radiokot.money.inbox.logic.InboxTransferPrefillTest"`
Expected: PASS (3 + 3 tests).

- [ ] **Step 6: Wire the view model**

In `transfers/view/TransferSheetViewModel.kt`:

Add the constructor parameter (and `import ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCase`, `import java.util.UUID`):

```kotlin
class TransferSheetViewModel(
    private val parameters: Parameters,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transferFundsUseCase: TransferFundsUseCase,
    private val editTransferUseCase: EditTransferUseCase,
    private val completeInboxItemUseCase: CompleteInboxItemUseCase,
) : ViewModel() {
```

Add after `val memo = _memo.asStateFlow()`:

```kotlin
    /**
     * Normalized payee to offer a rule for, when opened from the inbox.
     */
    val rememberPayee: String? = parameters.rememberPayee
    private val _isRememberPayeeEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(parameters.rememberPayee != null)
    val isRememberPayeeEnabled = _isRememberPayeeEnabled.asStateFlow()
```

Add next to `onMemoUpdated`:

```kotlin
    fun onRememberPayeeToggled(isEnabled: Boolean) {
        _isRememberPayeeEnabled.value = isEnabled
    }
```

Replace the whole `private fun transferFunds()` with:

```kotlin
    private var transferJob: Job? = null
    private fun transferFunds() {
        val sourceCounterparty = _sourceCounterparty.value
        val destinationCounterparty = _destinationCounterparty.value
        val destinationAmount = destinationAmountValue.value
        val sourceAmount =
            if (isSourceInputShown.value)
                sourceAmountValue.value
            else
                destinationAmount
        val memo = memo.value
            .trim()
            .takeIf(String::isNotEmpty)
        val dateTime = dateTime.value
        val transferId = UUID.randomUUID().toString()
        val inboxItemId = parameters.inboxItemId
        val rememberPayeePattern = rememberPayee
            ?.takeIf { _isRememberPayeeEnabled.value }

        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            log.debug {
                "transferFunds(): transferring:" +
                        "\nsource=$sourceCounterparty," +
                        "\nsourceAmount=$sourceAmount," +
                        "\ndestination=$destinationCounterparty," +
                        "\ndestinationAmount=$destinationAmount," +
                        "\nmemo=$memo," +
                        "\ndateTime=$dateTime," +
                        "\ninboxItemId=$inboxItemId"
            }

            transferFundsUseCase(
                sourceId = sourceCounterparty.id,
                sourceAmount = sourceAmount,
                destinationId = destinationCounterparty.id,
                destinationAmount = destinationAmount,
                dateTime = dateTime,
                memo = memo,
                transferId = transferId,
            )
                .onFailure { error ->
                    log.error(error) {
                        "transferFunds(): failed to transfer funds"
                    }
                }
                .onSuccess {
                    log.info {
                        "Transferred $sourceAmount from $sourceCounterparty " +
                                "as $destinationAmount to $destinationCounterparty"
                    }

                    log.debug {
                        "transferFunds(): funds transferred"
                    }

                    if (inboxItemId != null) {
                        completeInboxItemUseCase(
                            itemId = inboxItemId,
                            transferId = transferId,
                            rememberPayeePattern = rememberPayeePattern,
                            sourceId = sourceCounterparty.id,
                            destinationId = destinationCounterparty.id,
                        ).onFailure { error ->
                            log.error(error) {
                                "transferFunds(): failed to complete the inbox item"
                            }
                        }
                    }

                    _events.emit(Event.TransferDone)
                }
        }
    }
```

Extend `Parameters`:

```kotlin
    class Parameters(
        val sourceId: TransferCounterpartyId,
        val destinationId: TransferCounterpartyId,
        val transferToEditId: String?,
        val sourceAmount: BigInteger?,
        val destinationAmount: BigInteger?,
        val memo: String?,
        val dateTime: LocalDateTime?,
        val inboxItemId: String? = null,
        val rememberPayee: String? = null,
    )
```

In `transfers/TransfersModule.kt`, the `TransferSheetViewModel` definition gets `completeInboxItemUseCase = get(),` after `editTransferUseCase = get(),`. (The binding lives in `inboxModule`, which `homeModule` loads; Koin resolves across modules at runtime, so no include cycle is needed.)

In `inbox/InboxModule.kt`, inside `sessionScope { }` add (with `import ua.com.radiokot.money.inbox.logic.CompleteInboxItemUseCase`):

```kotlin
        factory {
            CompleteInboxItemUseCase(
                inboxRepository = get(),
                payeeRuleRepository = get(),
            )
        } bind CompleteInboxItemUseCase::class
```

- [ ] **Step 7: Show the toggle on the sheet**

In `transfers/view/TransferSheet.kt` (add `import ua.com.radiokot.money.uikit.RedToggleSwitch`):

In `TransferSheetRoot`, pass three more arguments to `TransferSheet(`:

```kotlin
        rememberPayee = viewModel.rememberPayee,
        isRememberPayeeEnabled = viewModel.isRememberPayeeEnabled.collectAsState(),
        onRememberPayeeToggled = remember { viewModel::onRememberPayeeToggled },
```

In the private `TransferSheet(` signature add after `onSwapCounterpartiesClicked: () -> Unit,`:

```kotlin
    rememberPayee: String? = null,
    isRememberPayeeEnabled: State<Boolean> = remember { mutableStateOf(false) },
    onRememberPayeeToggled: (Boolean) -> Unit = {},
```

Insert immediately before the `        AmountKeyboard(` call (after the memo `Box { ... }`):

```kotlin
        if (rememberPayee != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { onRememberPayeeToggled(!isRememberPayeeEnabled.value) },
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    )
            ) {
                BasicText(
                    text = "Remember for “$rememberPayee”",
                    modifier = Modifier
                        .weight(1f)
                )

                RedToggleSwitch(
                    isToggled = isRememberPayeeEnabled,
                    onToggled = onRememberPayeeToggled,
                )
            }
        }
```

The preview keeps compiling thanks to the defaults; add `rememberPayee = "deepseerwea",` to the preview call to see the row.

- [ ] **Step 8: Build and run tests**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass. Manual smoke: add an expense from the Categories tab as before — no toggle row is shown and the expense saves.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/transfers app/src/main/java/ua/com/radiokot/money/inbox app/src/test/java/ua/com/radiokot/money/inbox
git commit -m "Open the transfer sheet prefilled from an inbox item and learn a rule"
```

---

### Task 12: Rules screen

**Files:**
- Create: `inbox/view/ViewPayeeRuleItem.kt`, `inbox/view/RulesScreenViewModel.kt`, `inbox/view/RulesScreen.kt`, `inbox/view/RulesScreenNavigation.kt`
- Modify: `inbox/InboxModule.kt`

**Interfaces:**
- Consumes: `PayeeRuleRepository` (Task 7/8), `PayeeNormalizer.normalizePattern` (Task 3), `CategoryRepository.getSubcategoriesByCategoriesFlow()`.
- Produces: `@Serializable object RulesScreenRoute`; `fun NavGraphBuilder.rulesScreen(onClose: () -> Unit)` — hosted by `InboxActivity` in Task 13.

- [ ] **Step 1: Write the list item and the view model**

`inbox/view/ViewPayeeRuleItem.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.inbox.data.PayeeRule

@Immutable
class ViewPayeeRuleItem(
    val pattern: String,
    val matchTypeText: String,
    val categoryTitle: String,
    val hits: Long,
    val key: String,
    val source: PayeeRule? = null,
) {
    constructor(
        rule: PayeeRule,
        categoryTitle: String,
    ) : this(
        pattern = rule.payeePattern,
        matchTypeText = when (rule.matchType) {
            PayeeRule.MatchType.Exact -> "is"
            PayeeRule.MatchType.Contains -> "contains"
        },
        categoryTitle = categoryTitle,
        hits = rule.hits,
        key = rule.id,
        source = rule,
    )
}
```

`inbox/view/RulesScreenViewModel.kt`:

```kotlin
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
        val pattern = PayeeNormalizer.normalizePattern(newPattern)
        if (pattern.isEmpty()) {
            log.warn {
                "onPatternEdited(): ignoring empty pattern"
            }
            return
        }

        viewModelScope.launch {
            payeeRuleRepository.updateRule(
                ruleId = rule.id,
                payeePattern = pattern,
                matchType = rule.matchType,
            )
        }
    }

    fun onMatchTypeToggled(rule: PayeeRule) {
        viewModelScope.launch {
            payeeRuleRepository.updateRule(
                ruleId = rule.id,
                payeePattern = rule.payeePattern,
                matchType =
                    if (rule.matchType == PayeeRule.MatchType.Exact)
                        PayeeRule.MatchType.Contains
                    else
                        PayeeRule.MatchType.Exact,
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
```

- [ ] **Step 2: Write the screen**

`inbox/view/RulesScreen.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.TextButton

@Composable
private fun RulesScreen(
    ruleItemList: State<List<ViewPayeeRuleItem>>,
    onRuleClicked: (ViewPayeeRuleItem) -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .add(WindowInsets.statusBars)
        )
        .padding(
            horizontal = 16.dp,
        )
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = 56.dp,
            )
    ) {
        TextButton(
            text = "❌",
            padding = remember { PaddingValues(6.dp) },
            modifier = Modifier
                .clickable(
                    onClick = onCloseClicked,
                )
        )

        Text(
            text = "Payee rules",
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = 16.dp,
                )
        )
    }

    if (ruleItemList.value.isEmpty()) {
        Text(
            text = "No rules yet. Categorize a payment in the inbox " +
                    "with \"Remember\" on to create one.",
            color = Color.Gray,
            modifier = Modifier
                .padding(vertical = 16.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        items(
            items = ruleItemList.value,
            key = ViewPayeeRuleItem::key,
        ) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { onRuleClicked(item) },
                    )
                    .padding(
                        vertical = 10.dp,
                    )
            ) {
                Text(
                    text = "Payee ${item.matchTypeText} “${item.pattern}”",
                    fontSize = 16.sp,
                )
                Text(
                    text = "→ ${item.categoryTitle} · used ${item.hits}×",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
fun RulesScreen(
    viewModel: RulesScreenViewModel,
) = RulesScreen(
    ruleItemList = viewModel.ruleItemList.collectAsState(),
    onRuleClicked = remember { viewModel::onRuleClicked },
    onCloseClicked = remember { viewModel::onCloseClicked },
)

@Preview(
    apiLevel = 34,
)
@Composable
private fun RulesScreenPreview(
) = RulesScreen(
    ruleItemList = listOf(
        ViewPayeeRuleItem(
            pattern = "deepseerwea",
            matchTypeText = "is",
            categoryTitle = "Services / AI",
            hits = 4,
            key = "1",
        ),
        ViewPayeeRuleItem(
            pattern = "wolt",
            matchTypeText = "contains",
            categoryTitle = "Food",
            hits = 12,
            key = "2",
        ),
    ).let(::mutableStateOf),
    onRuleClicked = {},
    onCloseClicked = {},
)
```

- [ ] **Step 3: Write the navigation with the action dialogs**

`inbox/view/RulesScreenNavigation.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import android.app.Activity
import android.widget.EditText
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.inbox.data.PayeeRule

@Serializable
object RulesScreenRoute

fun NavGraphBuilder.rulesScreen(
    onClose: () -> Unit,
) = composable<RulesScreenRoute> {

    val activity: Activity? = LocalActivity.current
    val viewModel: RulesScreenViewModel = koinViewModel()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RulesScreenViewModel.Event.Close ->
                    onClose()

                is RulesScreenViewModel.Event.ProceedToRuleActions ->
                    showRuleActions(
                        activity = checkNotNull(activity) {
                            "The screen must have an activity to proceed"
                        },
                        rule = event.rule,
                        viewModel = viewModel,
                    )
            }
        }
    }

    RulesScreen(
        viewModel = viewModel,
    )
}

private fun showRuleActions(
    activity: Activity,
    rule: PayeeRule,
    viewModel: RulesScreenViewModel,
) {
    val toggleMatchTypeTitle =
        if (rule.matchType == PayeeRule.MatchType.Exact)
            "Match payees containing it"
        else
            "Match the exact payee only"

    AlertDialog.Builder(activity)
        .setTitle(rule.payeePattern)
        .setItems(arrayOf("Edit pattern", toggleMatchTypeTitle, "Delete")) { _, which ->
            when (which) {
                0 -> showPatternEditor(activity, rule, viewModel)
                1 -> viewModel.onMatchTypeToggled(rule)
                2 -> AlertDialog.Builder(activity)
                    .setMessage("Delete the rule for “${rule.payeePattern}”?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.onDeleteConfirmed(rule) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        .show()
}

private fun showPatternEditor(
    activity: Activity,
    rule: PayeeRule,
    viewModel: RulesScreenViewModel,
) {
    val input = EditText(activity).apply {
        setText(rule.payeePattern)
        setSingleLine()
        setSelection(text.length)
    }

    AlertDialog.Builder(activity)
        .setTitle("Payee pattern")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            viewModel.onPatternEdited(rule, input.text.toString())
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

- [ ] **Step 4: Bind the view model**

In `inbox/InboxModule.kt`, inside `sessionScope { }` (imports `org.koin.core.module.dsl.viewModel`, `ua.com.radiokot.money.inbox.view.RulesScreenViewModel`):

```kotlin
        viewModel {
            RulesScreenViewModel(
                payeeRuleRepository = get(),
                categoryRepository = get(),
            )
        } bind RulesScreenViewModel::class
```

- [ ] **Step 5: Build**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`. (The screen becomes reachable in Task 13; check its preview in Android Studio if available.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox
git commit -m "Add the payee rules screen"
```

---

### Task 13: Inbox screen and activity

**Files:**
- Create: `inbox/logic/UndoInboxItemUseCase.kt`, `inbox/view/ViewInboxItem.kt`, `inbox/view/InboxScreenViewModel.kt`, `inbox/view/InboxScreen.kt`, `inbox/view/InboxScreenNavigation.kt`, `inbox/view/InboxActivity.kt`
- Modify: `inbox/InboxModule.kt`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/ua/com/radiokot/money/inbox/logic/UndoInboxItemUseCaseTest.kt`

**Interfaces:**
- Consumes: `InboxRepository`, `CardAccountResolver` (Task 7), `CardAccountPreferences` (Task 8), `InboxTransferPrefill`, `TransferSheetRoute` inbox constructor (Task 11), `rulesScreen`/`RulesScreenRoute` (Task 12), existing `RevertTransferUseCase`, `TransferCounterpartySelectionSheetRoute`, `transferCounterpartySelectionSheet`, `transferSheet`, `TransfersNavigator`, `routeIs`.
- Produces: `class InboxActivity` (started by Task 14); `class UndoInboxItemUseCase(inboxRepository, revertTransferUseCase) { suspend operator fun invoke(item: InboxItem): Result<Unit> }`.

- [ ] **Step 1: Write the failing undo test**

`app/src/test/java/ua/com/radiokot/money/inbox/logic/UndoInboxItemUseCaseTest.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.com.radiokot.money.inbox.FakeInboxRepository
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.transfers.logic.RevertTransferUseCase

class UndoInboxItemUseCaseTest {

    @Test
    fun revertsTransferAndReturnsItemToPending() = runBlocking {
        val inbox = FakeInboxRepository()
        val reverted = mutableListOf<String>()
        val useCase = UndoInboxItemUseCase(
            inboxRepository = inbox,
            revertTransferUseCase = object : RevertTransferUseCase {
                override suspend fun invoke(transferId: String): Result<Unit> {
                    reverted += transferId
                    return Result.success(Unit)
                }
            },
        )
        val item = InboxItem(
            id = "item",
            receivedAt = LocalDateTime(2026, 10, 2, 8, 6),
            sourcePackage = "se.seb.latvia",
            rawText = "x",
            amount = null,
            currencyCode = null,
            payee = null,
            cardLast4 = null,
            accountId = null,
            status = InboxItem.Status.Done,
            transferId = "tr",
            dedupHash = "h",
        )
        inbox.addItem(item)

        useCase(item).getOrThrow()

        assertEquals(listOf("tr"), reverted)
        val updated = inbox.items.value.single()
        assertEquals(InboxItem.Status.Pending, updated.status)
        assertNull(updated.transferId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCaseTest"`
Expected: FAIL, `Unresolved reference 'UndoInboxItemUseCase'`.

- [ ] **Step 3: Write the undo use case**

`inbox/logic/UndoInboxItemUseCase.kt`:

```kotlin
package ua.com.radiokot.money.inbox.logic

import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.transfers.logic.RevertTransferUseCase

/**
 * Undoes an auto-created (or inbox-created) expense: the transfer is reverted
 * with its balance effect, the item goes back to pending.
 */
class UndoInboxItemUseCase(
    private val inboxRepository: InboxRepository,
    private val revertTransferUseCase: RevertTransferUseCase,
) {

    suspend operator fun invoke(item: InboxItem): Result<Unit> = runCatching {
        val transferId = item.transferId
        if (transferId != null) {
            revertTransferUseCase(transferId).getOrThrow()
        }

        inboxRepository.markPending(item.id)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Write the list items and the view model**

`inbox/view/ViewInboxItem.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import androidx.compose.runtime.Immutable
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.logic.PayeeNormalizer

@Immutable
class ViewInboxItem(
    val title: String,
    val amountText: String?,
    val dateText: String,
    val isForeignCurrency: Boolean,
    val key: String,
    val source: InboxItem? = null,
) {
    /**
     * @param accountCurrencyCode currency of the item's account, if known
     */
    constructor(
        item: InboxItem,
        accountCurrencyCode: String?,
    ) : this(
        title = item.payee
            ?.let(PayeeNormalizer::displayName)
            ?: item.rawText.lineSequence().last().take(120),
        amountText = item.amount?.let { amount ->
            "${amount.toPlainString().replace('.', ',')} ${item.currencyCode.orEmpty()}".trim()
        },
        dateText = item.receivedAt.toString().replace('T', ' ').take(16),
        isForeignCurrency = accountCurrencyCode != null
                && item.currencyCode != null
                && !item.currencyCode.equals(accountCurrencyCode, ignoreCase = true),
        key = item.id,
        source = item,
    )
}

@Immutable
class ViewCardAccountItem(
    val cardLast4: String,
    /**
     * Null if not mapped: the most used account is used.
     */
    val accountTitle: String?,
)
```

`inbox/view/InboxScreenViewModel.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.radiokot.money.accounts.data.Account
import ua.com.radiokot.money.accounts.data.AccountRepository
import ua.com.radiokot.money.eventSharedFlow
import ua.com.radiokot.money.inbox.data.CardAccountPreferences
import ua.com.radiokot.money.inbox.data.InboxItem
import ua.com.radiokot.money.inbox.data.InboxRepository
import ua.com.radiokot.money.inbox.logic.CardAccountResolver
import ua.com.radiokot.money.inbox.logic.InboxTransferPrefill
import ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionResult
import ua.com.radiokot.money.transfers.view.TransferSheetRoute

/**
 * Activity-level: also receives counterparty selection results for the inbox flows.
 */
class InboxScreenViewModel(
    private val inboxRepository: InboxRepository,
    private val accountRepository: AccountRepository,
    private val cardAccountPreferences: CardAccountPreferences,
    private val cardAccountResolver: CardAccountResolver,
    private val undoInboxItemUseCase: UndoInboxItemUseCase,
) : ViewModel() {

    private val log by lazyLogger("InboxScreenVM")
    private val _events: MutableSharedFlow<Event> = eventSharedFlow()
    val events = _events.asSharedFlow()
    private var itemBeingProcessed: InboxItem? = null
    private var cardBeingMapped: String? = null

    private val accountsByIdFlow: Flow<Map<String, Account>> =
        accountRepository
            .getAccountsFlow()
            .map { accounts -> accounts.associateBy(Account::id) }

    val pendingItemList: StateFlow<List<ViewInboxItem>> =
        combine(
            inboxRepository.getPendingItemsFlow(),
            accountsByIdFlow,
        ) { items, accountsById ->
            items.map { item ->
                ViewInboxItem(
                    item = item,
                    accountCurrencyCode = item.accountId?.let(accountsById::get)?.currency?.code,
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val doneItemList: StateFlow<List<ViewInboxItem>> =
        combine(
            inboxRepository.getRecentDoneItemsFlow(limit = 30),
            accountsByIdFlow,
        ) { items, accountsById ->
            items.map { item ->
                ViewInboxItem(
                    item = item,
                    accountCurrencyCode = item.accountId?.let(accountsById::get)?.currency?.code,
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val cardItemList: StateFlow<List<ViewCardAccountItem>> =
        combine(
            inboxRepository.getKnownCardLast4Flow(),
            cardAccountPreferences.getCardAccountsFlow(),
            accountsByIdFlow,
        ) { cards, accountIdsByCard, accountsById ->
            cards.map { cardLast4 ->
                ViewCardAccountItem(
                    cardLast4 = cardLast4,
                    accountTitle = accountIdsByCard[cardLast4]?.let(accountsById::get)?.title,
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onPendingItemClicked(item: ViewInboxItem) {
        val inboxItem = item.source
            ?: return

        viewModelScope.launch {
            val accountId = inboxItem.accountId
                ?.takeIf { accountRepository.getAccount(it)?.isArchived == false }
                ?: cardAccountResolver.resolve(
                    cardLast4 = inboxItem.cardLast4,
                    ruleAccountId = null,
                )

            if (accountId == null) {
                log.warn {
                    "onPendingItemClicked(): no account to pay from"
                }
                return@launch
            }

            log.debug {
                "onPendingItemClicked(): proceeding to category selection:" +
                        "\nitem=$inboxItem," +
                        "\naccountId=$accountId"
            }

            itemBeingProcessed = inboxItem
            cardBeingMapped = null
            _events.emit(Event.ProceedToCategorySelection(TransferCounterpartyId.Account(accountId)))
        }
    }

    fun onDismissClicked(item: ViewInboxItem) {
        val itemId = item.source?.id
            ?: return

        viewModelScope.launch {
            inboxRepository.dismiss(itemId)
        }
    }

    fun onUndoClicked(item: ViewInboxItem) {
        val inboxItem = item.source
            ?: return

        viewModelScope.launch {
            undoInboxItemUseCase(inboxItem)
                .onFailure { error ->
                    log.error(error) {
                        "onUndoClicked(): failed to undo:" +
                                "\nitem=$inboxItem"
                    }
                }
        }
    }

    fun onCardItemClicked(item: ViewCardAccountItem) {
        cardBeingMapped = item.cardLast4
        itemBeingProcessed = null
        _events.tryEmit(Event.ProceedToAccountSelection)
    }

    fun onCounterpartySelected(result: TransferCounterpartySelectionResult) {
        viewModelScope.launch {
            val cardLast4 = cardBeingMapped
            if (cardLast4 != null) {
                cardBeingMapped = null
                val account = (result.selectedCounterparty as? TransferCounterparty.Account)
                    ?.account
                    ?: return@launch

                log.debug {
                    "onCounterpartySelected(): mapping card:" +
                            "\ncardLast4=$cardLast4," +
                            "\naccount=$account"
                }

                cardAccountPreferences.setAccountIdForCard(cardLast4, account.id)
                return@launch
            }

            val inboxItem = itemBeingProcessed
                ?: return@launch
            itemBeingProcessed = null

            val category = (result.selectedCounterparty as? TransferCounterparty.Category)
                ?.category
                ?: return@launch
            val accountId = (result.otherSelectedCounterpartyId as? TransferCounterpartyId.Account)
                ?.accountId
                ?: return@launch
            val account = accountRepository.getAccount(accountId)
                ?: return@launch

            _events.emit(
                Event.ProceedToTransfer(
                    route = InboxTransferPrefill.buildRoute(
                        item = inboxItem,
                        account = account,
                        category = category,
                    )
                )
            )
        }
    }

    fun onRulesClicked() {
        _events.tryEmit(Event.ProceedToRules)
    }

    fun onCloseClicked() {
        _events.tryEmit(Event.Close)
    }

    sealed interface Event {

        /**
         * Pass the result to [onCounterpartySelected].
         */
        class ProceedToCategorySelection(
            val accountId: TransferCounterpartyId.Account,
        ) : Event

        /**
         * Pass the result to [onCounterpartySelected].
         */
        object ProceedToAccountSelection : Event

        class ProceedToTransfer(
            val route: TransferSheetRoute,
        ) : Event

        object ProceedToRules : Event

        object Close : Event
    }
}
```

- [ ] **Step 6: Write the screen**

`inbox/view/InboxScreen.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import ua.com.radiokot.money.uikit.TextButton

@Composable
private fun InboxScreen(
    pendingItemList: State<List<ViewInboxItem>>,
    doneItemList: State<List<ViewInboxItem>>,
    cardItemList: State<List<ViewCardAccountItem>>,
    onPendingItemClicked: (ViewInboxItem) -> Unit,
    onDismissClicked: (ViewInboxItem) -> Unit,
    onUndoClicked: (ViewInboxItem) -> Unit,
    onCardItemClicked: (ViewCardAccountItem) -> Unit,
    onRulesClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) = Column(
    modifier = Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .add(WindowInsets.statusBars)
        )
        .padding(
            horizontal = 16.dp,
        )
) {
    val buttonPadding = remember { PaddingValues(6.dp) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = 56.dp,
            )
    ) {
        TextButton(
            text = "❌",
            padding = buttonPadding,
            modifier = Modifier
                .clickable(
                    onClick = onCloseClicked,
                )
        )

        Text(
            text = "Inbox",
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = 16.dp,
                )
        )

        TextButton(
            text = "Rules",
            padding = buttonPadding,
            modifier = Modifier
                .clickable(
                    onClick = onRulesClicked,
                )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        if (cardItemList.value.isNotEmpty()) {
            item(key = "cards-title") {
                SectionTitle("Cards")
            }

            items(
                items = cardItemList.value,
                key = { "card-${it.cardLast4}" },
            ) { card ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { onCardItemClicked(card) },
                        )
                        .padding(
                            vertical = 10.dp,
                        )
                ) {
                    Text(
                        text = "Card •${card.cardLast4}",
                        modifier = Modifier
                            .weight(1f)
                    )
                    Text(
                        text = card.accountTitle ?: "Most used account",
                        color = Color.Gray,
                    )
                }
            }
        }

        item(key = "pending-title") {
            SectionTitle("To categorize")
        }

        if (pendingItemList.value.isEmpty()) {
            item(key = "pending-empty") {
                Text(
                    text = "Nothing to categorize",
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                )
            }
        }

        items(
            items = pendingItemList.value,
            key = ViewInboxItem::key,
        ) { item ->
            InboxItemRow(
                item = item,
                actionText = "✕",
                onClick = { onPendingItemClicked(item) },
                onActionClicked = { onDismissClicked(item) },
            )
        }

        if (doneItemList.value.isNotEmpty()) {
            item(key = "done-title") {
                SectionTitle("Recorded")
            }

            items(
                items = doneItemList.value,
                key = { "done-${it.key}" },
            ) { item ->
                InboxItemRow(
                    item = item,
                    actionText = "Undo",
                    onClick = null,
                    onActionClicked = { onUndoClicked(item) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
) = Text(
    text = text,
    fontSize = 16.sp,
    fontWeight = FontWeight(500),
    modifier = Modifier
        .padding(
            top = 20.dp,
            bottom = 6.dp,
        )
)

@Composable
private fun InboxItemRow(
    item: ViewInboxItem,
    actionText: String,
    onClick: (() -> Unit)?,
    onActionClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null)
                Modifier.clickable(onClick = onClick)
            else
                Modifier
        )
        .padding(
            vertical = 10.dp,
        )
) {
    Column(
        modifier = Modifier
            .weight(1f)
    ) {
        Text(
            text = item.title,
            fontSize = 16.sp,
        )
        Text(
            text = item.dateText,
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }

    if (item.amountText != null) {
        Text(
            text =
                if (item.isForeignCurrency)
                    "${item.amountText} ⚠"
                else
                    item.amountText,
            modifier = Modifier
                .padding(
                    horizontal = 12.dp,
                )
        )
    }

    TextButton(
        text = actionText,
        padding = remember { PaddingValues(6.dp) },
        modifier = Modifier
            .clickable(
                onClick = onActionClicked,
            )
    )
}

@Composable
fun InboxScreen(
    viewModel: InboxScreenViewModel,
) = InboxScreen(
    pendingItemList = viewModel.pendingItemList.collectAsState(),
    doneItemList = viewModel.doneItemList.collectAsState(),
    cardItemList = viewModel.cardItemList.collectAsState(),
    onPendingItemClicked = remember { viewModel::onPendingItemClicked },
    onDismissClicked = remember { viewModel::onDismissClicked },
    onUndoClicked = remember { viewModel::onUndoClicked },
    onCardItemClicked = remember { viewModel::onCardItemClicked },
    onRulesClicked = remember { viewModel::onRulesClicked },
    onCloseClicked = remember { viewModel::onCloseClicked },
)

@Preview(
    apiLevel = 34,
)
@Composable
private fun InboxScreenPreview(
) = InboxScreen(
    pendingItemList = listOf(
        ViewInboxItem(
            title = "DEEPSEERWEA",
            amountText = "2,12 USD",
            dateText = "2026-10-02 08:06",
            isForeignCurrency = true,
            key = "1",
        ),
        ViewInboxItem(
            title = "Jums ir jauns ziņojums internetbankā",
            amountText = null,
            dateText = "2026-10-02 09:00",
            isForeignCurrency = false,
            key = "2",
        ),
    ).let(::mutableStateOf),
    doneItemList = listOf(
        ViewInboxItem(
            title = "CAFE EXAMPLE",
            amountText = "4,50 EUR",
            dateText = "2026-10-01 13:10",
            isForeignCurrency = false,
            key = "3",
        ),
    ).let(::mutableStateOf),
    cardItemList = listOf(
        ViewCardAccountItem(
            cardLast4 = "0000",
            accountTitle = "Main",
        ),
    ).let(::mutableStateOf),
    onPendingItemClicked = {},
    onDismissClicked = {},
    onUndoClicked = {},
    onCardItemClicked = {},
    onRulesClicked = {},
    onCloseClicked = {},
)
```

- [ ] **Step 7: Write the navigation and the activity**

`inbox/view/InboxScreenNavigation.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferSheetRoute

@Serializable
object InboxScreenRoute

/**
 * @param viewModel activity-level instance, as it also receives selection results
 */
fun NavGraphBuilder.inboxScreen(
    viewModel: InboxScreenViewModel,
    onProceedToCategorySelection: (accountId: TransferCounterpartyId.Account) -> Unit,
    onProceedToAccountSelection: () -> Unit,
    onProceedToTransfer: (TransferSheetRoute) -> Unit,
    onProceedToRules: () -> Unit,
    onClose: () -> Unit,
) = composable<InboxScreenRoute> {

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxScreenViewModel.Event.ProceedToCategorySelection ->
                    onProceedToCategorySelection(event.accountId)

                InboxScreenViewModel.Event.ProceedToAccountSelection ->
                    onProceedToAccountSelection()

                is InboxScreenViewModel.Event.ProceedToTransfer ->
                    onProceedToTransfer(event.route)

                InboxScreenViewModel.Event.ProceedToRules ->
                    onProceedToRules()

                InboxScreenViewModel.Event.Close ->
                    onClose()
            }
        }
    }

    InboxScreen(
        viewModel = viewModel,
    )
}
```

`inbox/view/InboxActivity.kt`:

```kotlin
package ua.com.radiokot.money.inbox.view

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.MoneyAppActivity
import ua.com.radiokot.money.MoneyAppModalBottomSheetHost
import ua.com.radiokot.money.auth.logic.UserSessionScope
import ua.com.radiokot.money.rememberMoneyAppNavController
import ua.com.radiokot.money.routeIs
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionSheetRoute
import ua.com.radiokot.money.transfers.view.TransferSheetRoute
import ua.com.radiokot.money.transfers.view.TransfersNavigator
import ua.com.radiokot.money.transfers.view.transferCounterpartySelectionSheet
import ua.com.radiokot.money.transfers.view.transferSheet

class InboxActivity : MoneyAppActivity(
    requiresUnlocking = true,
    requiresSession = true,
) {

    override fun onCreateAllowed(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        setContent {
            UserSessionScope {
                Content(
                    finishActivity = ::finish,
                )
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun Content(
    finishActivity: () -> Unit,
) {
    val navController = rememberMoneyAppNavController()
    val transfersNavigatorFactory = koinInject<TransfersNavigator.Factory>()
    val transfersNavigator = remember(transfersNavigatorFactory, navController) {
        transfersNavigatorFactory.create(
            isIncognito = false,
            navController = navController,
        )
    }
    val inboxViewModel: InboxScreenViewModel = koinViewModel()

    NavHost(
        navController = navController,
        startDestination = InboxScreenRoute,
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) },
        modifier = Modifier
            .fillMaxSize(),
    ) {

        inboxScreen(
            viewModel = inboxViewModel,
            onProceedToCategorySelection = { accountId ->
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        isForSource = false,
                        alreadySelectedCounterpartyId = accountId,
                        showAccounts = false,
                        showCategories = true,
                    ),
                )
            },
            onProceedToAccountSelection = {
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        isForSource = true,
                        alreadySelectedCounterpartyId = null,
                        showAccounts = true,
                        showCategories = false,
                    ),
                )
            },
            onProceedToTransfer = { route ->
                navController.navigate(route)
            },
            onProceedToRules = {
                navController.navigate(RulesScreenRoute)
            },
            onClose = finishActivity,
        )

        rulesScreen(
            onClose = navController::navigateUp,
        )

        transferCounterpartySelectionSheet(
            onSelected = { result ->
                // Selection requested by the transfer sheet itself (changing the account/category).
                if (navController.previousBackStackEntry?.destination?.routeIs<TransferSheetRoute>() == true) {
                    transfersNavigator.proceedToTransfer(result)
                } else {
                    navController.navigateUp()
                    inboxViewModel.onCounterpartySelected(result)
                }
            },
        )

        transferSheet(
            onProceedToTransferCounterpartySelection = {
                    alreadySelectedCounterpartyId: TransferCounterpartyId,
                    selectSource: Boolean,
                    showCategories: Boolean,
                    showAccounts: Boolean,
                ->
                navController.navigate(
                    route = TransferCounterpartySelectionSheetRoute(
                        isForSource = selectSource,
                        alreadySelectedCounterpartyId = alreadySelectedCounterpartyId,
                        showCategories = showCategories,
                        showAccounts = showAccounts,
                    ),
                )
            },
            onTransferDone = navController::navigateUp,
        )
    }

    MoneyAppModalBottomSheetHost(
        moneyAppNavController = navController,
    )
}
```

- [ ] **Step 8: Bind and declare**

In `inbox/InboxModule.kt`, inside `sessionScope { }` (imports `ua.com.radiokot.money.inbox.logic.UndoInboxItemUseCase`, `ua.com.radiokot.money.inbox.view.InboxScreenViewModel`):

```kotlin
        factory {
            UndoInboxItemUseCase(
                inboxRepository = get(),
                revertTransferUseCase = get(),
            )
        } bind UndoInboxItemUseCase::class

        viewModel {
            InboxScreenViewModel(
                inboxRepository = get(),
                accountRepository = get(),
                cardAccountPreferences = get(),
                cardAccountResolver = get(),
                undoInboxItemUseCase = get(),
            )
        } bind InboxScreenViewModel::class
```

In `app/src/main/AndroidManifest.xml`, after the `ArchivedAccountsActivity` declaration:

```xml
        <activity
            android:name=".inbox.view.InboxActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustNothing" />
```

- [ ] **Step 9: Build and run tests**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/inbox app/src/main/AndroidManifest.xml app/src/test/java/ua/com/radiokot/money/inbox
git commit -m "Add the inbox screen"
```

---

### Task 14: Entry points — Preferences section, notification access prompt, badge

**Files:**
- Modify: `preferences/view/PreferencesScreenViewModel.kt`, `preferences/view/PreferencesScreen.kt`, `preferences/view/PreferencesScreenNavigation.kt`, `preferences/PreferencesModule.kt`, `home/view/HomeViewModel.kt`, `home/HomeModule.kt`, `home/view/HomeActivity.kt`

**Interfaces:**
- Consumes: `InboxRepository.getPendingCountFlow()` (Task 8), `NotificationAccess` (Task 9), `InboxActivity` (Task 13).
- Produces: `preferencesScreen(onProceedToPasscodeSetup, onSignedOut, onProceedToInbox: () -> Unit)`; the "More" tab dot also lights up while there are pending inbox items.

Note: the concurrent UI redesign (sub-project E) moves "More" to a profile icon; the badge source here (`HomeViewModel.hasMoreNotice`) is what that redesign should keep feeding.

- [ ] **Step 1: Extend the preferences view model**

In `PreferencesScreenViewModel.kt` add the constructor parameter `inboxRepository: InboxRepository,` (after `syncErrorRepository: SyncErrorRepository,`, import `ua.com.radiokot.money.inbox.data.InboxRepository`) and, after `isAppLockEnabled`:

```kotlin
    private val _isNotificationAccessGranted: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isNotificationAccessGranted = _isNotificationAccessGranted.asStateFlow()

    val pendingInboxCount: StateFlow<Long> =
        inboxRepository
            .getPendingCountFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun onNotificationAccessChecked(isGranted: Boolean) {
        _isNotificationAccessGranted.value = isGranted
    }

    fun onNotificationAccessClicked() {
        _events.tryEmit(Event.ProceedToNotificationAccessSettings)
    }

    fun onInboxClicked() {
        _events.tryEmit(Event.ProceedToInbox)
    }
```

and in `sealed interface Event` add:

```kotlin
        object ProceedToNotificationAccessSettings : Event
        object ProceedToInbox : Event
```

In `preferences/PreferencesModule.kt` add `inboxModule` to `includes(...)` (import `ua.com.radiokot.money.inbox.inboxModule`) and `inboxRepository = get(),` to the `PreferencesScreenViewModel(` call.

- [ ] **Step 2: Handle the new events and re-check access on resume**

In `PreferencesScreenNavigation.kt` (imports `android.content.ActivityNotFoundException`, `androidx.compose.ui.platform.LocalContext`, `androidx.lifecycle.compose.LifecycleResumeEffect`, `ua.com.radiokot.money.inbox.listener.NotificationAccess`):

```kotlin
fun NavGraphBuilder.preferencesScreen(
    onProceedToPasscodeSetup: () -> Unit,
    onSignedOut: () -> Unit,
    onProceedToInbox: () -> Unit,
) = composable(PreferencesScreenRoute) {

    val activity: Activity? = LocalActivity.current
    val context = LocalContext.current
    val viewModel = koinViewModel<PreferencesScreenViewModel>()

    // The user may come back from the system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.onNotificationAccessChecked(NotificationAccess.isGranted(context))
        onPauseOrDispose { }
    }
```

and inside the `when (event)` add:

```kotlin
                PreferencesScreenViewModel.Event.ProceedToNotificationAccessSettings ->
                    try {
                        context.startActivity(NotificationAccess.getSettingsIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(NotificationAccess.getFallbackSettingsIntent())
                    }

                PreferencesScreenViewModel.Event.ProceedToInbox ->
                    onProceedToInbox()
```

- [ ] **Step 3: Add the section to the screen**

In `PreferencesScreen.kt`, add to the private `PreferencesScreen(` parameters:

```kotlin
    isNotificationAccessGranted: State<Boolean>,
    onNotificationAccessClicked: () -> Unit,
    pendingInboxCount: State<Long>,
    onInboxClicked: () -> Unit,
```

Insert right before the `Text(text = "Currency", ...)` block:

```kotlin
    Text(
        text = "Bank notifications",
        fontSize = 16.sp,
        fontWeight = FontWeight(500),
    )

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text =
            if (isNotificationAccessGranted.value)
                "SEB card payments are recorded from notifications."
            else
                "Allow notification access so SEB card payments become expenses automatically.",
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (!isNotificationAccessGranted.value) {
        TextButton(
            text = "Allow notification access",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onNotificationAccessClicked,
                )
        )

        Spacer(modifier = Modifier.height(12.dp))
    }

    TextButton(
        text =
            if (pendingInboxCount.value > 0)
                "Inbox · ${pendingInboxCount.value} to categorize"
            else
                "Inbox",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onInboxClicked,
            )
    )

    Spacer(modifier = Modifier.height(40.dp))
```

Pass the new arguments in the public `PreferencesScreen(viewModel)`:

```kotlin
    isNotificationAccessGranted = viewModel.isNotificationAccessGranted.collectAsState(),
    onNotificationAccessClicked = remember { viewModel::onNotificationAccessClicked },
    pendingInboxCount = viewModel.pendingInboxCount.collectAsState(),
    onInboxClicked = remember { viewModel::onInboxClicked },
```

and in `PreferencesScreenPreview`:

```kotlin
    isNotificationAccessGranted = false.let(::mutableStateOf),
    onNotificationAccessClicked = {},
    pendingInboxCount = 2L.let(::mutableStateOf),
    onInboxClicked = {},
```

- [ ] **Step 4: Badge and navigation from Home**

In `home/view/HomeViewModel.kt` add the constructor parameter `inboxRepository: InboxRepository,` and replace `hasMoreNotice` (imports `kotlinx.coroutines.flow.combine`, `ua.com.radiokot.money.inbox.data.InboxRepository`):

```kotlin
    val hasMoreNotice: StateFlow<Boolean> =
        combine(
            syncErrorRepository.getErrorCountFlow(),
            inboxRepository.getPendingCountFlow(),
        ) { syncErrorCount, pendingInboxCount ->
            syncErrorCount > 0 || pendingInboxCount > 0
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

In `home/HomeModule.kt`:

```kotlin
        viewModel {
            HomeViewModel(
                syncErrorRepository = get(),
                inboxRepository = get(),
            )
        } bind HomeViewModel::class
```

In `home/view/HomeActivity.kt` (import `ua.com.radiokot.money.inbox.view.InboxActivity`), extend the `preferencesScreen(` call:

```kotlin
            preferencesScreen(
                onProceedToPasscodeSetup = {
                    context.startActivity(
                        Intent(context, SetUpPasscodeActivity::class.java)
                    )
                },
                onSignedOut = goToAuth,
                onProceedToInbox = {
                    context.startActivity(
                        Intent(context, InboxActivity::class.java)
                    )
                },
            )
```

- [ ] **Step 5: Build and run tests**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ua/com/radiokot/money/preferences app/src/main/java/ua/com/radiokot/money/home
git commit -m "Add inbox and notification access entry points"
```

---

### Task 15: End-to-end check on the phone and spec status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` (status line and table)

- [ ] **Step 1: Install and grant access**

Run: `./gradlew.bat assembleDebug && adb install -r app/build/outputs/apk/debug/ua.com.radiokot.money.debug-0.31.1-debug.apk`
(If the file name differs, use the single APK in `app/build/outputs/apk/debug/`.)

More → Bank notifications → "Allow notification access" → enable "Bank payments to expenses". On Android 13+ a sideloaded app may show "Restricted setting"; then open App info → ⋮ → "Allow restricted settings" and retry. Back in the app the section text switches to "SEB card payments are recorded from notifications."

- [ ] **Step 2: Unmatched payment → pending**

Make a small real card payment (or wait for the next one). With `adb logcat | grep -E "BankNotificationListener|PowerSyncInboxRepo"` expect `Bank notification processed: Pending(itemId=..., reason=NoRule)` within a second of the notification and no network activity from the listener. The "More" dot appears; More → Inbox shows the payee, amount, time = the moment the notification arrived (local), and a "Cards" row for the card.

- [ ] **Step 3: Categorize and learn**

Tap the item → pick the category → the transfer sheet opens with amount, memo = payee, date = notification day, and "Remember for “<payee>”" on → Save. The item moves to "Recorded"; Rules shows the new rule; the account balance dropped by the amount.

- [ ] **Step 4: Matched payment → automatic expense, then undo**

Pay the same merchant again. Expect `AutoRecorded(...)`; the expense appears in Activity with the payee as memo; the rule's "used" count increased. In Inbox → Recorded tap "Undo": the expense disappears from Activity, the balance is restored, the item is pending again.

- [ ] **Step 5: Foreign currency and sync**

A payment in a currency other than the account's (the sampled USD case) must land in "To categorize" with "⚠" and empty amounts in the sheet, the original amount in the memo. Close the app, wait ≥30 minutes (or reopen), then in psql: `select status, payee, amount from money.inbox_items order by received_at desc limit 5;` and `select payee_pattern, hits from money.payee_rules;` — the local state is on the server; `money.sync_errors` has no new rows.

- [ ] **Step 6: Update the spec status**

In the spec, change the status line to `Status: A and B implemented 2026-10-01; C and D implemented 2026-10-xx; E designed 2026-10-02, not started.` (actual date) and the table rows C and D to `done`. Add one line under "C/D decisions": `- Dedup key is (package, title, text) for recognized payments and adds the post time only for unrecognized texts, because re-posts change the post time.`

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md
git commit -m "Mark bank notifications, rules and inbox as implemented"
```

---

## Known Risks and Open Questions

- **Spec deviation — dedup key.** See Review Focus 1. Confirm with the user; if they insist on the spec's key, change only `BankNotificationDedupHash.compute` call sites in Task 7.
- **PowerSync transaction coupling.** `AtomicCrudSupabaseConnector.tryToUploadSpecialTransaction` uploads any CRUD transaction that contains a `transfers` row through the `transfer`/`transfer_revert` RPC and silently drops the other rows of that transaction. Inbox and rule writes therefore must never share a `writeTransaction` with a transfer (Task 7 keeps them separate). Consequence: a crash between the transfer write and the inbox write leaves an expense without an inbox record; a re-post of the same notification would then create a second expense. Accepted as very unlikely.
- **Fatal upload errors are discarded.** A server CHECK violation (e.g. an unknown `status` slug) is Postgres class 23 → the connector discards the transaction without retry. Keep slugs in sync with the CHECK constraints in Task 1.
- **Card mapping is device-local** (SharedPreferences), not synced — fine for one phone.
- **Android 13+ restricted settings** may block granting notification access to a sideloaded APK until "Allow restricted settings" is enabled in App info (Task 15 Step 1).
- **Merge conflicts with the UI redesign plan (E)** in `HomeActivity.kt` (bottom bar), `PreferencesScreen.kt` and `TransferSheet.kt` (hard-coded colors). Whichever lands second rebases; the inbox entry and toggle row are small, self-contained blocks.
