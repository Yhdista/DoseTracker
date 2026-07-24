package com.yhdista.dosetracker.core

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class IsoWeekTest {

    @Test
    fun `week 1 can start in the previous calendar year`() {
        // 2026-01-01 is a Thursday, so ISO week 1 of 2026 starts on Monday 2025-12-29.
        assertEquals(LocalDate(2025, 12, 29), isoWeekStart(2026, 1))
    }

    @Test
    fun `isoWeekOf returns the week-year of the week's Thursday`() {
        // 2025-12-31 (Wednesday) belongs to week 1 of the 2026 week-year.
        assertEquals(IsoWeek(2026, 1), isoWeekOf(LocalDate(2025, 12, 31)))
        assertEquals(IsoWeek(2026, 30), isoWeekOf(LocalDate(2026, 7, 24)))
    }

    @Test
    fun `isoWeekStart and isoWeekOf round-trip`() {
        val date = LocalDate(2026, 7, 24) // Friday
        val week = isoWeekOf(date)
        assertEquals(LocalDate(2026, 7, 20), isoWeekStart(week.year, week.week)) // that Monday
        assertEquals(week, isoWeekOf(isoWeekStart(week.year, week.week)))
    }

    @Test
    fun `long years have 53 weeks`() {
        assertEquals(53, isoWeeksInYear(2026)) // starts on a Thursday
        assertEquals(53, isoWeeksInYear(2020)) // leap year starting on a Wednesday
        assertEquals(52, isoWeeksInYear(2021))
        assertEquals(52, isoWeeksInYear(2025))
    }
}
