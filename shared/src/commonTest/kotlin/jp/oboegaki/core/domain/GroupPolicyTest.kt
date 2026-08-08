package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.TodoDetail
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupPolicyTest {
    @Test
    fun supportsDeepHierarchyWithoutAConfiguredDepthLimit() {
        val items = (0 until 1_000).map { index ->
            item(
                id = "g$index",
                isGroup = true,
                groupId = if (index == 0) null else "g${index - 1}",
                rank = index.toLong(),
            )
        } + item(id = "leaf", groupId = "g999", rank = 1_001)

        val flattened = GroupPolicy.flatten(items)

        assertEquals(1_001, flattened.size)
        assertEquals(1_000, flattened.last().depth)
        assertEquals("leaf", flattened.last().item.id)
    }

    @Test
    fun rejectsMovingAGroupIntoItsDescendant() {
        val root = item("root", isGroup = true)
        val child = item("child", isGroup = true, groupId = root.id)

        val decision = GroupPolicy.validatePlacement(root, child.id, listOf(root, child))

        assertIs<GroupPlacementDecision.Rejected>(decision)
        assertEquals(GroupRejectionReason.SELF_OR_DESCENDANT, decision.reason)
    }

    @Test
    fun keepsTodoAndMemoGroupsSeparate() {
        val todo = item("todo")
        val memoGroup = item("memo-group", kind = ItemKind.MEMO, isGroup = true)

        val decision = GroupPolicy.validatePlacement(todo, memoGroup.id, listOf(todo, memoGroup))

        assertIs<GroupPlacementDecision.Rejected>(decision)
        assertEquals(GroupRejectionReason.DIFFERENT_KIND, decision.reason)
    }

    @Test
    fun collapsingAGroupHidesEveryNestedLevel() {
        val root = item("root", isGroup = true)
        val childGroup = item("child-group", isGroup = true, groupId = root.id)
        val leaf = item("leaf", groupId = childGroup.id)

        val flattened = GroupPolicy.flatten(listOf(root, childGroup, leaf), setOf(root.id))

        assertEquals(listOf("root"), flattened.map { it.item.id })
        assertTrue(flattened.single().hasChildren)
    }

    @Test
    fun hierarchyUsesTheSameDomainSiblingOrderAsMovingWithinGroup() {
        val zone = TimeZone.UTC
        val root = item("root", isGroup = true)
        val late = item(
            "late",
            groupId = root.id,
            rank = 1,
        ).copy(todo = TodoDetail(scheduledAtEpochMillis = LocalDateTime(2026, 8, 2, 12, 0).toInstant(zone).toEpochMilliseconds()))
        val early = item(
            "early",
            groupId = root.id,
            rank = 2,
        ).copy(todo = TodoDetail(scheduledAtEpochMillis = LocalDateTime(2026, 8, 2, 9, 0).toInstant(zone).toEpochMilliseconds()))

        val flattened = GroupPolicy.flatten(listOf(root, late, early))

        assertEquals(listOf("root", "early", "late"), flattened.map { it.item.id })
    }

    @Test
    fun recurringTemplateIncludesCompletedDescendantsAndKeepsRelationsInTemplateSet() {
        val root = item("root", isGroup = true)
        val completed = item("completed", groupId = root.id).copy(
            lifecycle = ItemLifecycle.COMPLETED,
        )
        val active = item("active", groupId = root.id)
        val relation = ItemRelation("r", completed.id, active.id, RelationType.REQUIRED_BEFORE, 0)

        val template = GroupPolicy.recurringTemplateItems(root, listOf(root, completed, active))

        assertEquals(setOf(root.id, completed.id, active.id), template.map { it.id }.toSet())
        assertEquals(setOf(completed.id, active.id), setOf(relation.fromItemId, relation.toItemId))
    }

    @Test
    fun recurringCopyIdMapMapsRelationEndpointsToMatchingCopies() {
        val root = item("root", isGroup = true)
        val first = item("first", groupId = root.id)
        val second = item("second", groupId = root.id)
        val copies = listOf(
            root.copy(id = "root-copy"),
            first.copy(id = "first-copy", groupId = "root-copy"),
            second.copy(id = "second-copy", groupId = "root-copy"),
        )

        val mapping = GroupPolicy.recurringCopyIdMap(root, listOf(root, first, second), copies)
        val relation = ItemRelation("r", first.id, second.id, RelationType.REQUIRED_BEFORE, 0)
        val copiedRelation = relation.copy(
            fromItemId = mapping.getValue(relation.fromItemId),
            toItemId = mapping.getValue(relation.toItemId),
        )

        assertEquals("first-copy", copiedRelation.fromItemId)
        assertEquals("second-copy", copiedRelation.toItemId)
    }

    private fun item(
        id: String,
        kind: ItemKind = ItemKind.TODO,
        isGroup: Boolean = false,
        groupId: String? = null,
        rank: Long = 0,
    ) = AppItem(
        id = id,
        kind = kind,
        title = id,
        manualRank = rank,
        isGroup = isGroup,
        groupId = groupId,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
