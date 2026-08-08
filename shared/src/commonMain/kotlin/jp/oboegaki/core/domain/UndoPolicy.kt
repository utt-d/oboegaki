package jp.oboegaki.core.domain

data class UndoOperation(
    val operationId: String,
    val type: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val revertedAtEpochMillis: Long? = null,
)

enum class UndoRestoreScope {
    ITEMS_ONLY,
    FULL_STATE,
}

sealed interface UndoEligibility {
    data class Allowed(val scope: UndoRestoreScope) : UndoEligibility
    data object Expired : UndoEligibility
    data object AlreadyReverted : UndoEligibility
    data object LaterOperation : UndoEligibility
}

/** Pure conflict and expiry rules shared by normal and notification undo paths. */
object UndoPolicy {
    fun evaluate(
        target: UndoOperation,
        nowEpochMillis: Long,
        laterOperations: List<UndoOperation>,
    ): UndoEligibility {
        if (target.revertedAtEpochMillis != null) return UndoEligibility.AlreadyReverted
        if (nowEpochMillis > target.expiresAtEpochMillis) return UndoEligibility.Expired
        val later = laterOperations.any { candidate ->
            candidate.operationId != target.operationId &&
                candidate.createdAtEpochMillis >= target.createdAtEpochMillis &&
                (target.type.startsWith("NOTIFICATION_") || candidate.revertedAtEpochMillis == null)
        }
        if (later) return UndoEligibility.LaterOperation
        return UndoEligibility.Allowed(
            if (target.type == "IMPORT") UndoRestoreScope.FULL_STATE else UndoRestoreScope.ITEMS_ONLY,
        )
    }
}
