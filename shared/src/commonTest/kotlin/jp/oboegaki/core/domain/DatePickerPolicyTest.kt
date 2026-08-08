package jp.oboegaki.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DatePickerPolicyTest {
    @Test
    fun monthNavigationWrapsAcrossYears() {
        assertEquals(CalendarMonth(2027, 1), DatePickerPolicy.shiftMonth(2026, 12, 1))
        assertEquals(CalendarMonth(2026, 12), DatePickerPolicy.shiftMonth(2027, 1, -1))
    }

    @Test
    fun dayIsClampedToTheLastDayOfTheTargetMonth() {
        assertEquals(28, DatePickerPolicy.clampDay(2027, 2, 31))
        assertEquals(29, DatePickerPolicy.clampDay(2028, 2, 31))
        assertEquals(30, DatePickerPolicy.clampDay(2027, 4, 31))
    }
}
