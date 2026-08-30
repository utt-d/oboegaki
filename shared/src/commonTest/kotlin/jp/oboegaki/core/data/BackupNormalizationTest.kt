package jp.oboegaki.core.data

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.TodoDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupNormalizationTest {
    @Test
    fun blankItemIdsAreRejectedBeforeNormalization() {
        val blank = item("   ", ItemKind.TODO)
        val valid = item("valid", ItemKind.TODO)

        assertFalse(isBackupItemImportable(blank))
        assertEquals(listOf("valid"), normalizeBackupData(listOf(blank, valid), emptyList()).items.map { it.id })
    }

    @Test
    fun duplicateItemsKeepEveryItemAndCanonicalReferencesUseFirstId() {
        val group = item("group", ItemKind.TODO, isGroup = true)
        val first = item("same", ItemKind.TODO, groupId = group.id)
        val duplicate = item(
            "same",
            ItemKind.TODO,
            groupId = group.id,
            parentId = "missing-parent",
            convertedFromId = "missing-memo",
        )

        val result = normalizeBackupData(listOf(group, first, duplicate), emptyList())

        assertEquals(3, result.items.size)
        assertEquals(1, result.duplicateItemIds)
        assertEquals(listOf("group", "same", "same~duplicate-2"), result.items.map { it.id })
        assertEquals("group", result.items[2].groupId)
        assertEquals(1, result.correctedParentReferences)
        assertEquals(1, result.correctedConversionReferences)
    }

    @Test
    fun invalidGroupAndWrongKindReferencesAreRootedOrCleared() {
        val memoGroup = item("memo-group", ItemKind.MEMO, isGroup = true)
        val todo = item("todo", ItemKind.TODO, groupId = memoGroup.id, parentId = "memo", convertedFromId = "todo")
        val memo = item("memo", ItemKind.MEMO)

        val result = normalizeBackupData(listOf(memoGroup, todo, memo), emptyList())
        val normalizedTodo = result.items.first { it.id == "todo" }

        assertNull(normalizedTodo.groupId)
        assertNull(normalizedTodo.parentId)
        assertNull(normalizedTodo.convertedFromId)
        assertEquals(1, result.correctedGroupReferences)
        assertEquals(1, result.correctedParentReferences)
        assertEquals(1, result.correctedConversionReferences)
    }

    @Test
    fun duplicateRelationIdsAreRemappedAndUnknownEndpointsAreDropped() {
        val first = item("first", ItemKind.TODO)
        val second = item("second", ItemKind.TODO)
        val relations = listOf(
            ItemRelation("r", first.id, second.id, RelationType.RECOMMENDED_BEFORE, 1),
            ItemRelation("r", first.id, second.id, RelationType.RECOMMENDED_BEFORE, 2),
            ItemRelation("missing", "unknown", second.id, RelationType.RECOMMENDED_BEFORE, 3),
        )

        val result = normalizeBackupData(listOf(first, second), relations)

        assertEquals(1, result.duplicateRelationIds)
        assertEquals(listOf("r", "r~duplicate-2"), result.relations.map { it.id })
        assertEquals(1, result.correctedRelations)
        assertTrue(result.relations.all { it.fromItemId == first.id && it.toItemId == second.id })
    }

    @Test
    fun blankRelationIdsGetStableNonBlankIds() {
        val first = item("first", ItemKind.TODO)
        val second = item("second", ItemKind.TODO)
        val result = normalizeBackupData(
            listOf(first, second),
            listOf(ItemRelation("   ", first.id, second.id, RelationType.RECOMMENDED_BEFORE, 1)),
        )

        assertEquals(1, result.relations.size)
        assertTrue(result.relations.single().id.isNotBlank())
        assertEquals("relation~duplicate-1", result.relations.single().id)
    }

    private fun item(
        id: String,
        kind: ItemKind,
        isGroup: Boolean = false,
        groupId: String? = null,
        parentId: String? = null,
        convertedFromId: String? = null,
    ) = AppItem(
        id = id,
        kind = kind,
        lifecycle = ItemLifecycle.ACTIVE,
        title = id,
        manualRank = 1,
        isGroup = isGroup,
        groupId = groupId,
        parentId = parentId,
        convertedFromId = convertedFromId,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        todo = if (kind == ItemKind.TODO) TodoDetail() else null,
    )
}
