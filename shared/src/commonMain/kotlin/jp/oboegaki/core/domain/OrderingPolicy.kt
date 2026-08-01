package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RelationType

enum class MoveRejectionReason {
    SCHEDULE_CONFLICT,
    PRIORITY_CONFLICT,
    PREREQUISITE_CONFLICT,
    DIFFERENT_SECTION,
    INVALID_INDEX,
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

        val required = relations.filter { it.type == RelationType.REQUIRED_BEFORE }
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
        val violatesPrerequisite = relations.any {
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
}

