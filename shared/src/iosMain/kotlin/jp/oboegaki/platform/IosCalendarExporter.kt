@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package jp.oboegaki.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import jp.oboegaki.core.model.CalendarRecurrenceFrequency
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpan
import platform.Foundation.NSError
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.UIKit.UIDevice
import kotlin.coroutines.resume

class IosCalendarExporter : CalendarExporter {
    private val eventStore = EKEventStore()

    override suspend fun export(event: CalendarEventDraft): CalendarExportResult =
        suspendCancellableCoroutine { continuation ->
            val completion: (Boolean, NSError?) -> Unit = completion@{ granted, error ->
                if (!continuation.isActive) return@completion
                when {
                    error != null -> continuation.resume(
                        CalendarExportResult.Failed(error.localizedDescription),
                    )
                    !granted -> continuation.resume(CalendarExportResult.PermissionDenied)
                    else -> continuation.resume(save(event))
                }
            }

            val majorVersion = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 16
            if (majorVersion >= 17) {
                eventStore.requestWriteOnlyAccessToEventsWithCompletion(completion)
            } else {
                eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, completion)
            }
        }

    private fun save(draft: CalendarEventDraft): CalendarExportResult {
        val calendar = eventStore.defaultCalendarForNewEvents
            ?: return CalendarExportResult.Unavailable
        return runCatching {
            val event = EKEvent.eventWithEventStore(eventStore)
            event.title = draft.title
            event.notes = draft.notes
            event.startDate = NSDate(
                timeIntervalSinceReferenceDate = draft.startAtEpochMillis / 1_000.0 - APPLE_REFERENCE_DATE_OFFSET_SECONDS,
            )
            event.endDate = NSDate(
                timeIntervalSinceReferenceDate = draft.endAtEpochMillis / 1_000.0 - APPLE_REFERENCE_DATE_OFFSET_SECONDS,
            )
            event.calendar = calendar
            draft.recurrence?.let { recurrence ->
                val frequency = when (recurrence.frequency) {
                    CalendarRecurrenceFrequency.DAILY -> EKRecurrenceFrequency.EKRecurrenceFrequencyDaily
                    CalendarRecurrenceFrequency.WEEKLY -> EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly
                    CalendarRecurrenceFrequency.MONTHLY -> EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly
                    CalendarRecurrenceFrequency.YEARLY -> EKRecurrenceFrequency.EKRecurrenceFrequencyYearly
                }
                val end = recurrence.endAtEpochMillis?.let {
                    EKRecurrenceEnd.recurrenceEndWithEndDate(
                        NSDate(
                            timeIntervalSinceReferenceDate = it / 1_000.0 - APPLE_REFERENCE_DATE_OFFSET_SECONDS,
                        ),
                    )
                }
                val recurrenceRule = when (recurrence.frequency) {
                    CalendarRecurrenceFrequency.MONTHLY -> EKRecurrenceRule(
                        recurrenceWithFrequency = frequency,
                        interval = recurrence.interval.toLong(),
                        daysOfTheWeek = null,
                        daysOfTheMonth = listOf(
                            NSNumber(int = if (recurrence.lastDayOfMonth) -1 else recurrence.dayOfMonth ?: 1),
                        ),
                        monthsOfTheYear = null,
                        weeksOfTheYear = null,
                        daysOfTheYear = null,
                        setPositions = null,
                        end = end,
                    )
                    CalendarRecurrenceFrequency.YEARLY -> EKRecurrenceRule(
                        recurrenceWithFrequency = frequency,
                        interval = recurrence.interval.toLong(),
                        daysOfTheWeek = null,
                        daysOfTheMonth = listOf(NSNumber(int = recurrence.dayOfMonth ?: 1)),
                        monthsOfTheYear = listOf(NSNumber(int = recurrence.monthOfYear ?: 1)),
                        weeksOfTheYear = null,
                        daysOfTheYear = null,
                        setPositions = null,
                        end = end,
                    )
                    CalendarRecurrenceFrequency.DAILY,
                    CalendarRecurrenceFrequency.WEEKLY,
                    -> EKRecurrenceRule(
                        recurrenceWithFrequency = frequency,
                        interval = recurrence.interval.toLong(),
                        end = end,
                    )
                }
                event.addRecurrenceRule(recurrenceRule)
            }
            val saved = eventStore.saveEvent(event, EKSpan.EKSpanThisEvent, commit = true, error = null)
            if (saved) CalendarExportResult.Added(calendar.title)
            else CalendarExportResult.Failed("カレンダーに追加できませんでした")
        }.getOrElse {
            CalendarExportResult.Failed(it.message ?: "カレンダーに追加できませんでした")
        }
    }

    private companion object {
        const val APPLE_REFERENCE_DATE_OFFSET_SECONDS = 978_307_200.0
    }
}
