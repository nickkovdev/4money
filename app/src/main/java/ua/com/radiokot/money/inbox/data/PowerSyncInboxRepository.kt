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
                item.direction.slug,
            ),
        )
    }

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

    // The status and the transfer ID are written in a single UPDATE.
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
            direction = InboxItem.Direction.fromSlug(
                getStringOptional(DbSchema.INBOX_ITEM_DIRECTION)?.trim()
            ),
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
            "${DbSchema.INBOX_ITEM_DEDUP_HASH}, " +
            "${DbSchema.INBOX_ITEM_DIRECTION} " +
            "FROM ${DbSchema.INBOX_ITEMS_TABLE}"

/**
 * Params: ID, received at, source package, raw text, amount, currency code,
 * payee, card last 4, account ID, status, transfer ID, dedup hash, direction.
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
            "${DbSchema.INBOX_ITEM_DEDUP_HASH}, " +
            "${DbSchema.INBOX_ITEM_DIRECTION}" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
