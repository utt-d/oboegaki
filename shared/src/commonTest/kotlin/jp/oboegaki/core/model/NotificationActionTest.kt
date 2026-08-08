package jp.oboegaki.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationActionTest {
    @Test
    fun undoTokenKeepsTheTargetOperationAndExpiry() {
        val token = NotificationUndoToken(
            operationId = "operation-1",
            itemId = "item-1",
            action = NotificationAction.COMPLETE,
            expiresAtEpochMillis = 10_000L,
        )

        assertEquals("operation-1", token.operationId)
        assertEquals("item-1", token.itemId)
        assertEquals(NotificationAction.COMPLETE, token.action)
        assertEquals(10_000L, token.expiresAtEpochMillis)
    }

    @Test
    fun notificationActionResultIsTypedForAlreadyHandledItems() {
        val result: NotificationActionResult = NotificationActionResult.ItemNotActive(ItemLifecycle.COMPLETED)

        assertEquals(ItemLifecycle.COMPLETED, (result as NotificationActionResult.ItemNotActive).lifecycle)
    }

    @Test
    fun notificationActionResultCanRejectNonLeafItems() {
        val result: NotificationActionResult = NotificationActionResult.ItemNotEligible(ItemKind.TODO, true)

        assertEquals(true, (result as NotificationActionResult.ItemNotEligible).isGroup)
    }

    @Test
    fun undoTokenExpiresAfterItsTenSecondWindow() {
        val token = NotificationUndoToken("op", "item", NotificationAction.DEFER, 10_000L)

        assertEquals(false, token.isExpired(10_000L))
        assertEquals(true, token.isExpired(10_001L))
    }
}
