package jp.oboegaki.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import jp.oboegaki.platform.Reminder
import jp.oboegaki.platform.ReminderResult
import jp.oboegaki.platform.ReminderScheduler

class AndroidReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(reminder: Reminder): ReminderResult = runCatching {
        cancelLegacy(reminder.itemId)
        if (reminder.scheduledAtEpochMillis <= System.currentTimeMillis()) return ReminderResult.Failed("時刻が過ぎています")
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.scheduledAtEpochMillis,
            pendingIntent(reminder.itemId, reminder.title),
        )
        ReminderResult.Scheduled
    }.getOrElse { ReminderResult.Failed(it.message ?: "通知を登録できませんでした") }

    override suspend fun cancel(itemId: String) {
        alarmManager.cancel(pendingIntent(itemId, ""))
        cancelLegacy(itemId)
    }

    override suspend fun reconcileAll(reminders: List<Reminder>, staleItemIds: Set<String>) {
        staleItemIds.forEach { cancel(it) }
        reminders.forEach { schedule(it) }
    }

    private fun pendingIntent(itemId: String, title: String): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java)
            .setData(Uri.parse("oboegaki://reminder/item/$itemId"))
            .putExtra("item_id", itemId)
            .putExtra("title", title)
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Cancels alarms created by versions that used String.hashCode() identity. */
    private fun cancelLegacy(itemId: String) {
        val legacyIntent = Intent(appContext, ReminderReceiver::class.java)
        val legacy = PendingIntent.getBroadcast(
            appContext,
            itemId.hashCode(),
            legacyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(legacy)
        legacy.cancel()
    }
}
