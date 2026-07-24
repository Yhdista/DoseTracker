package com.yhdista.dosetracker.domain.model

import kotlinx.datetime.LocalDate

data class ReminderSchedule(
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int, // 0..1439, local time of day
    val daysOfWeek: Int,   // bitmask, see WeekDays
    val enabled: Boolean = true,
    val scheduleType: ScheduleType = ScheduleType.WEEKDAYS,
    val intervalDays: Int = 1,
    val startDate: LocalDate? = null,
    val timeType: TimeType = TimeType.EXACT,
    val dayPeriod: DayPeriod? = null,
    val cycleWeekId: Long? = null
)
