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
