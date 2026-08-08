package jp.oboegaki.core.data

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.DeferMethod
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RecurrenceRule
import jp.oboegaki.core.model.RecurrenceUnit
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.TodoDetail

fun AppItem.toEntity() = ItemEntity(
    id = id,
    kind = kind.name,
    lifecycle = lifecycle.name,
    title = title,
    body = body,
    manualRank = manualRank,
    isGroup = isGroup,
    groupId = groupId,
    parentId = parentId,
    convertedFromId = convertedFromId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
    revision = revision,
)

fun TodoDetail.toEntity(itemId: String) = TodoDetailEntity(
    itemId, availableFromEpochMillis, scheduledAtEpochMillis, dueAtEpochMillis, priority.name,
    estimatedMinutes, deferCount, nextSplitPromptAt, splitPromptDisabled, deferMethod.name,
    deferValue, pinWithinGroup, recurrence?.unit?.name, recurrence?.interval,
    recurrence?.endAtEpochMillis, recurrence?.anchorMonth, recurrence?.anchorDayOfMonth,
    recurrenceScheduledAnchorMonth, recurrenceScheduledAnchorDayOfMonth,
    recurrenceAvailableAnchorMonth, recurrenceAvailableAnchorDayOfMonth,
    recurrenceDueAnchorMonth, recurrenceDueAnchorDayOfMonth,
)

fun ItemRelation.toEntity() = ItemRelationEntity(
    id, fromItemId, toItemId, type.name, createdAtEpochMillis,
)

fun ItemEntity.toModel(detail: TodoDetailEntity?) = AppItem(
    id = id,
    kind = enumValueOf<ItemKind>(kind),
    lifecycle = enumValueOf<ItemLifecycle>(lifecycle),
    title = title,
    body = body,
    manualRank = manualRank,
    isGroup = isGroup,
    groupId = groupId,
    parentId = parentId,
    convertedFromId = convertedFromId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
    revision = revision,
    todo = detail?.toModel(),
)

fun TodoDetailEntity.toModel() = TodoDetail(
    availableFromEpochMillis = availableFromEpochMillis,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    dueAtEpochMillis = dueAtEpochMillis,
    priority = enumValueOf<Priority>(priority),
    estimatedMinutes = estimatedMinutes,
    deferCount = deferCount,
    nextSplitPromptAt = nextSplitPromptAt,
    splitPromptDisabled = splitPromptDisabled,
    deferMethod = enumValueOf<DeferMethod>(deferMethod),
    // Releases before 0.3.0 persisted the implicit default (3) as though it
    // were an item override. No UI exposed an override, so let the app-wide
    // setting control existing items too.
    deferValue = deferValue?.takeUnless { it == LEGACY_IMPLICIT_DEFER_ITEMS },
    pinWithinGroup = pinWithinGroup,
    recurrence = recurrenceUnit?.let { unit ->
        RecurrenceRule(
            unit = enumValueOf<RecurrenceUnit>(unit),
            interval = recurrenceInterval ?: 1,
            endAtEpochMillis = recurrenceEndAtEpochMillis,
            anchorMonth = recurrenceAnchorMonth,
            anchorDayOfMonth = recurrenceAnchorDayOfMonth,
        )
    },
    recurrenceScheduledAnchorMonth = recurrenceScheduledAnchorMonth,
    recurrenceScheduledAnchorDayOfMonth = recurrenceScheduledAnchorDayOfMonth,
    recurrenceAvailableAnchorMonth = recurrenceAvailableAnchorMonth,
    recurrenceAvailableAnchorDayOfMonth = recurrenceAvailableAnchorDayOfMonth,
    recurrenceDueAnchorMonth = recurrenceDueAnchorMonth,
    recurrenceDueAnchorDayOfMonth = recurrenceDueAnchorDayOfMonth,
)

fun ItemRelationEntity.toModel() = ItemRelation(
    id, fromItemId, toItemId, enumValueOf<RelationType>(type), createdAtEpochMillis,
)

private const val LEGACY_IMPLICIT_DEFER_ITEMS = 3
