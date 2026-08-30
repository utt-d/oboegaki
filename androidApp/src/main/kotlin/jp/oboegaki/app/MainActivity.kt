package jp.oboegaki.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.oboegaki.ui.OboegakiApp
import jp.oboegaki.platform.notifyNotificationSettingsResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val rescheduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationItemId by mutableStateOf<String?>(null)
    private var notificationRequestKey by mutableLongStateOf(0L)
    private lateinit var backupFileGateway: AndroidBackupFileGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        requestPostNotificationsIfNeeded()
        val app = OboegakiApplication.from(this)
        backupFileGateway = AndroidBackupFileGateway(this)
        setContent {
            OboegakiApp(
                repository = app.repository,
                calendarExporter = AndroidCalendarExporter(this@MainActivity),
                backupFileGateway = backupFileGateway,
                focusItemId = notificationItemId,
                focusRequestKey = notificationRequestKey,
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Force-stop and process death remove alarms. A lifecycle start is a
        // deliberate app entry point, so reconcile once here rather than from
        // composition or every frame.
        val app = OboegakiApplication.from(this)
        rescheduleScope.launch {
            runCatching { app.repository.rescheduleAllReminders() }
        }
    }

    override fun onResume() {
        super.onResume()
        notifyNotificationSettingsResumed()
    }

    override fun onDestroy() {
        rescheduleScope.cancel()
        super.onDestroy()
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val itemId = if (intent?.hasExtra(NotificationContract.EXTRA_ITEM_ID) == true) {
            intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID)
        } else {
            itemIdFromUri(intent?.data)
        }
        if (!itemId.isNullOrEmpty()) {
            notificationItemId = itemId
            notificationRequestKey += 1
        }
    }

    private fun itemIdFromUri(uri: Uri?): String? {
        val segments = uri?.pathSegments ?: return null
        val itemIndex = segments.indexOf("item")
        if (itemIndex < 0) return null
        return segments.getOrNull(itemIndex + 1)?.takeIf(String::isNotEmpty)
    }

    private fun requestPostNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) return
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)) return
        preferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private companion object {
        const val PREFERENCES = "notification_diagnostics"
        const val NOTIFICATION_PERMISSION_REQUESTED = "post_notifications_requested"
    }
}
