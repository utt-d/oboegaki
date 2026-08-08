package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.TodoDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OrderingPolicyTest {
    @Test
    fun scheduledItemsRemainChronological() {
        val late = todo("late", 1, scheduledAt = 13 * 60)
        val early = todo("early", 2, scheduledAt = 10 * 60)
        val result = OrderingPolicy.validateMove(listOf(early, late), emptyList(), "late", 0)
        assertIs<MoveDecision.Rejected>(result)
        assertEquals(MoveRejectionReason.SCHEDULE_CONFLICT, result.reason)
    }

    @Test
    fun priorityCannotBeCrossedInsideSameSchedule() {
        val high = todo("high", 1, priority = Priority.HIGH)
        val low = todo("low", 2, priority = Priority.LOW)
        val result = OrderingPolicy.validateMove(listOf(high, low), emptyList(), "low", 0)
        assertIs<MoveDecision.Rejected>(result)
        assertEquals(MoveRejectionReason.PRIORITY_CONFLICT, result.reason)
    }

    @Test
    fun sameGroupCanBeReordered() {
        val first = todo("first", 1)
        val second = todo("second", 2)
        assertIs<MoveDecision.Allowed>(
            OrderingPolicy.validateMove(listOf(first, second), emptyList(), "second", 0),
        )
    }

    @Test
    fun prerequisiteCannotBeReversed() {
        val first = todo("first", 1)
        val second = todo("second", 2)
        val relation = ItemRelation("r", "first", "second", RelationType.REQUIRED_BEFORE, 0)
        val result = OrderingPolicy.validateMove(listOf(first, second), listOf(relation), "second", 0)
        assertIs<MoveDecision.Rejected>(result)
        assertEquals(MoveRejectionReason.PREREQUISITE_CONFLICT, result.reason)
    }

    @Test
    fun cycleIsDetected() {
        val relations = listOf(ItemRelation("r", "a", "b", RelationType.REQUIRED_BEFORE, 0))
        assertTrue(OrderingPolicy.wouldCreateCycle(relations, "b", "a"))
    }

    @Test
    fun importedRelationsDiscardDanglingAndCycleEdgesDeterministically() {
        val a = todo("a", 1)
        val b = todo("b", 2)
        val c = todo("c", 3)
        val low = todo("low", 4, priority = Priority.LOW)
        val high = todo("high", 5, priority = Priority.HIGH)
        val relations = listOf(
            ItemRelation("z-cycle", "c", "a", RelationType.REQUIRED_BEFORE, 3),
            ItemRelation("a-edge", "a", "b", RelationType.REQUIRED_BEFORE, 1),
            ItemRelation("missing", "missing", "a", RelationType.REQUIRED_BEFORE, 0),
            ItemRelation("b-edge", "b", "c", RelationType.REQUIRED_BEFORE, 2),
            ItemRelation("priority-conflict", low.id, high.id, RelationType.REQUIRED_BEFORE, 4),
        )

        val safe = OrderingPolicy.sanitizeRelations(listOf(a, b, c, low, high), relations)

        assertEquals(listOf("a-edge", "b-edge"), safe.map { it.id })
    }

    @Test
    fun proposedPrerequisiteRejectsDanglingEndpoint() {
        val target = todo("target", 1)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(target),
            emptyList(),
            target,
            setOf("missing"),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.DANGLING_ENDPOINT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsInactiveEndpoint() {
        val inactive = todo("inactive", 1).copy(lifecycle = ItemLifecycle.COMPLETED)
        val target = todo("target", 2)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(inactive, target),
            emptyList(),
            target,
            setOf(inactive.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.INACTIVE_ENDPOINT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsNonTodoEndpoint() {
        val memo = todo("memo", 1).copy(kind = ItemKind.MEMO, todo = null)
        val target = todo("target", 2)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(memo, target),
            emptyList(),
            target,
            setOf(memo.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.NON_TODO_ENDPOINT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsGroupEndpoint() {
        val group = todo("group", 1).copy(isGroup = true)
        val target = todo("target", 2)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(group, target),
            emptyList(),
            target,
            setOf(group.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.GROUP_ENDPOINT, result.reason)
    }

    @Test
    fun sanitizeRelationsKeepsOnlyActiveLeavesInTheSameDirectParent() {
        val group = todo("group", 0).copy(isGroup = true)
        val first = todo("first", 1).copy(groupId = group.id)
        val second = todo("second", 2).copy(groupId = group.id)
        val otherGroup = todo("other-group", 3).copy(isGroup = true)
        val other = todo("other", 4).copy(groupId = otherGroup.id)
        val relations = listOf(
            ItemRelation("valid", first.id, second.id, RelationType.REQUIRED_BEFORE, 0),
            ItemRelation("group", group.id, second.id, RelationType.REQUIRED_BEFORE, 1),
            ItemRelation("cross", first.id, other.id, RelationType.REQUIRED_BEFORE, 2),
        )

        assertEquals(listOf("valid"), OrderingPolicy.sanitizeRelations(
            listOf(group, first, second, otherGroup, other),
            relations,
        ).map { it.id })
    }

    @Test
    fun prerequisiteCandidatesAreActiveLeavesOfTheSameDirectParent() {
        val group = todo("group", 0).copy(isGroup = true)
        val valid = todo("valid", 1).copy(groupId = group.id)
        val completed = todo("completed", 2).copy(groupId = group.id, lifecycle = ItemLifecycle.COMPLETED)
        val nestedGroup = todo("nested", 3).copy(groupId = group.id, isGroup = true)
        val nestedLeaf = todo("nested-leaf", 4).copy(groupId = nestedGroup.id)

        assertEquals(
            listOf(valid.id),
            OrderingPolicy.prerequisiteCandidates(
                listOf(group, valid, completed, nestedGroup, nestedLeaf),
                group.id,
            ).map { it.id },
        )
    }

    @Test
    fun proposedPrerequisiteRejectsDifferentGroupBranch() {
        val from = todo("from", 1).copy(groupId = "group-a")
        val target = todo("target", 2).copy(groupId = "group-b")

        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(from, target),
            emptyList(),
            target,
            setOf(from.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.DIFFERENT_GROUP, result.reason)
    }

    @Test
    fun importedRelationsDiscardDifferentGroupBranch() {
        val from = todo("from", 1).copy(groupId = "group-a")
        val target = todo("target", 2).copy(groupId = "group-b")
        val relation = ItemRelation("cross", from.id, target.id, RelationType.REQUIRED_BEFORE, 0)

        assertTrue(OrderingPolicy.sanitizeRelations(listOf(from, target), listOf(relation)).isEmpty())
    }

    @Test
    fun sanitizeRelationsRejectsMissingInactiveAndWrongKindParents() {
        val missingFrom = todo("missing-from", 1).copy(groupId = "missing")
        val missingTarget = todo("missing-target", 2).copy(groupId = "missing")
        val inactiveParent = todo("inactive-parent", 0).copy(isGroup = true, lifecycle = ItemLifecycle.COMPLETED)
        val inactiveFrom = todo("inactive-from", 1).copy(groupId = inactiveParent.id)
        val inactiveTarget = todo("inactive-target", 2).copy(groupId = inactiveParent.id)
        val nonGroupParent = todo("non-group-parent", 0)
        val nonGroupFrom = todo("non-group-from", 1).copy(groupId = nonGroupParent.id)
        val nonGroupTarget = todo("non-group-target", 2).copy(groupId = nonGroupParent.id)
        val relations = listOf(
            ItemRelation("missing", missingFrom.id, missingTarget.id, RelationType.REQUIRED_BEFORE, 0),
            ItemRelation("inactive", inactiveFrom.id, inactiveTarget.id, RelationType.REQUIRED_BEFORE, 1),
            ItemRelation("non-group", nonGroupFrom.id, nonGroupTarget.id, RelationType.REQUIRED_BEFORE, 2),
        )

        assertTrue(
            OrderingPolicy.sanitizeRelations(
                listOf(missingFrom, missingTarget, inactiveParent, inactiveFrom, inactiveTarget, nonGroupParent, nonGroupFrom, nonGroupTarget),
                relations,
            ).isEmpty(),
        )
    }

    @Test
    fun proposedPrerequisiteRejectsScheduledAndUnscheduledConflict() {
        val unscheduled = todo("unscheduled", 1)
        val scheduled = todo("scheduled", 2, scheduledAt = 10)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(unscheduled, scheduled),
            emptyList(),
            scheduled,
            setOf(unscheduled.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.SCHEDULE_CONFLICT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsReversedScheduledTime() {
        val early = todo("early", 1, scheduledAt = 10)
        val late = todo("late", 2, scheduledAt = 11)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(early, late),
            emptyList(),
            early,
            setOf(late.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.SCHEDULE_CONFLICT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsPriorityConflictWithinScheduleGroup() {
        val low = todo("low", 1, scheduledAt = 10, priority = Priority.LOW)
        val high = todo("high", 2, scheduledAt = 10, priority = Priority.HIGH)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(low, high),
            emptyList(),
            high,
            setOf(low.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.PRIORITY_CONFLICT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsCycle() {
        val a = todo("a", 1)
        val b = todo("b", 2)
        val relation = ItemRelation("r", a.id, b.id, RelationType.REQUIRED_BEFORE, 0)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(a, b),
            listOf(relation),
            a,
            setOf(b.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.CYCLE, result.reason)
    }

    @Test
    fun proposedPrerequisiteAcceptsImmutableOrderCompatibleRelation() {
        val early = todo("early", 1, scheduledAt = 10)
        val late = todo("late", 2, scheduledAt = 11)
        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(early, late),
            emptyList(),
            late,
            setOf(early.id),
        )

        assertIs<PrerequisiteValidation.Valid>(result)
    }

    @Test
    fun proposedPrerequisiteAcceptsTwoRootLeaves() {
        val from = todo("from", 1)
        val target = todo("target", 2)

        assertIs<PrerequisiteValidation.Valid>(
            OrderingPolicy.validateProposedPrerequisites(
                listOf(from, target), emptyList(), target, setOf(from.id),
            ),
        )
    }

    @Test
    fun proposedPrerequisiteAcceptsLeavesWithTheSameActiveParent() {
        val parent = todo("parent", 0).copy(isGroup = true)
        val from = todo("from", 1).copy(groupId = parent.id)
        val target = todo("target", 2).copy(groupId = parent.id)

        assertIs<PrerequisiteValidation.Valid>(
            OrderingPolicy.validateProposedPrerequisites(
                listOf(parent, from, target), emptyList(), target, setOf(from.id),
            ),
        )
    }

    @Test
    fun proposedPrerequisiteRejectsMissingParent() {
        val from = todo("from", 1).copy(groupId = "missing")
        val target = todo("target", 2).copy(groupId = "missing")

        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(from, target), emptyList(), target, setOf(from.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.INVALID_PARENT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsInactiveParent() {
        val parent = todo("parent", 0).copy(isGroup = true, lifecycle = ItemLifecycle.COMPLETED)
        val from = todo("from", 1).copy(groupId = parent.id)
        val target = todo("target", 2).copy(groupId = parent.id)

        val result = OrderingPolicy.validateProposedPrerequisites(
            listOf(parent, from, target), emptyList(), target, setOf(from.id),
        )

        assertIs<PrerequisiteValidation.Invalid>(result)
        assertEquals(PrerequisiteRejectionReason.INVALID_PARENT, result.reason)
    }

    @Test
    fun proposedPrerequisiteRejectsWrongKindOrNonGroupParent() {
        val wrongKindParent = todo("memo-parent", 0).copy(kind = ItemKind.MEMO, todo = null, isGroup = true)
        val from = todo("from", 1).copy(groupId = wrongKindParent.id)
        val target = todo("target", 2).copy(groupId = wrongKindParent.id)
        val wrongKind = OrderingPolicy.validateProposedPrerequisites(
            listOf(wrongKindParent, from, target), emptyList(), target, setOf(from.id),
        )
        assertIs<PrerequisiteValidation.Invalid>(wrongKind)
        assertEquals(PrerequisiteRejectionReason.INVALID_PARENT, wrongKind.reason)

        val nonGroupParent = todo("leaf-parent", 0)
        val fromUnderLeaf = from.copy(groupId = nonGroupParent.id)
        val targetUnderLeaf = target.copy(groupId = nonGroupParent.id)
        val nonGroup = OrderingPolicy.validateProposedPrerequisites(
            listOf(nonGroupParent, fromUnderLeaf, targetUnderLeaf), emptyList(), targetUnderLeaf, setOf(fromUnderLeaf.id),
        )
        assertIs<PrerequisiteValidation.Invalid>(nonGroup)
        assertEquals(PrerequisiteRejectionReason.INVALID_PARENT, nonGroup.reason)
    }

    @Test
    fun canonicalSortIgnoresLegacyRelationThatConflictsWithImmutableOrder() {
        val early = todo("early", 1, scheduledAt = 10)
        val late = todo("late", 2, scheduledAt = 11)
        val legacy = ItemRelation("legacy", late.id, early.id, RelationType.REQUIRED_BEFORE, 0)

        assertEquals(listOf("early", "late"), OrderingPolicy.canonicalSort(listOf(late, early), listOf(legacy)).map { it.id })
    }

    @Test
    fun changingToNonTodoRetainsNeitherRelationDirection() {
        val relations = listOf(
            ItemRelation("in", "before", "changed", RelationType.REQUIRED_BEFORE, 0),
            ItemRelation("out", "changed", "after", RelationType.RECOMMENDED_BEFORE, 1),
            ItemRelation("other", "before", "after", RelationType.RECOMMENDED_BEFORE, 2),
        )

        val retained = OrderingPolicy.relationsAfterKindChange("changed", ItemKind.MEMO, relations)

        assertEquals(listOf("other"), retained.map { it.id })
    }

    private fun todo(
        id: String,
        rank: Long,
        scheduledAt: Int? = null,
        priority: Priority = Priority.NORMAL,
    ) = AppItem(
        id = id,
        kind = ItemKind.TODO,
        title = id,
        manualRank = rank,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        todo = TodoDetail(scheduledAtEpochMillis = scheduledAt?.times(60_000L), priority = priority),
    )
}
