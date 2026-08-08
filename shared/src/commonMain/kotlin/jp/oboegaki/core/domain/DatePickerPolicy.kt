package jp.oboegaki.core.domain

data class CalendarMonth(val year: Int, val month: Int)

object DatePickerPolicy {
    const val FIRST_YEAR = 1900
    const val LAST_YEAR = 2200

    fun shiftMonth(year: Int, month: Int, amount: Int): CalendarMonth {
        val shifted = year * 12 + month - 1 + amount
        return CalendarMonth(shifted.floorDiv(12), shifted.mod(12) + 1)
    }

    fun clampDay(year: Int, month: Int, day: Int): Int =
        day.coerceIn(1, daysInMonth(year, month))

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 400 == 0 || year % 4 == 0 && year % 100 != 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
