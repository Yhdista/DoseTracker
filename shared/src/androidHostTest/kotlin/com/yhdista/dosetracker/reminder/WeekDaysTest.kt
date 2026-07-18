package com.yhdista.dosetracker.reminder

import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekDaysTest {

    @Test
    fun `toBitmask sets one bit per day, Monday as bit 0`() {
        assertEquals(0b0000001, WeekDays.toBitmask(setOf(DayOfWeek.MONDAY)))
        assertEquals(0b1000000, WeekDays.toBitmask(setOf(DayOfWeek.SUNDAY)))
        assertEquals(0b0000101, WeekDays.toBitmask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun `fromBitmask is the inverse of toBitmask`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        assertEquals(days, WeekDays.fromBitmask(WeekDays.toBitmask(days)))
    }

    @Test
    fun `contains reports whether a day's bit is set`() {
        val mask = WeekDays.toBitmask(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(WeekDays.contains(mask, DayOfWeek.MONDAY))
        assertTrue(WeekDays.contains(mask, DayOfWeek.FRIDAY))
        assertFalse(WeekDays.contains(mask, DayOfWeek.SUNDAY))
    }

    @Test
    fun `ALL_DAYS contains every day`() {
        DayOfWeek.entries.forEach { day ->
            assertTrue(WeekDays.contains(WeekDays.ALL_DAYS, day))
        }
    }
}
