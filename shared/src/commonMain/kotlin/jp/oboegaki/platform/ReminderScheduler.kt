package jp.oboegaki.platform

data class Reminder(
    val itemId: String,
    val title: String,
    val scheduledAtEpochMillis: Long,
)

sealed interface ReminderResult {
    data object Scheduled : ReminderResult
    data object PermissionRequired : ReminderResult
    data class Failed(val reason: String) : ReminderResult
}

interface ReminderScheduler {
    suspend fun schedule(reminder: Reminder): ReminderResult
    suspend fun cancel(itemId: String)
    suspend fun reconcileAll(reminders: List<Reminder>, staleItemIds: Set<String> = emptySet())
}

object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(reminder: Reminder) = ReminderResult.Scheduled
    override suspend fun cancel(itemId: String) = Unit
    override suspend fun reconcileAll(reminders: List<Reminder>, staleItemIds: Set<String>) = Unit
}
