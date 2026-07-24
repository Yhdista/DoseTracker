package com.yhdista.dosetracker.domain.model

/**
 * A dose the schedules say *will* happen on a future day, projected without being persisted.
 *
 * Doses are only written to the database for the day they are generated for, so the days ahead in
 * the agenda have no Dose rows at all. A planned dose carries just enough to show that day's plan
 * (name, amount, time); it has no id and no status because nothing has been created yet.
 */
data class PlannedDose(
    val scheduleId: Long,
    val medicationId: Long,
    val medicationName: String,
    val amount: Double,
    val unit: String,
    val minutesOfDay: Int,
    val cycleId: Long?,
)
