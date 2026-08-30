package jp.oboegaki.core.domain

import jp.oboegaki.core.model.RecurrenceRule
import jp.oboegaki.core.model.RecurrenceUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CalendarRecurrencePolicyTest {
    private val zone = TimeZone.of("Asia/Tokyo")

    @Test
    fun monthlyThirtyIsTypedAsUnsupported() {
        val start = LocalDateTime(2026, 4, 30, 18, 0).toInstant(zone).toEpochMilliseconds()
        val result = CalendarRecurrencePolicy.fromRule(
            RecurrenceRule(RecurrenceUnit.MONTH, anchorDayOfMonth = 30),
            start,
            zone,
        )

        assertIs<CalendarRecurrenceDecision.Unsupported>(result)
        assertEquals(CalendarRecurrenceUnsupportedReason.MONTH_DAY_29_OR_30, result.reason)
    }

    @Test
    fun monthlyThirtyOneUsesInclusiveLastDayRRule() {
        val start = LocalDateTime(2026, 1, 31, 18, 0).toInstant(zone).toEpochMilliseconds()
        val end = LocalDateTime(2026, 4, 1, 9, 0).toInstant(zone).toEpochMilliseconds()
        val result = CalendarRecurrencePolicy.fromRule(
            RecurrenceRule(RecurrenceUnit.MONTH, endAtEpochMillis = end),
            start,
            zone,
        )
        val supported = assertIs<CalendarRecurrenceDecision.Supported>(result).recurrence

        assertEquals(
            "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=-1;UNTIL=20260401T090000Z",
            CalendarRecurrencePolicy.toRRule(supported, start, zone),
        )
    }

    @Test
    fun invalidAnchorMonthDayIsTypedAsUnsupported() {
        val start = LocalDateTime(2026, 1, 31, 18, 0).toInstant(zone).toEpochMilliseconds()
        val result = CalendarRecurrencePolicy.fromRule(
            RecurrenceRule(RecurrenceUnit.MONTH, anchorMonth = 4, anchorDayOfMonth = 31),
            start,
            zone,
        )

        assertIs<CalendarRecurrenceDecision.Unsupported>(result)
        assertEquals(CalendarRecurrenceUnsupportedReason.INVALID_ANCHOR_DATE, result.reason)
        assertEquals("カレンダーに存在しない日付の定期設定は追加できません。", result.message)
    }

    @Test
    fun untilUsesUtcForUtcEvents() {
        val utc = TimeZone.UTC
        val start = LocalDateTime(2026, 1, 1, 12, 30).toInstant(utc).toEpochMilliseconds()
        val end = LocalDateTime(2026, 3, 1, 9, 0).toInstant(utc).toEpochMilliseconds()
        val supported = assertIs<CalendarRecurrenceDecision.Supported>(
            CalendarRecurrencePolicy.fromRule(
                RecurrenceRule(RecurrenceUnit.DAY, endAtEpochMillis = end),
                start,
                utc,
            ),
        ).recurrence

        assertEquals(
            "FREQ=DAILY;INTERVAL=1;UNTIL=20260301T123000Z",
            CalendarRecurrencePolicy.toRRule(supported, start, utc),
        )
    }

    @Test
    fun untilUsesOffsetAtTheInclusiveEndDateAcrossDst() {
        val dstZone = TimeZone.of("America/New_York")
        val start = LocalDateTime(2026, 3, 7, 9, 0).toInstant(dstZone).toEpochMilliseconds()
        val end = LocalDateTime(2026, 3, 10, 1, 0).toInstant(dstZone).toEpochMilliseconds()
        val supported = assertIs<CalendarRecurrenceDecision.Supported>(
            CalendarRecurrencePolicy.fromRule(
                RecurrenceRule(RecurrenceUnit.DAY, endAtEpochMillis = end),
                start,
                dstZone,
            ),
        ).recurrence

        assertEquals(
            "FREQ=DAILY;INTERVAL=1;UNTIL=20260310T130000Z",
            CalendarRecurrencePolicy.toRRule(supported, start, dstZone),
        )
    }

    @Test
    fun yearlyLeapDayIsNotSilentlyDowngraded() {
        val start = LocalDateTime(2028, 2, 29, 8, 0).toInstant(zone).toEpochMilliseconds()
        val result = CalendarRecurrencePolicy.fromRule(
            RecurrenceRule(RecurrenceUnit.YEAR, anchorMonth = 2, anchorDayOfMonth = 29),
            start,
            zone,
        )

        assertIs<CalendarRecurrenceDecision.Unsupported>(result)
        assertEquals(CalendarRecurrenceUnsupportedReason.LEAP_DAY, result.reason)
    }
}
