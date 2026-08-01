package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
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

