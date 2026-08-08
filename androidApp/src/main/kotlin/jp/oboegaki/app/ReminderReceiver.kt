package jp.oboegaki.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.oboegaki.core.domain.ReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ReminderReceiver : BroadcastReceiver() {
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
                        deliver(context, app, intent)
                    } catch (_: Throwable) {
                        intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID)?.let { itemId ->
                            ReminderNotificationSupport.cancelSafely(
                                context,
                                NotificationIdentity.notificationId(context, itemId),
                            )
                        }
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
    ) {
        val itemId = intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID)
            ?.takeIf(String::isNotEmpty) ?: return
        if (!intent.hasExtra(NotificationContract.EXTRA_REVISION) ||
            !intent.hasExtra(NotificationContract.EXTRA_SCHEDULED_AT)
        ) return
        val revision = intent.getLongExtra(NotificationContract.EXTRA_REVISION, Long.MIN_VALUE)
        val scheduledAt = intent.getLongExtra(NotificationContract.EXTRA_SCHEDULED_AT, Long.MIN_VALUE)
        val item = app.repository.getItem(itemId) ?: return
        if (!ReminderPolicy.isDeliveryEligible(item, revision, scheduledAt, System.currentTimeMillis())) return
        val settings = app.repository.getSettings()
        val notification = ReminderNotificationSupport.createReminderNotification(
            context = context.applicationContext,
            itemId = item.id,
            title = item.title,
            revision = item.revision,
            showContent = settings.showReminderContentOnLockScreen,
            actionsEnabled = settings.reminderNotificationActionsEnabled,
        )
        ReminderNotificationSupport.notifySafely(
            context,
            NotificationIdentity.notificationId(context, item.id),
            notification,
        )
    }
}
