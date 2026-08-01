package jp.oboegaki.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IosReminderScheduler : ReminderScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun schedule(reminder: Reminder): ReminderResult {
        val seconds = (reminder.scheduledAtEpochMillis - currentTimeMillis()) / 1_000.0
        if (seconds <= 0.0) return ReminderResult.Failed("時刻が過ぎています")

        val permission = requestPermission()
        if (!permission) return ReminderResult.PermissionRequired

        val content = UNMutableNotificationContent().apply {
            setTitle(reminder.title)
            setBody("予定した時刻になりました")
            setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = seconds.coerceAtLeast(1.0),
            repeats = false,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = reminder.itemId,
            content = content,
            trigger = trigger,
        )
        return suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(request) { error: NSError? ->
                if (!continuation.isActive) return@addNotificationRequest
                continuation.resume(
                    if (error == null) ReminderResult.Scheduled
                    else ReminderResult.Failed(error.localizedDescription),
                )
            }
        }
    }

    override suspend fun cancel(itemId: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(itemId))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(itemId))
    }

    override suspend fun reconcileAll(reminders: List<Reminder>) {
        center.removeAllPendingNotificationRequests()
        reminders.forEach { schedule(it) }
    }

    private suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

    private fun currentTimeMillis(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()
}
