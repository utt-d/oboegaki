package jp.oboegaki.platform

import kotlinx.coroutines.flow.Flow

enum class NotificationState {
    ENABLED,
    POST_NOTIFICATIONS_REQUIRED,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    CHANNEL_NOT_READY,
    PLATFORM_MANAGED,
}

data class NotificationStatus(val state: NotificationState)

sealed interface NotificationTestResult {
    data object Sent : NotificationTestResult
    data object PermissionRequired : NotificationTestResult
    data object AppNotificationsDisabled : NotificationTestResult
    data object ChannelDisabled : NotificationTestResult
    data object ChannelNotReady : NotificationTestResult
    data class Failed(val reason: String) : NotificationTestResult
    data object PlatformManaged : NotificationTestResult
}

expect fun initializeNotificationDiagnostics(platformContext: Any)
expect fun currentNotificationStatus(): NotificationStatus
expect fun notificationSettingsEvents(): Flow<Unit>
expect fun notifyNotificationSettingsResumed()
expect fun openNotificationSettings()
expect fun tryTestNotification(): NotificationTestResult
