package jp.oboegaki.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.platform.Reminder
import jp.oboegaki.platform.ReminderResult
import jp.oboegaki.platform.ReminderScheduler

class AndroidReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val settings = appContext.getSharedPreferences(REMINDER_SETTINGS, Context.MODE_PRIVATE)

    override suspend fun schedule(reminder: Reminder): ReminderResult = runCatching {
        cancelLegacy(reminder.itemId)
        if (reminder.scheduledAtEpochMillis <= System.currentTimeMillis()) return ReminderResult.Failed("時刻が過ぎています")
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.scheduledAtEpochMillis,
            pendingIntent(reminder.itemId, reminder.title, reminder.scheduledAtEpochMillis, reminder.revision),
        )
        ReminderResult.Scheduled
    }.getOrElse { ReminderResult.Failed(it.message ?: "通知を登録できませんでした") }

    override suspend fun cancel(itemId: String) {
        // PendingIntent identity ignores extras, so the empty values still cancel
        // alarms created with any title, scheduled time, or revision.
        alarmManager.cancel(pendingIntent(itemId, "", 0L, 0L))
        cancelLegacy(itemId)
    }

    override suspend fun reconcileAll(reminders: List<Reminder>, staleItemIds: Set<String>) {
        staleItemIds.forEach { cancel(it) }
        reminders.forEach { schedule(it) }
    }

    override suspend fun applySettings(value: AppSettings) {
        settings.edit()
            .putBoolean(KEY_SHOW_CONTENT, value.showReminderContentOnLockScreen)
            .putBoolean(KEY_ACTIONS_ENABLED, value.reminderNotificationActionsEnabled)
            .commit()
    }

    fun showReminderContentOnLockScreen(): Boolean = settings.getBoolean(KEY_SHOW_CONTENT, false)

    fun reminderNotificationActionsEnabled(): Boolean = settings.getBoolean(KEY_ACTIONS_ENABLED, true)

    private fun pendingIntent(
        itemId: String,
        title: String,
        scheduledAtEpochMillis: Long,
        revision: Long,
    ): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java)
            .setData(Uri.parse("oboegaki://reminder/item/${Uri.encode(itemId)}/operation/schedule"))
            .putExtra(NotificationContract.EXTRA_ITEM_ID, itemId)
            .putExtra(NotificationContract.EXTRA_TITLE, title)
            .putExtra(NotificationContract.EXTRA_REVISION, revision)
            .putExtra(NotificationContract.EXTRA_SCHEDULED_AT, scheduledAtEpochMillis)
        return PendingIntent.getBroadcast(
            appContext,
            PendingIntentIdentity.requestCode(itemId, "schedule", "reminder"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Cancels alarms created by versions that used the old URI/request identity. */
    private fun cancelLegacy(itemId: String) {
        val legacyIntents = listOf(
            PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(appContext, ReminderReceiver::class.java)
                    .setData(Uri.parse("oboegaki://reminder/item/$itemId")),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ),
            PendingIntent.getBroadcast(
                appContext,
                itemId.hashCode(),
                Intent(appContext, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        legacyIntents.filterNotNull().forEach { legacy ->
            alarmManager.cancel(legacy)
            legacy.cancel()
        }
    }

    private companion object {
        const val REMINDER_SETTINGS = "reminder_settings"
        const val KEY_SHOW_CONTENT = "show_reminder_content_on_lock_screen"
        const val KEY_ACTIONS_ENABLED = "reminder_notification_actions_enabled"
    }
}
