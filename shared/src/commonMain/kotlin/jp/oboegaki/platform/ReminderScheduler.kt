package jp.oboegaki.platform

import jp.oboegaki.core.model.AppSettings

data class Reminder(
    val itemId: String,
    val title: String,
    val scheduledAtEpochMillis: Long,
    val revision: Long = 0,
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
    suspend fun applySettings(settings: AppSettings) = Unit
}

object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(reminder: Reminder) = ReminderResult.Scheduled
    override suspend fun cancel(itemId: String) = Unit
    override suspend fun reconcileAll(reminders: List<Reminder>, staleItemIds: Set<String>) = Unit
}
