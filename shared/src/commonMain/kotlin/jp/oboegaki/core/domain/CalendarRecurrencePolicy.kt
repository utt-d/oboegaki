package jp.oboegaki.core.domain

import jp.oboegaki.core.model.CalendarRecurrence
import jp.oboegaki.core.model.CalendarRecurrenceFrequency
import jp.oboegaki.core.model.RecurrenceRule
import jp.oboegaki.core.model.RecurrenceUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class CalendarRecurrenceUnsupportedReason {
    MONTH_DAY_29_OR_30,
    LEAP_DAY,
    INVALID_ANCHOR_DATE,
}

sealed interface CalendarRecurrenceDecision {
    data class Supported(val recurrence: CalendarRecurrence) : CalendarRecurrenceDecision
    data class Unsupported(
        val reason: CalendarRecurrenceUnsupportedReason,
        val message: String,
    ) : CalendarRecurrenceDecision
}

/**
 * The single policy for deciding which app recurrence rules may be exported.
 * Exporters only serialize the result; they do not reinterpret the rule.
 */
object CalendarRecurrencePolicy {
    fun fromRule(
        rule: RecurrenceRule,
        startAtEpochMillis: Long,
        timeZone: TimeZone,
    ): CalendarRecurrenceDecision {
        val start = Instant.fromEpochMilliseconds(startAtEpochMillis).toLocalDateTime(timeZone)
        val day = rule.anchorDayOfMonth ?: start.dayOfMonth
        val month = rule.anchorMonth ?: start.monthNumber
        if (month !in 1..12 || day !in 1..31 || day > maxDaysInMonth(month)) {
            return CalendarRecurrenceDecision.Unsupported(
                CalendarRecurrenceUnsupportedReason.INVALID_ANCHOR_DATE,
                "カレンダーに存在しない日付の定期設定は追加できません。",
            )
        }
        return when (rule.unit) {
            RecurrenceUnit.DAY -> supported(CalendarRecurrenceFrequency.DAILY, rule, start, timeZone)
            RecurrenceUnit.WEEK -> supported(CalendarRecurrenceFrequency.WEEKLY, rule, start, timeZone)
            RecurrenceUnit.MONTH -> when (day) {
                in 1..28 -> supported(
                    CalendarRecurrenceFrequency.MONTHLY,
                    rule,
                    start,
                    timeZone,
                    dayOfMonth = day,
                )
                31 -> supported(
                    CalendarRecurrenceFrequency.MONTHLY,
                    rule,
                    start,
                    timeZone,
                    lastDayOfMonth = true,
                )
                else -> CalendarRecurrenceDecision.Unsupported(
                    CalendarRecurrenceUnsupportedReason.MONTH_DAY_29_OR_30,
                    "月の29日・30日の定期設定は端末のカレンダーと日末の扱いが異なるため追加できません。",
                )
            }
            RecurrenceUnit.YEAR -> if (month == 2 && day == 29) {
                CalendarRecurrenceDecision.Unsupported(
                    CalendarRecurrenceUnsupportedReason.LEAP_DAY,
                    "2月29日の年ごとの定期設定は端末のカレンダーと扱いが異なるため追加できません。",
                )
            } else {
                supported(
                    CalendarRecurrenceFrequency.YEARLY,
                    rule,
                    start,
                    timeZone,
                    monthOfYear = month,
                    dayOfMonth = day,
                )
            }
        }
    }

    /** Generates the RFC 5545 rule used by Android CalendarContract. */
    fun toRRule(
        recurrence: CalendarRecurrence,
        startAtEpochMillis: Long,
        timeZone: TimeZone,
    ): String = buildString {
        append("FREQ=")
        append(recurrence.frequency.name)
        append(";INTERVAL=")
        append(recurrence.interval)
        if (recurrence.monthOfYear != null) {
            append(";BYMONTH=")
            append(recurrence.monthOfYear)
        }
        when {
            recurrence.lastDayOfMonth -> append(";BYMONTHDAY=-1")
            recurrence.dayOfMonth != null -> {
                append(";BYMONTHDAY=")
                append(recurrence.dayOfMonth)
            }
        }
        recurrence.endAtEpochMillis?.let { endAt ->
            val start = Instant.fromEpochMilliseconds(startAtEpochMillis).toLocalDateTime(timeZone)
            val endDate = Instant.fromEpochMilliseconds(endAt).toLocalDateTime(timeZone)
            val until = LocalDateTime(
                endDate.year,
                endDate.monthNumber,
                endDate.dayOfMonth,
                start.hour,
                start.minute,
                start.second,
                start.nanosecond,
            ).toInstant(timeZone)
            append(";UNTIL=")
            append(until.toUtcCompact())
        }
    }

    fun toRRule(
        recurrence: CalendarRecurrence,
        startAtEpochMillis: Long,
        timeZoneId: String,
    ): String = toRRule(recurrence, startAtEpochMillis, TimeZone.of(timeZoneId))

    private fun supported(
        frequency: CalendarRecurrenceFrequency,
        rule: RecurrenceRule,
        start: LocalDateTime,
        timeZone: TimeZone,
        monthOfYear: Int? = null,
        dayOfMonth: Int? = null,
        lastDayOfMonth: Boolean = false,
    ): CalendarRecurrenceDecision {
        val end = rule.endAtEpochMillis?.let { endAt ->
            // The app stores an inclusive local date. Use the event's local
            // clock time so the selected final date is never lost because the
            // date-only picker happened to use a different default time.
            val endDate = Instant.fromEpochMilliseconds(endAt).toLocalDateTime(timeZone)
            LocalDateTime(
                endDate.year,
                endDate.monthNumber,
                endDate.dayOfMonth,
                start.hour,
                start.minute,
                start.second,
                start.nanosecond,
            ).toInstant(timeZone).toEpochMilliseconds()
        }
        return CalendarRecurrenceDecision.Supported(
            CalendarRecurrence(
                frequency = frequency,
                interval = rule.interval,
                endAtEpochMillis = end,
                monthOfYear = monthOfYear,
                dayOfMonth = dayOfMonth,
                lastDayOfMonth = lastDayOfMonth,
            ),
        )
    }

    private fun LocalDateTime.compact(): String = buildString {
        append(year.toString().padStart(4, '0'))
        append(monthNumber.toString().padStart(2, '0'))
        append(dayOfMonth.toString().padStart(2, '0'))
        append('T')
        append(hour.toString().padStart(2, '0'))
        append(minute.toString().padStart(2, '0'))
        append(second.toString().padStart(2, '0'))
    }

    private fun Instant.toUtcCompact(): String = toLocalDateTime(TimeZone.UTC)
        .compact() + "Z"

    private fun maxDaysInMonth(month: Int): Int = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
