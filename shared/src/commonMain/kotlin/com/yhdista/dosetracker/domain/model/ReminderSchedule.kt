package com.yhdista.dosetracker.domain.model

data class ReminderSchedule(
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int, // 0..1439, local time of day
    val daysOfWeek: Int,   // bitmask, see WeekDays
    val enabled: Boolean = true
)
