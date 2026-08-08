package jp.oboegaki.core.model

/** Actions exposed by an operating-system reminder notification. */
enum class NotificationAction {
    COMPLETE,
    DEFER,
}

data class NotificationUndoToken(
    val operationId: String,
    val itemId: String,
    val action: NotificationAction,
    val expiresAtEpochMillis: Long,
) {
    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis
}

sealed interface NotificationActionResult {
    data class Applied(
        val action: NotificationAction,
        val itemId: String,
        val title: String,
        val undoToken: NotificationUndoToken,
        val shouldSuggestSplit: Boolean = false,
    ) : NotificationActionResult

    data object ActionsDisabled : NotificationActionResult
    data object ItemNotFound : NotificationActionResult
    data class ItemNotActive(val lifecycle: ItemLifecycle) : NotificationActionResult
    data class ItemNotEligible(val kind: ItemKind, val isGroup: Boolean) : NotificationActionResult
    data object StaleNotification : NotificationActionResult
    data class Failed(val reason: String) : NotificationActionResult
}

sealed interface NotificationUndoResult {
    data object Applied : NotificationUndoResult
    data object Expired : NotificationUndoResult
    data object AlreadyReverted : NotificationUndoResult
    data object DifferentOperationAlreadyHappened : NotificationUndoResult
    data object NotFound : NotificationUndoResult
    data class Failed(val reason: String) : NotificationUndoResult
}
