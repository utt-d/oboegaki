package jp.oboegaki.ui

import androidx.compose.ui.window.ComposeUIViewController
import jp.oboegaki.core.data.RoomItemRepository
import jp.oboegaki.core.data.buildDatabase
import jp.oboegaki.core.data.databaseBuilder
import jp.oboegaki.platform.IosReminderScheduler
import jp.oboegaki.platform.IosCalendarExporter
import jp.oboegaki.platform.IosBackupFileGateway
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController
import kotlin.native.ref.WeakReference

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    val appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    var hostReference: WeakReference<UIViewController>? = null
    val backupFileGateway = IosBackupFileGateway { hostReference?.get() }
    val host = ComposeUIViewController {
        OboegakiApp(
            repository = RoomItemRepository(
                database = buildDatabase(databaseBuilder()),
                reminderScheduler = IosReminderScheduler(),
                appVersion = appVersion?.takeIf { it.isNotBlank() } ?: "unknown",
            ),
            calendarExporter = IosCalendarExporter(),
            backupFileGateway = backupFileGateway,
        )
    }
    hostReference = WeakReference(host)
    return host
}
