package jp.oboegaki.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.net.Uri
import jp.oboegaki.core.domain.ReminderPolicy
import jp.oboegaki.platform.NotificationState
import jp.oboegaki.platform.currentNotificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ReminderReceiver : BroadcastReceiver() {
    private enum class DeliveryResult { DELIVERED, IGNORED, RETRYABLE }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                runCatching { pendingResult.finish() }
            }
        }

        try {
            val app = OboegakiApplication.from(context)
            val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                workScope.launch {
                    try {
                        if (deliver(context, app, intent) == DeliveryResult.RETRYABLE &&
                            intent.getIntExtra(NotificationContract.EXTRA_RETRY_ATTEMPT, 0) == 0
                        ) {
                            scheduleRetry(context, intent)
                        }
                    } catch (_: Throwable) {
                        // Malformed data and non-transient receiver failures are
                        // intentionally not retried; the original alarm is done.
                    } finally {
                        finishOnce()
                        runCatching { workScope.cancel() }
                    }
                }
            } catch (_: Throwable) {
                runCatching { workScope.cancel() }
                finishOnce()
            }
        } catch (_: Throwable) {
            finishOnce()
        }
    }

    private suspend fun deliver(
        context: Context,
        app: OboegakiApplication,
        intent: Intent,
    ): DeliveryResult {
        val itemId = intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID)
            ?.takeIf(String::isNotEmpty) ?: return DeliveryResult.IGNORED
        if (!intent.hasExtra(NotificationContract.EXTRA_REVISION) ||
            !intent.hasExtra(NotificationContract.EXTRA_SCHEDULED_AT)
        ) return DeliveryResult.IGNORED
        val revision = intent.getLongExtra(NotificationContract.EXTRA_REVISION, Long.MIN_VALUE)
        val scheduledAt = intent.getLongExtra(NotificationContract.EXTRA_SCHEDULED_AT, Long.MIN_VALUE)
        val item = try {
            app.repository.getItem(itemId)
        } catch (_: Throwable) {
            return DeliveryResult.RETRYABLE
        } ?: return DeliveryResult.IGNORED
        if (!ReminderPolicy.isDeliveryEligible(item, revision, scheduledAt, System.currentTimeMillis())) {
            return DeliveryResult.IGNORED
        }
        if (currentNotificationStatus().state != NotificationState.ENABLED) {
            return DeliveryResult.IGNORED
        }
        val settings = try {
            app.repository.getSettings()
        } catch (_: Throwable) {
            return DeliveryResult.RETRYABLE
        }
        val notification = ReminderNotificationSupport.createReminderNotification(
            context = context.applicationContext,
            itemId = item.id,
            title = item.title,
            revision = item.revision,
            showContent = settings.showReminderContentOnLockScreen,
            actionsEnabled = settings.reminderNotificationActionsEnabled,
        )
        val posted = ReminderNotificationSupport.notifySafely(
            context,
            NotificationIdentity.notificationId(context, item.id),
            notification,
        )
        if (posted) return DeliveryResult.DELIVERED
        return if (currentNotificationStatus().state == NotificationState.ENABLED) {
            DeliveryResult.RETRYABLE
        } else {
            DeliveryResult.IGNORED
        }
    }

    private fun scheduleRetry(context: Context, source: Intent) {
        val itemId = source.getStringExtra(NotificationContract.EXTRA_ITEM_ID)
            ?.takeIf(String::isNotEmpty) ?: return
        val retryIntent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("oboegaki://reminder/item/${Uri.encode(itemId)}/operation/schedule"))
            .putExtra(NotificationContract.EXTRA_ITEM_ID, itemId)
            .putExtra(NotificationContract.EXTRA_TITLE, source.getStringExtra(NotificationContract.EXTRA_TITLE).orEmpty())
            .putExtra(NotificationContract.EXTRA_REVISION, source.getLongExtra(NotificationContract.EXTRA_REVISION, Long.MIN_VALUE))
            .putExtra(NotificationContract.EXTRA_SCHEDULED_AT, source.getLongExtra(NotificationContract.EXTRA_SCHEDULED_AT, Long.MIN_VALUE))
            .putExtra(NotificationContract.EXTRA_RETRY_ATTEMPT, 1)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PendingIntentIdentity.requestCode(itemId, "schedule", "reminder"),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000L,
                pendingIntent,
            )
        }
    }
}
