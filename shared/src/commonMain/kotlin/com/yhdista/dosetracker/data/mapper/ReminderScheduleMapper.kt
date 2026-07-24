package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.model.ScheduleType
import com.yhdista.dosetracker.domain.model.TimeType
import kotlinx.datetime.LocalDate

// Persistence constants are spelled out here on purpose: renaming a domain enum
// constant must never silently change or corrupt what is stored in the database.

internal fun ScheduleType.toDbValue(): String = when (this) {
    ScheduleType.WEEKDAYS -> "WEEKDAYS"
    ScheduleType.INTERVAL -> "INTERVAL"
}

internal fun scheduleTypeFromDb(value: String): ScheduleType = when (value) {
    "INTERVAL" -> ScheduleType.INTERVAL
    else -> ScheduleType.WEEKDAYS
}

internal fun TimeType.toDbValue(): String = when (this) {
    TimeType.EXACT -> "EXACT"
    TimeType.PERIOD -> "PERIOD"
}

internal fun timeTypeFromDb(value: String): TimeType = when (value) {
    "PERIOD" -> TimeType.PERIOD
    else -> TimeType.EXACT
}

internal fun DayPeriod.toDbValue(): String = when (this) {
    DayPeriod.MORNING -> "MORNING"
    DayPeriod.NOON -> "NOON"
    DayPeriod.EVENING -> "EVENING"
    DayPeriod.NIGHT -> "NIGHT"
}

internal fun dayPeriodFromDb(value: String): DayPeriod? = when (value) {
    "MORNING" -> DayPeriod.MORNING
    "NOON" -> DayPeriod.NOON
    "EVENING" -> DayPeriod.EVENING
    "NIGHT" -> DayPeriod.NIGHT
    else -> null
}

internal fun ReminderScheduleEntity.toDomain(): ReminderSchedule = ReminderSchedule(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled,
    scheduleType = scheduleTypeFromDb(scheduleType),
    intervalDays = intervalDays,
    startDate = startDate?.let { LocalDate.parse(it) },
    timeType = timeTypeFromDb(timeType),
    dayPeriod = dayPeriod?.let { dayPeriodFromDb(it) },
    cycleWeekId = cycleWeekId
)

internal fun ReminderSchedule.toEntity(): ReminderScheduleEntity = ReminderScheduleEntity(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled,
    scheduleType = scheduleType.toDbValue(),
    intervalDays = intervalDays,
    startDate = startDate?.toString(),
    timeType = timeType.toDbValue(),
    dayPeriod = dayPeriod?.toDbValue(),
    cycleWeekId = cycleWeekId
)
