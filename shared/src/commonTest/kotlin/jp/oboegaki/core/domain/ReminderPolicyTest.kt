package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.TodoDetail
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderPolicyTest {
    @Test
    fun onlyFutureActiveLeafTodosAreEligibleForRescheduling() {
        val base = AppItem(
            id = "item",
            kind = ItemKind.TODO,
            title = "やること",
            manualRank = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            todo = TodoDetail(scheduledAtEpochMillis = 20_000L),
        )

        assertTrue(ReminderPolicy.isEligible(base, 10_000L))
        assertFalse(ReminderPolicy.isEligible(base.copy(todo = TodoDetail(scheduledAtEpochMillis = 10_000L)), 10_000L))
        assertFalse(ReminderPolicy.isEligible(base.copy(lifecycle = ItemLifecycle.COMPLETED), 10_000L))
        assertFalse(ReminderPolicy.isEligible(base.copy(isGroup = true), 10_000L))
        assertFalse(ReminderPolicy.isEligible(base.copy(kind = ItemKind.MEMO, todo = null), 10_000L))
    }

    @Test
    fun deliveryRequiresRevisionAndScheduledTimeAndAcceptsDueAlarm() {
        val item = AppItem(
            id = "item",
            kind = ItemKind.TODO,
            title = "やること",
            manualRank = 0,
            revision = 4,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            todo = TodoDetail(scheduledAtEpochMillis = 100L),
        )
        assertTrue(ReminderPolicy.isDeliveryEligible(item, 4, 100L, 100L))
        assertFalse(ReminderPolicy.isDeliveryEligible(item, 3, 100L, 100L))
        assertFalse(ReminderPolicy.isDeliveryEligible(item, 4, 101L, 100L))
    }

    @Test
    fun deliveryRejectsGroupsAndNonTodos() {
        val item = AppItem(
            id = "group",
            kind = ItemKind.TODO,
            title = "グループ",
            manualRank = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            isGroup = true,
            todo = TodoDetail(scheduledAtEpochMillis = 1L),
        )
        assertFalse(ReminderPolicy.isDeliveryEligible(item, 0, 1L, 1L))
    }
}
