package jp.oboegaki.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.nio.ByteBuffer
import java.security.MessageDigest

internal object NotificationContract {
    const val ACTION_COMPLETE = "jp.oboegaki.app.action.COMPLETE"
    const val ACTION_DEFER = "jp.oboegaki.app.action.DEFER"
    const val ACTION_UNDO = "jp.oboegaki.app.action.UNDO"
    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_REVISION = "revision"
    const val EXTRA_SCHEDULED_AT = "scheduled_at_epoch_millis"
    const val EXTRA_OPERATION_ID = "operation_id"
    const val EXTRA_NOTIFICATION_ACTION = "notification_action"
    const val EXTRA_EXPIRES_AT = "expires_at"
    const val EXTRA_RETRY_ATTEMPT = "retry_attempt"
}

internal object PendingIntentIdentity {
    fun requestCode(itemId: String, action: String, operation: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$itemId:$action:$operation".encodeToByteArray())
        val value = ByteBuffer.wrap(digest).int and Int.MAX_VALUE
        return if (value == 0) 1 else value
    }
}

internal object NotificationIdentity {
    private const val PREFERENCES = "reminder_identity"
    private const val ID_PREFIX = "notification_id:"

    fun notificationId(context: Context, itemId: String): Int {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "$ID_PREFIX$itemId"
        preferences.getInt(key, 0).takeIf { it != 0 }?.let { return it }

        val digest = MessageDigest.getInstance("SHA-256").digest(itemId.encodeToByteArray())
        var candidate = ByteBuffer.wrap(digest).int and Int.MAX_VALUE
        if (candidate == 0) candidate = 1
        val used = preferences.all
            .filterKeys { it.startsWith(ID_PREFIX) }
            .values
            .filterIsInstance<Int>()
            .toHashSet()
        while (candidate in used || candidate == 0) {
            candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        }
        preferences.edit().putInt(key, candidate).apply()
        return candidate
    }
}

internal object ReminderNotificationSupport {
    fun confirmationPendingIntent(context: Context, itemId: String, operation: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("oboegaki://notification/item/${Uri.encode(itemId)}/action/confirm/operation/${Uri.encode(operation)}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NotificationContract.EXTRA_ITEM_ID, itemId)
        return PendingIntent.getActivity(
            context,
            PendingIntentIdentity.requestCode(itemId, "confirm", operation),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun actionPendingIntent(
        context: Context,
        itemId: String,
        action: String,
        revision: Long,
        operation: String,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("oboegaki://notification/item/${Uri.encode(itemId)}/action/${Uri.encode(action)}/operation/${Uri.encode(operation)}"))
            .putExtra(NotificationContract.EXTRA_ITEM_ID, itemId)
            .putExtra(NotificationContract.EXTRA_REVISION, revision)
        return PendingIntent.getBroadcast(
            context,
            PendingIntentIdentity.requestCode(itemId, action, operation),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun undoPendingIntent(
        context: Context,
        itemId: String,
        action: String,
        operationId: String,
        expiresAt: Long,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationContract.ACTION_UNDO)
            .setData(Uri.parse("oboegaki://notification/item/${Uri.encode(itemId)}/action/undo/operation/${Uri.encode(operationId)}"))
            .putExtra(NotificationContract.EXTRA_ITEM_ID, itemId)
            .putExtra(NotificationContract.EXTRA_NOTIFICATION_ACTION, action)
            .putExtra(NotificationContract.EXTRA_OPERATION_ID, operationId)
            .putExtra(NotificationContract.EXTRA_EXPIRES_AT, expiresAt)
        return PendingIntent.getBroadcast(
            context,
            PendingIntentIdentity.requestCode(itemId, "undo", operationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createReminderNotification(
        context: Context,
        itemId: String,
        title: String,
        revision: Long,
        showContent: Boolean,
        actionsEnabled: Boolean,
    ): Notification {
        val operation = "reminder"
        val confirm = confirmationPendingIntent(context, itemId, operation)
        val builder = NotificationCompat.Builder(context, OboegakiApplication.REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (showContent) title else context.getString(R.string.notification_generic_title))
            .setContentText(
                if (showContent) context.getString(R.string.notification_reminder_text)
                else context.getString(R.string.notification_generic_text),
            )
            .setContentIntent(confirm)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context))
            .setAutoCancel(true)
        if (actionsEnabled) {
            builder.addAction(NotificationCompat.Action.Builder(
                android.R.drawable.ic_popup_reminder,
                context.getString(R.string.notification_confirm),
                confirm,
            ).build())
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_popup_reminder,
                    context.getString(R.string.notification_complete),
                    actionPendingIntent(context, itemId, NotificationContract.ACTION_COMPLETE, revision, operation),
                ).build(),
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_popup_reminder,
                    context.getString(R.string.notification_defer),
                    actionPendingIntent(context, itemId, NotificationContract.ACTION_DEFER, revision, operation),
                ).build(),
            )
        }
        return builder.build()
    }

    fun createActionResultNotification(
        context: Context,
        itemId: String,
        title: String,
        message: String,
        showContent: Boolean,
        actionsEnabled: Boolean,
        undoAction: String?,
        operationId: String?,
        expiresAt: Long?,
    ): Notification {
        val builder = NotificationCompat.Builder(context, OboegakiApplication.REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(message)
            .setContentText(
                if (showContent) title else context.getString(R.string.notification_updated_text),
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context))
            .setAutoCancel(true)
            .setTimeoutAfter(10_000L)
        if (itemId.isNotEmpty()) {
            builder.setContentIntent(confirmationPendingIntent(context, itemId, "result"))
        }
        if (actionsEnabled && undoAction != null && operationId != null && expiresAt != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_popup_reminder,
                    context.getString(R.string.notification_undo),
                    undoPendingIntent(context, itemId, undoAction, operationId, expiresAt),
                ).build(),
            )
        }
        return builder.build()
    }

    fun notifySafely(context: Context, id: Int, notification: Notification): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
            true
        }.getOrDefault(false)
    }

    fun cancelSafely(context: Context, id: Int) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(id) }
    }

    private fun publicVersion(context: Context): Notification = NotificationCompat.Builder(
        context,
        OboegakiApplication.REMINDER_CHANNEL,
    )
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentText(context.getString(R.string.notification_generic_title))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()
}
