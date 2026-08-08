package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RelationType

enum class MoveRejectionReason {
    SCHEDULE_CONFLICT,
    PRIORITY_CONFLICT,
    PREREQUISITE_CONFLICT,
    DIFFERENT_SECTION,
    INVALID_INDEX,
}

enum class PrerequisiteRejectionReason {
    CYCLE,
    DANGLING_ENDPOINT,
    INACTIVE_ENDPOINT,
    NON_TODO_ENDPOINT,
    GROUP_ENDPOINT,
    DIFFERENT_GROUP,
    INVALID_PARENT,
    SCHEDULE_CONFLICT,
    PRIORITY_CONFLICT,
}

sealed interface PrerequisiteValidation {
    data object Valid : PrerequisiteValidation
    data class Invalid(
        val reason: PrerequisiteRejectionReason,
        val message: String,
    ) : PrerequisiteValidation
}

sealed interface MoveDecision {
    data class Allowed(val destinationIndex: Int, val newRank: Long) : MoveDecision
    data class Rejected(val reason: MoveRejectionReason, val message: String) : MoveDecision
}

object OrderingPolicy {
    private const val RANK_STEP = 1_000L

    fun canonicalSort(items: List<AppItem>, relations: List<ItemRelation>): List<AppItem> {
        val base = items.sortedWith(
            compareBy<AppItem> { it.todo?.scheduledAtEpochMillis == null }
                .thenBy { it.todo?.scheduledAtEpochMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.todo?.priority?.weight ?: Priority.NONE.weight }
                .thenByDescending { it.todo?.pinWithinGroup == true }
                .thenBy { it.manualRank },
        ).toMutableList()

        // Relations are user data and older databases may contain edges that
        // predate the immutable time/priority rules. Keep those edges from
        // changing the canonical order.
        val required = sanitizeRelations(items, relations)
            .filter { it.type == RelationType.REQUIRED_BEFORE }
        repeat(base.size.coerceAtLeast(1)) {
            var changed = false
            required.forEach { relation ->
                val from = base.indexOfFirst { it.id == relation.fromItemId }
                val to = base.indexOfFirst { it.id == relation.toItemId }
                if (from >= 0 && to >= 0 && from > to) {
                    val item = base.removeAt(from)
                    base.add(base.indexOfFirst { it.id == relation.toItemId }, item)
                    changed = true
                }
            }
            if (!changed) return base
        }
        return base
    }

    fun validateMove(
        items: List<AppItem>,
        relations: List<ItemRelation>,
        movingId: String,
        destinationIndex: Int,
    ): MoveDecision {
        val source = items.indexOfFirst { it.id == movingId }
        if (source < 0 || destinationIndex !in items.indices) {
            return MoveDecision.Rejected(MoveRejectionReason.INVALID_INDEX, "移動先を確認できません")
        }
        val moving = items[source]
        val destination = items[destinationIndex]
        val movingMinute = moving.todo?.scheduledAtEpochMillis?.div(60_000)
        val destinationMinute = destination.todo?.scheduledAtEpochMillis?.div(60_000)
        if (movingMinute != destinationMinute) {
            return MoveDecision.Rejected(
                MoveRejectionReason.SCHEDULE_CONFLICT,
                "行う時刻の順番と矛盾するため、この位置には移動できません",
            )
        }
        if (moving.todo?.priority != destination.todo?.priority) {
            return MoveDecision.Rejected(
                MoveRejectionReason.PRIORITY_CONFLICT,
                "優先度の順番と矛盾するため、この位置には移動できません",
            )
        }

        val moved = items.toMutableList().apply {
            val value = removeAt(source)
            add(destinationIndex.coerceAtMost(size), value)
        }
        val position = moved.mapIndexed { index, item -> item.id to index }.toMap()
        val violatesPrerequisite = sanitizeRelations(items, relations).any {
            it.type == RelationType.REQUIRED_BEFORE &&
                position[it.fromItemId] != null && position[it.toItemId] != null &&
                position.getValue(it.fromItemId) >= position.getValue(it.toItemId)
        }
        if (violatesPrerequisite) {
            return MoveDecision.Rejected(
                MoveRejectionReason.PREREQUISITE_CONFLICT,
                "先に終えるやることとの関係を保つ必要があります",
            )
        }

        val previous = moved.getOrNull(destinationIndex - 1)?.manualRank
        val next = moved.getOrNull(destinationIndex + 1)?.manualRank
        val rank = when {
            previous == null && next == null -> RANK_STEP
            previous == null -> next!! - RANK_STEP
            next == null -> previous + RANK_STEP
            next - previous > 1 -> previous + (next - previous) / 2
            else -> (destinationIndex + 1L) * RANK_STEP
        }
        return MoveDecision.Allowed(destinationIndex, rank)
    }

