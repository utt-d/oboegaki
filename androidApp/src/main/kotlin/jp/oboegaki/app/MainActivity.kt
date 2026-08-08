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

class MainActivity : ComponentActivity() {
    private var notificationItemId by mutableStateOf<String?>(null)
    private var notificationRequestKey by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        val app = OboegakiApplication.from(this)
        setContent {
            OboegakiApp(
                repository = app.repository,
                calendarExporter = AndroidCalendarExporter(this@MainActivity),
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
}
