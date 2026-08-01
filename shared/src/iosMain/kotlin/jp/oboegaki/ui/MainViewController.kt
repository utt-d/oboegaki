package jp.oboegaki.ui

import androidx.compose.ui.window.ComposeUIViewController
import jp.oboegaki.core.data.RoomItemRepository
import jp.oboegaki.core.data.buildDatabase
import jp.oboegaki.core.data.databaseBuilder
import jp.oboegaki.platform.IosReminderScheduler
import jp.oboegaki.platform.IosCalendarExporter

fun MainViewController() = ComposeUIViewController {
    OboegakiApp(
        repository = RoomItemRepository(
            database = buildDatabase(databaseBuilder()),
            reminderScheduler = IosReminderScheduler(),
        ),
        calendarExporter = IosCalendarExporter(),
    )
}
