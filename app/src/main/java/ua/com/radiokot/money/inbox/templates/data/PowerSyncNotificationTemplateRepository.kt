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

package ua.com.radiokot.money.inbox.templates.data

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.powersync.db.getBooleanOptional
import com.powersync.db.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.LocalDateTime
import ua.com.radiokot.money.lazyLogger
import ua.com.radiokot.money.powersync.DbSchema
import ua.com.radiokot.money.powersync.DbSchema.fromDbString
import ua.com.radiokot.money.powersync.DbSchema.toDbString

class PowerSyncNotificationTemplateRepository(
    private val database: PowerSyncDatabase,
) : NotificationTemplateRepository {

    private val log by lazyLogger("PowerSyncNotificationTemplateRepo")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Loaded once, re-emitted by PowerSync on every table change.
    private val templatesSharedFlow = database
        .watch(
            sql = SELECT_TEMPLATES,
            mapper = { cursor -> MappedRow(toTemplateOrNull(cursor)) },
        )
        .map { rows -> rows.mapNotNull(MappedRow::template) }
        .flowOn(Dispatchers.Default)
        .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)

    override fun getTemplatesFlow(): Flow<List<NotificationTemplate>> =
        templatesSharedFlow

    override suspend fun getTemplates(): List<NotificationTemplate> =
        templatesSharedFlow.first()

    override suspend fun addTemplates(templates: List<NotificationTemplate>) {
        if (templates.isEmpty()) {
            return
        }

        log.debug {
            "addTemplates(): adding ${templates.size} templates"
        }

        database.writeTransaction { transaction ->
            templates.forEach { template ->
                transaction.execute(
                    sql = "INSERT INTO ${DbSchema.NOTIFICATION_TEMPLATES_TABLE} (" +
                            "${DbSchema.ID}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_SOURCE_PACKAGE}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_NAME}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_DIRECTION}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_PATTERN}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_FIELDS}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_SAMPLE_TEXT}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_IS_ENABLED}, " +
                            "${DbSchema.NOTIFICATION_TEMPLATE_CREATED_AT}" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    parameters = listOf(
                        template.id,
                        template.sourcePackage,
                        template.name,
                        template.direction.value,
                        template.pattern,
                        template.fields.toJson(),
                        template.sampleText,
                        if (template.isEnabled) 1L else 0L,
                        template.createdAt.toDbString(),
                    ),
                )
            }
        }
    }

    override suspend fun setEnabledForPackage(
        sourcePackage: String,
        isEnabled: Boolean,
    ) {
        database.execute(
            sql = "UPDATE ${DbSchema.NOTIFICATION_TEMPLATES_TABLE} SET " +
                    "${DbSchema.NOTIFICATION_TEMPLATE_IS_ENABLED} = ? " +
                    "WHERE ${DbSchema.NOTIFICATION_TEMPLATE_SOURCE_PACKAGE} = ?",
            parameters = listOf(if (isEnabled) 1L else 0L, sourcePackage),
        )
    }

    override suspend fun deleteTemplate(id: String) {
        database.execute(
            sql = "DELETE FROM ${DbSchema.NOTIFICATION_TEMPLATES_TABLE} WHERE ${DbSchema.ID} = ?",
            parameters = listOf(id),
        )
    }

    /**
     * @return the template or null if the row can't be understood,
     * which is logged without any content.
     */
    private fun toTemplateOrNull(cursor: SqlCursor): NotificationTemplate? = with(cursor) {
        val id = getString(DbSchema.ID)

        try {
            NotificationTemplate(
                id = id,
                sourcePackage = getString(DbSchema.NOTIFICATION_TEMPLATE_SOURCE_PACKAGE).trim(),
                name = getString(DbSchema.NOTIFICATION_TEMPLATE_NAME),
                direction = NotificationTemplate.Direction
                    .fromValue(getString(DbSchema.NOTIFICATION_TEMPLATE_DIRECTION).trim()),
                pattern = getString(DbSchema.NOTIFICATION_TEMPLATE_PATTERN),
                fields = TemplateFields.fromJson(getString(DbSchema.NOTIFICATION_TEMPLATE_FIELDS)),
                sampleText = getString(DbSchema.NOTIFICATION_TEMPLATE_SAMPLE_TEXT),
                isEnabled = getBooleanOptional(DbSchema.NOTIFICATION_TEMPLATE_IS_ENABLED) == true,
                createdAt = LocalDateTime.fromDbString(getString(CREATED_AT_SELECTED)),
            )
        } catch (e: Exception) {
            log.warn {
                "toTemplateOrNull(): skipping unreadable template $id: ${e::class.simpleName}"
            }
            null
        }
    }
}

// The watch row type can't be null, while an unreadable row is skipped.
private class MappedRow(val template: NotificationTemplate?)

private const val CREATED_AT_SELECTED = "created_at_datetime"

private const val SELECT_TEMPLATES =
    "SELECT ${DbSchema.ID}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_SOURCE_PACKAGE}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_NAME}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_DIRECTION}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_PATTERN}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_FIELDS}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_SAMPLE_TEXT}, " +
            "${DbSchema.NOTIFICATION_TEMPLATE_IS_ENABLED}, " +
            "datetime(${DbSchema.NOTIFICATION_TEMPLATE_CREATED_AT}) AS $CREATED_AT_SELECTED " +
            "FROM ${DbSchema.NOTIFICATION_TEMPLATES_TABLE} " +
            "ORDER BY $CREATED_AT_SELECTED, ${DbSchema.ID}"
