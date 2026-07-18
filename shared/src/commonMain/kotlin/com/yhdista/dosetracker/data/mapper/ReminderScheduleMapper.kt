package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.domain.model.ReminderSchedule

fun ReminderScheduleEntity.toDomain(): ReminderSchedule = ReminderSchedule(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled
)

fun ReminderSchedule.toEntity(): ReminderScheduleEntity = ReminderScheduleEntity(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled
)
