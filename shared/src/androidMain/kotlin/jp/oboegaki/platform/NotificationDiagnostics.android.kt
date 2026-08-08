package jp.oboegaki.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private object AndroidNotificationDiagnosticsState {
    var context: Context? = null
}

private val notificationSettingsEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

private const val REMINDER_CHANNEL = "todo_reminders"
private const val TEST_NOTIFICATION_ID = 0x4f424f

actual fun initializeNotificationDiagnostics(platformContext: Any) {
    AndroidNotificationDiagnosticsState.context = (platformContext as Context).applicationContext
}

actual fun currentNotificationStatus(): NotificationStatus {
    val context = AndroidNotificationDiagnosticsState.context ?: return NotificationStatus(NotificationState.PLATFORM_MANAGED)
    val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return NotificationStatus(NotificationState.POST_NOTIFICATIONS_REQUIRED)

    if (!appNotificationsEnabled) {
        return NotificationStatus(NotificationState.APP_NOTIFICATIONS_DISABLED)
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26 &&
        manager.getNotificationChannel(REMINDER_CHANNEL)?.let { channel ->
            channel.importance == NotificationManager.IMPORTANCE_NONE
        } == true
    ) return NotificationStatus(NotificationState.CHANNEL_DISABLED)
    if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(REMINDER_CHANNEL) == null) {
        return NotificationStatus(NotificationState.CHANNEL_NOT_READY)
    }
    return NotificationStatus(NotificationState.ENABLED)
}

actual fun notificationSettingsEvents(): Flow<Unit> = notificationSettingsEventFlow.asSharedFlow()

actual fun notifyNotificationSettingsResumed() {
    notificationSettingsEventFlow.tryEmit(Unit)
}

actual fun openNotificationSettings() {
    val context = AndroidNotificationDiagnosticsState.context ?: return
    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT < 26) {
        runCatching { context.startActivity(detailsIntent) }
        return
    }
    val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(notificationIntent) }
        .onFailure { runCatching { context.startActivity(detailsIntent) } }
}

actual fun tryTestNotification(): NotificationTestResult {
    val context = AndroidNotificationDiagnosticsState.context
        ?: return NotificationTestResult.Failed("通知の状態を確認できません")
    return when (currentNotificationStatus().state) {
        NotificationState.ENABLED -> runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, REMINDER_CHANNEL)
            } else {
                Notification.Builder(context)
            }
            manager.notify(
                TEST_NOTIFICATION_ID,
                builder
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("通知を試します")
                    .setContentText("通知は有効です")
                    .setAutoCancel(true)
                    .build(),
            )
            NotificationTestResult.Sent
        }.getOrElse { NotificationTestResult.Failed(it.message ?: "通知を出せませんでした") }
        NotificationState.POST_NOTIFICATIONS_REQUIRED -> NotificationTestResult.PermissionRequired
        NotificationState.APP_NOTIFICATIONS_DISABLED -> NotificationTestResult.AppNotificationsDisabled
        NotificationState.CHANNEL_DISABLED -> NotificationTestResult.ChannelDisabled
        NotificationState.CHANNEL_NOT_READY -> NotificationTestResult.ChannelNotReady
        NotificationState.PLATFORM_MANAGED -> NotificationTestResult.PlatformManaged
    }
}
