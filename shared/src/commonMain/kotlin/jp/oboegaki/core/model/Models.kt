package jp.oboegaki.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ItemKind { UNSORTED, TODO, MEMO }

@Serializable
enum class ItemLifecycle { ACTIVE, COMPLETED, ARCHIVED, SPLIT, DELETED, CONVERTED }

@Serializable
enum class Priority(val weight: Int) {
    HIGH(3), NORMAL(2), LOW(1), NONE(0);

    val label: String
        get() = when (this) {
            HIGH -> "高い"
            NORMAL -> "ふつう"
            LOW -> "低い"
            NONE -> "指定なし"
        }
}

@Serializable
enum class DeferMethod { AFTER_ITEMS, END_OF_TODAY, AFTER_MINUTES, TOMORROW, ASK }

@Serializable
enum class RelationType { REQUIRED_BEFORE, RECOMMENDED_BEFORE }

@Serializable
enum class RecurrenceUnit {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    val label: String
        get() = when (this) {
            DAY -> "日"
            WEEK -> "週"
            MONTH -> "か月"
            YEAR -> "年"
        }
}

@Serializable
data class RecurrenceRule(
    val unit: RecurrenceUnit,
    val interval: Int = 1,
    val endAtEpochMillis: Long? = null,
    /** The original local calendar position used to avoid month/year drift. */
    val anchorMonth: Int? = null,
    val anchorDayOfMonth: Int? = null,
)

@Serializable
data class TodoDetail(
    val availableFromEpochMillis: Long? = null,
    val scheduledAtEpochMillis: Long? = null,
    val dueAtEpochMillis: Long? = null,
    val priority: Priority = Priority.NONE,
    val estimatedMinutes: Int? = 15,
    val deferCount: Int = 0,
    val nextSplitPromptAt: Int = 3,
    val splitPromptDisabled: Boolean = false,
    val deferMethod: DeferMethod = DeferMethod.AFTER_ITEMS,
    val deferValue: Int? = 3,
    val pinWithinGroup: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    /** Original local calendar anchors for every date carried by a recurrence. */
    val recurrenceScheduledAnchorMonth: Int? = null,
    val recurrenceScheduledAnchorDayOfMonth: Int? = null,
    val recurrenceAvailableAnchorMonth: Int? = null,
    val recurrenceAvailableAnchorDayOfMonth: Int? = null,
    val recurrenceDueAnchorMonth: Int? = null,
    val recurrenceDueAnchorDayOfMonth: Int? = null,
)

@Serializable
data class AppItem(
    val id: String,
    val kind: ItemKind,
    val lifecycle: ItemLifecycle = ItemLifecycle.ACTIVE,
    val title: String,
    val body: String = "",
    val manualRank: Long,
    val isGroup: Boolean = false,
    val groupId: String? = null,
    /** The source item when this item was created by decomposition. Not the containing group. */
    val parentId: String? = null,
    val convertedFromId: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val archivedAtEpochMillis: Long? = null,
    val revision: Long = 0,
    val todo: TodoDetail? = null,
)

@Serializable
data class ItemRelation(
    val id: String,
    val fromItemId: String,
    val toItemId: String,
    val type: RelationType,
    val createdAtEpochMillis: Long,
)

data class AllSections(
    val unsorted: List<AppItem> = emptyList(),
    val todos: List<AppItem> = emptyList(),
    val todoGroups: List<AppItem> = emptyList(),
    val memos: List<AppItem> = emptyList(),
    val memoGroups: List<AppItem> = emptyList(),
    val completed: List<AppItem> = emptyList(),
    val archived: List<AppItem> = emptyList(),
)

@Serializable
data class DecompositionSettings(
    val enabled: Boolean = true,
    val threshold: Int = 3,
)
