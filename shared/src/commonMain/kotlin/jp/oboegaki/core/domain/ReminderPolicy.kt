package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle

/** The shared eligibility rule used by Android and other reminder schedulers. */
object ReminderPolicy {
    fun isEligible(item: AppItem, nowEpochMillis: Long): Boolean {
        val scheduled = item.todo?.scheduledAtEpochMillis
        return item.kind == ItemKind.TODO &&
            !item.isGroup &&
            item.lifecycle == ItemLifecycle.ACTIVE &&
            scheduled != null &&
            scheduled > nowEpochMillis
    }

    /** Delivery accepts an alarm that is already due; scheduling uses the future-only rule above. */
    fun isDeliveryEligible(
        item: AppItem,
        expectedRevision: Long,
        expectedScheduledAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): Boolean {
        val scheduled = item.todo?.scheduledAtEpochMillis
        return item.kind == ItemKind.TODO &&
            !item.isGroup &&
            item.lifecycle == ItemLifecycle.ACTIVE &&
            item.revision == expectedRevision &&
            scheduled == expectedScheduledAtEpochMillis &&
            expectedScheduledAtEpochMillis <= nowEpochMillis
    }
}
