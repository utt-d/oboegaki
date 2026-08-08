package jp.oboegaki.platform

import jp.oboegaki.core.model.CalendarRecurrence

data class CalendarEventDraft(
    val itemId: String,
    val title: String,
    val notes: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long,
    val timeZoneId: String = "UTC",
    val recurrence: CalendarRecurrence? = null,
)

sealed interface CalendarExportResult {
    data object Opened : CalendarExportResult
    data class Added(val calendarName: String?) : CalendarExportResult
    data object PermissionDenied : CalendarExportResult
    data object Unavailable : CalendarExportResult
    data class Failed(val reason: String) : CalendarExportResult
}

interface CalendarExporter {
    suspend fun export(event: CalendarEventDraft): CalendarExportResult
}

object NoOpCalendarExporter : CalendarExporter {
    override suspend fun export(event: CalendarEventDraft) = CalendarExportResult.Unavailable
}
