package jp.oboegaki.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class UndoPolicyTest {
    @Test
    fun expiryBoundaryIsStillUndoable() {
        val target = operation("op", "COMPLETE", 10, 20)
        assertEquals(
            UndoEligibility.Allowed(UndoRestoreScope.ITEMS_ONLY),
            UndoPolicy.evaluate(target, 20, emptyList()),
        )
        assertEquals(UndoEligibility.Expired, UndoPolicy.evaluate(target, 21, emptyList()))
    }

    @Test
    fun laterOperationAtSameTimestampBlocksNotificationUndo() {
        val target = operation("op", "NOTIFICATION_DEFER:item", 10, 20)
        val later = operation("later", "COMPLETE", 10, 20)
        assertEquals(UndoEligibility.LaterOperation, UndoPolicy.evaluate(target, 10, listOf(later)))
    }

    @Test
    fun normalUndoDoesNotCrossUnrevertedNotificationAndImportRestoresAll() {
        val normal = operation("normal", "COMPLETE", 10, 20)
        val notification = operation("notification", "NOTIFICATION_COMPLETE:item", 11, 20)
        assertEquals(UndoEligibility.LaterOperation, UndoPolicy.evaluate(normal, 11, listOf(notification)))
        assertEquals(
            UndoEligibility.Allowed(UndoRestoreScope.FULL_STATE),
            UndoPolicy.evaluate(operation("import", "IMPORT", 10, 20), 20, emptyList()),
        )
        assertEquals(
            UndoEligibility.Allowed(UndoRestoreScope.ITEMS_ONLY),
            UndoPolicy.evaluate(normal, 20, listOf(notification.copy(revertedAtEpochMillis = 15))),
        )
    }

    private fun operation(id: String, type: String, created: Long, expires: Long) = UndoOperation(
        operationId = id,
        type = type,
        createdAtEpochMillis = created,
        expiresAtEpochMillis = expires,
    )
}
