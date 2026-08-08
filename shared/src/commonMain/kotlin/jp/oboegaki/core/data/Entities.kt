package jp.oboegaki.core.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "items", indices = [Index("kind"), Index("lifecycle"), Index("manualRank"), Index("groupId")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val lifecycle: String,
    val title: String,
    val body: String,
    val manualRank: Long,
    @ColumnInfo(defaultValue = "0") val isGroup: Boolean,
    val groupId: String?,
    val parentId: String?,
    val convertedFromId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val archivedAtEpochMillis: Long?,
    val revision: Long,
)

@Entity(
    tableName = "todo_details",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("itemId"), Index("scheduledAtEpochMillis"), Index("priority")],
)
data class TodoDetailEntity(
    @PrimaryKey val itemId: String,
    val availableFromEpochMillis: Long?,
    val scheduledAtEpochMillis: Long?,
    val dueAtEpochMillis: Long?,
    val priority: String,
    val estimatedMinutes: Int?,
    val deferCount: Int,
    val nextSplitPromptAt: Int,
    val splitPromptDisabled: Boolean,
    val deferMethod: String,
    val deferValue: Int?,
    val pinWithinGroup: Boolean,
    val recurrenceUnit: String?,
    val recurrenceInterval: Int?,
    val recurrenceEndAtEpochMillis: Long?,
    val recurrenceAnchorMonth: Int?,
    val recurrenceAnchorDayOfMonth: Int?,
    val recurrenceScheduledAnchorMonth: Int?,
    val recurrenceScheduledAnchorDayOfMonth: Int?,
    val recurrenceAvailableAnchorMonth: Int?,
    val recurrenceAvailableAnchorDayOfMonth: Int?,
    val recurrenceDueAnchorMonth: Int?,
    val recurrenceDueAnchorDayOfMonth: Int?,
)

@Entity(
    tableName = "item_relations",
    indices = [Index("fromItemId"), Index("toItemId")],
)
data class ItemRelationEntity(
    @PrimaryKey val id: String,
    val fromItemId: String,
    val toItemId: String,
    val type: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "operations", indices = [Index("createdAtEpochMillis")])
data class OperationEntity(
    @PrimaryKey val operationId: String,
    val type: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val payloadJson: String,
    val revertedAtEpochMillis: Long?,
)

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val builtIn: Boolean,
    val json: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
