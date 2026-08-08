package jp.oboegaki.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual fun initializeNotificationDiagnostics(platformContext: Any) = Unit

actual fun currentNotificationStatus(): NotificationStatus =
    NotificationStatus(NotificationState.PLATFORM_MANAGED)

actual fun notificationSettingsEvents(): Flow<Unit> = emptyFlow()

actual fun notifyNotificationSettingsResumed() = Unit

actual fun openNotificationSettings() = Unit

actual fun tryTestNotification(): NotificationTestResult = NotificationTestResult.PlatformManaged
