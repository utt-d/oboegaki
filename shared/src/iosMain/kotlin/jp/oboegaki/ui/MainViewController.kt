package jp.oboegaki.ui

import androidx.compose.ui.window.ComposeUIViewController
import jp.oboegaki.core.data.RoomItemRepository
import jp.oboegaki.core.data.buildDatabase
import jp.oboegaki.core.data.databaseBuilder
import jp.oboegaki.platform.IosReminderScheduler
import jp.oboegaki.platform.IosCalendarExporter
import platform.Foundation.NSBundle

fun MainViewController() = ComposeUIViewController {
    val appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    OboegakiApp(
        repository = RoomItemRepository(
            database = buildDatabase(databaseBuilder()),
            reminderScheduler = IosReminderScheduler(),
            appVersion = appVersion?.takeIf { it.isNotBlank() } ?: "unknown",
        ),
        calendarExporter = IosCalendarExporter(),
    )
}
