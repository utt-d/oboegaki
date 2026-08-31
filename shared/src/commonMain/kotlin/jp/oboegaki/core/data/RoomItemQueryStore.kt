package jp.oboegaki.core.data

import jp.oboegaki.core.domain.OrderingPolicy
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Read-only Room projections used by the compatibility repository facade. */
internal class RoomItemQueryStore(private val runtime: RoomRepositoryRuntime) {
    private val itemModels: Flow<List<AppItem>> = combine(
        runtime.dao.observeItems(), runtime.dao.observeTodoDetails(),
    ) { items, details ->
        val detailById = details.associateBy { it.itemId }
        items.map { it.toModel(detailById[it.id]) }
    }

    fun observeRelations(): Flow<List<ItemRelation>> =
        runtime.dao.observeRelations().map { values -> values.map(ItemRelationEntity::toModel) }

    fun observeAllSections(): Flow<AllSections> = combine(
        itemModels, observeRelations(),
    ) { items, relations ->
        val activeTodos = OrderingPolicy.canonicalSort(
            items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE && !it.isGroup },
            relations,
        )
        AllSections(
            unsorted = items.filter { it.kind == ItemKind.UNSORTED && it.lifecycle == ItemLifecycle.ACTIVE }
                .sortedBy { it.manualRank },
            todos = activeTodos,
            todoGroups = items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE && it.isGroup }
                .sortedBy { it.manualRank },
            memos = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ACTIVE }
                .filterNot { it.isGroup }
                .sortedBy { it.manualRank },
            memoGroups = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ACTIVE && it.isGroup }
                .sortedBy { it.manualRank },
            completed = items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.COMPLETED }
                .sortedByDescending { it.completedAtEpochMillis },
            archived = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ARCHIVED }
                .sortedByDescending { it.archivedAtEpochMillis },
        )
    }

    fun observeActiveTodos(): Flow<List<AppItem>> = observeAllSections().map { it.todos }

    fun observeActiveMemos(): Flow<List<AppItem>> = observeAllSections().map { it.memos }

    suspend fun getItem(id: String): AppItem? =
        runtime.dao.getItem(id)?.toModel(runtime.dao.getTodoDetail(id))
}
