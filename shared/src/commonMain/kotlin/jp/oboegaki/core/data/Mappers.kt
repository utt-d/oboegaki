package jp.oboegaki.core.data

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.DeferMethod
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.TodoDetail

fun AppItem.toEntity() = ItemEntity(
    id, kind.name, lifecycle.name, title, body, manualRank, parentId, convertedFromId,
    createdAtEpochMillis, updatedAtEpochMillis, completedAtEpochMillis, archivedAtEpochMillis, revision,
)

fun TodoDetail.toEntity(itemId: String) = TodoDetailEntity(
    itemId, availableFromEpochMillis, scheduledAtEpochMillis, dueAtEpochMillis, priority.name,
    estimatedMinutes, deferCount, nextSplitPromptAt, splitPromptDisabled, deferMethod.name,
    deferValue, pinWithinGroup,
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
    availableFromEpochMillis, scheduledAtEpochMillis, dueAtEpochMillis,
    enumValueOf<Priority>(priority), estimatedMinutes, deferCount, nextSplitPromptAt,
    splitPromptDisabled, enumValueOf<DeferMethod>(deferMethod), deferValue, pinWithinGroup,
)

fun ItemRelationEntity.toModel() = ItemRelation(
    id, fromItemId, toItemId, enumValueOf<RelationType>(type), createdAtEpochMillis,
)

