package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.domain.model.ReminderSchedule

import kotlinx.datetime.LocalDate

fun ReminderScheduleEntity.toDomain(): ReminderSchedule = ReminderSchedule(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled,
    scheduleType = scheduleType,
    intervalDays = intervalDays,
    startDate = startDate?.let { LocalDate.parse(it) },
    timeType = timeType,
    dayPeriod = dayPeriod
)

fun ReminderSchedule.toEntity(): ReminderScheduleEntity = ReminderScheduleEntity(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled,
    scheduleType = scheduleType,
    intervalDays = intervalDays,
    startDate = startDate?.toString(),
    timeType = timeType,
    dayPeriod = dayPeriod
)