    fun wouldCreateCycle(
        relations: List<ItemRelation>,
        fromItemId: String,
        toItemId: String,
    ): Boolean {
        if (fromItemId == toItemId) return true
        val edges = relations.filter { it.type == RelationType.REQUIRED_BEFORE }
            .groupBy { it.fromItemId }
        val pending = mutableListOf(toItemId)
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeAt(pending.lastIndex)
            if (current == fromItemId) return true
            if (visited.add(current)) {
                edges[current].orEmpty().forEach { pending += it.toItemId }
            }
        }
        return false
    }

    /** Candidates shown by leaf editors and reused by non-UI validation tests. */
    fun prerequisiteCandidates(
        items: List<AppItem>,
        directGroupId: String?,
        targetId: String? = null,
    ): List<AppItem> = items
        .asSequence()
        .filter {
            it.kind == ItemKind.TODO &&
                !it.isGroup &&
                it.lifecycle == ItemLifecycle.ACTIVE &&
                it.groupId == directGroupId &&
                it.id != targetId
        }
        .sortedWith(compareBy<AppItem> { it.manualRank }.thenBy { it.id })
        .toList()

    /**
     * Validates the complete relation set after replacing the target item's
     * required-before inputs with [prerequisiteIds]. This is the single core
     * rule used by repository create and update operations.
     */
    fun validateProposedPrerequisites(
        items: List<AppItem>,
        relations: List<ItemRelation>,
        target: AppItem,
        prerequisiteIds: Set<String>,
    ): PrerequisiteValidation {
        val prospectiveItems = items.filterNot { it.id == target.id } + target
        val proposedRelations = relations
            .filterNot { it.toItemId == target.id && it.type == RelationType.REQUIRED_BEFORE }
            .plus(
                prerequisiteIds.sorted().mapIndexed { index, prerequisiteId ->
                    ItemRelation(
                        id = "__proposed__$index:$prerequisiteId",
                        fromItemId = prerequisiteId,
                        toItemId = target.id,
                        type = RelationType.REQUIRED_BEFORE,
                        createdAtEpochMillis = Long.MIN_VALUE + index,
                    )
                },
            )

        val byId = prospectiveItems.associateBy { it.id }
        proposedRelations.forEach { relation ->
            val from = byId[relation.fromItemId]
                ?: return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.DANGLING_ENDPOINT,
                    "前提関係のやることが見つかりません",
                )
            val to = byId[relation.toItemId]
                ?: return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.DANGLING_ENDPOINT,
                    "前提関係のやることが見つかりません",
                )
            if (from.lifecycle != ItemLifecycle.ACTIVE || to.lifecycle != ItemLifecycle.ACTIVE) {
                return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.INACTIVE_ENDPOINT,
                    "前提関係には有効なやることだけ指定できます",
                )
            }
            if (from.kind != ItemKind.TODO || to.kind != ItemKind.TODO) {
                return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.NON_TODO_ENDPOINT,
                    "前提関係にはやることだけ指定できます",
                )
            }
            if (from.isGroup || to.isGroup) {
                return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.GROUP_ENDPOINT,
                    "グループは実行対象ではないため、先に終えるやることには指定できません",
                )
            }
            if (from.groupId != to.groupId) {
                return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.DIFFERENT_GROUP,
                    "前提関係は同じグループ内のやることに指定してください",
                )
            }
            if (from.groupId != null &&
                (!hasActiveDirectParent(from, byId) || !hasActiveDirectParent(to, byId))
            ) {
                return PrerequisiteValidation.Invalid(
                    PrerequisiteRejectionReason.INVALID_PARENT,
                    "蜑肴署髢｢菫ゅ・隕九▽縺九ｊ縺ｾ縺帙ｓ",
                )
            }
        }

        val acceptedRequired = mutableListOf<ItemRelation>()
        proposedRelations
            .filter { it.type == RelationType.REQUIRED_BEFORE }
            .distinctBy { it.id }
            .sortedWith(compareBy<ItemRelation> { it.id }.thenBy { it.createdAtEpochMillis })
            .forEach { relation ->
                if (wouldCreateCycle(acceptedRequired, relation.fromItemId, relation.toItemId)) {
                    return PrerequisiteValidation.Invalid(
                        PrerequisiteRejectionReason.CYCLE,
                        "前提関係が循環するため保存できません",
                    )
                }
                when (immutableConflict(prospectiveItems, relation)) {
                    PrerequisiteRejectionReason.SCHEDULE_CONFLICT -> {
                        return PrerequisiteValidation.Invalid(
                            PrerequisiteRejectionReason.SCHEDULE_CONFLICT,
                            "前提関係が予定時刻の順序と矛盾します",
                        )
                    }
                    PrerequisiteRejectionReason.PRIORITY_CONFLICT -> {
                        return PrerequisiteValidation.Invalid(
                            PrerequisiteRejectionReason.PRIORITY_CONFLICT,
                            "前提関係が優先度の順序と矛盾します",
                        )
                    }
                    else -> Unit
                }
                acceptedRequired += relation
            }
        return PrerequisiteValidation.Valid
    }

    /** Removes both incoming and outgoing relations when an item stops being a TODO. */
    fun relationsAfterKindChange(
        itemId: String,
        newKind: ItemKind,
        relations: List<ItemRelation>,
    ): List<ItemRelation> = if (newKind == ItemKind.TODO) {
        relations
    } else {
        relations.filterNot { it.fromItemId == itemId || it.toItemId == itemId }
    }

    /** Keeps only deterministic, non-dangling relations accepted by the domain. */
    fun sanitizeRelations(items: List<AppItem>, relations: List<ItemRelation>): List<ItemRelation> {
        val byId = items.associateBy { it.id }
        val accepted = mutableListOf<ItemRelation>()
        relations
            .distinctBy { it.id }
            .sortedWith(compareBy<ItemRelation> { it.id }.thenBy { it.createdAtEpochMillis })
            .forEach { relation ->
                val from = byId[relation.fromItemId] ?: return@forEach
                val to = byId[relation.toItemId] ?: return@forEach
                if (from.id == to.id ||
                    from.kind != ItemKind.TODO || to.kind != ItemKind.TODO ||
                    from.isGroup || to.isGroup ||
                    from.lifecycle != ItemLifecycle.ACTIVE || to.lifecycle != ItemLifecycle.ACTIVE ||
                    from.groupId != to.groupId ||
                    !hasActiveDirectParent(from, byId) ||
                    !hasActiveDirectParent(to, byId)
                ) return@forEach
                if (relation.type == RelationType.REQUIRED_BEFORE) {
                    if (immutableConflict(items, relation) != null ||
                        wouldCreateCycle(accepted, relation.fromItemId, relation.toItemId)
                    ) return@forEach
                }
                accepted += relation
            }
        return accepted
    }

    private fun hasActiveDirectParent(item: AppItem, byId: Map<String, AppItem>): Boolean {
        val parentId = item.groupId ?: return true
        val parent = byId[parentId] ?: return false
        return parent.isGroup && parent.kind == item.kind && parent.lifecycle == ItemLifecycle.ACTIVE
    }

    private fun immutableConflict(
        items: List<AppItem>,
        relation: ItemRelation,
    ): PrerequisiteRejectionReason? {
        val byId = items.associateBy { it.id }
        val from = byId[relation.fromItemId] ?: return PrerequisiteRejectionReason.DANGLING_ENDPOINT
        val to = byId[relation.toItemId] ?: return PrerequisiteRejectionReason.DANGLING_ENDPOINT
        val fromScheduled = from.todo?.scheduledAtEpochMillis
        val toScheduled = to.todo?.scheduledAtEpochMillis
        if (fromScheduled == null && toScheduled != null) {
            return PrerequisiteRejectionReason.SCHEDULE_CONFLICT
        }
        if (fromScheduled != null && toScheduled != null && fromScheduled > toScheduled) {
            return PrerequisiteRejectionReason.SCHEDULE_CONFLICT
        }
        val sameScheduleGroup = fromScheduled == toScheduled
        if (sameScheduleGroup &&
            (from.todo?.priority?.weight ?: Priority.NONE.weight) <
            (to.todo?.priority?.weight ?: Priority.NONE.weight)
        ) {
            return PrerequisiteRejectionReason.PRIORITY_CONFLICT
        }
        return null
    }
}
