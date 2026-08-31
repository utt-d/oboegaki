package jp.oboegaki.ui

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayDateTimeFormatTest {
    @Test
    fun dateAndTimePartsUseTheSelectedValues() {
        val value = LocalDateTime(2026, 8, 31, 9, 5)

        assertEquals("2026/08/31", formatDatePart(value))
        assertEquals("09:05", formatTimePart(value))
    }
}
