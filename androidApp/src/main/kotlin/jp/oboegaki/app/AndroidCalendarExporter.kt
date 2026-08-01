package jp.oboegaki.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import jp.oboegaki.platform.CalendarEventDraft
import jp.oboegaki.platform.CalendarExportResult
import jp.oboegaki.platform.CalendarExporter

class AndroidCalendarExporter(private val activity: Activity) : CalendarExporter {
    override suspend fun export(event: CalendarEventDraft): CalendarExportResult {
        val insertIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startAtEpochMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endAtEpochMillis)
        }
        return try {
            activity.startActivity(Intent.createChooser(insertIntent, "追加先のカレンダーを選ぶ"))
            CalendarExportResult.Opened
        } catch (_: ActivityNotFoundException) {
            CalendarExportResult.Unavailable
        } catch (error: Exception) {
            CalendarExportResult.Failed(error.message ?: "カレンダーを開けませんでした")
        }
    }
}
