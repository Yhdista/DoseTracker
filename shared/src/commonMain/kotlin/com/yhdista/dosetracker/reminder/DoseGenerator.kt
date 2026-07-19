package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.daysUntil
import kotlin.time.Duration.Companion.minutes

class DoseGenerator(
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) {
    suspend fun runForToday() {
        runForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    suspend fun runForDate(date: LocalDate) {
        val schedules = (repository.getEnabledSchedules() as? Data.Success)?.data ?: return
        val periodTimes = repository.getPeriodTimesOnce()
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()

        for (schedule in schedules) {
            if (!matchesDate(schedule, date)) continue

            val minutes = if (schedule.timeType == "PERIOD") {
                periodTimes[schedule.dayPeriod] ?: schedule.minutesOfDay
            } else {
                schedule.minutesOfDay
            }

            val hour = minutes / 60
            val minute = minutes % 60
            val scheduledInstant = date.atTime(hour, minute).toInstant(zone)

            // Look up if there's any dose for this schedule on this date
            val existingDose = repository.getDoseForScheduleOnDate(schedule.id, date)

            if (existingDose == null) {
                val newDose = createDose(schedule.medicationId, schedule.id, scheduledInstant)
                if (newDose != null && newDose.status == DoseStatus.PENDING && scheduledInstant > now) {
                    scheduler.scheduleReminder(newDose.id, scheduledInstant)
                    scheduler.scheduleMissedTimeout(
                        newDose.id,
                        scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                    )
                }
            } else {
                if (existingDose.status == DoseStatus.PENDING) {
                    if (existingDose.timestamp != scheduledInstant) {
                        // Cancel old alarms
                        scheduler.cancelReminder(existingDose.id)
                        scheduler.cancelMissedTimeout(existingDose.id)

                        // Update dose
                        val updatedDose = existingDose.copy(timestamp = scheduledInstant)
                        repository.updateDose(updatedDose)

                        // Schedule new alarms
                        if (scheduledInstant > now) {
                            scheduler.scheduleReminder(existingDose.id, scheduledInstant)
                            scheduler.scheduleMissedTimeout(
                                existingDose.id,
                                scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                            )
                        }
                    } else if (scheduledInstant > now) {
                        // Just make sure it is scheduled (for boot or re-arm)
                        scheduler.scheduleReminder(existingDose.id, scheduledInstant)
                        scheduler.scheduleMissedTimeout(
                            existingDose.id,
                            scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                        )
                    }
                }
            }
        }
    }

    private fun matchesDate(schedule: ReminderSchedule, date: LocalDate): Boolean {
        return when (schedule.scheduleType) {
            "WEEKDAYS" -> WeekDays.contains(schedule.daysOfWeek, date.dayOfWeek)
            "INTERVAL" -> {
                val start = schedule.startDate ?: return false
                val days = start.daysUntil(date)
                days >= 0 && days % schedule.intervalDays == 0
            }
            else -> false
        }
    }

    private suspend fun createDose(medicationId: Long, scheduleId: Long, at: Instant): Dose? {
        val medication = repository.getMedicationOnce(medicationId) ?: return null
        val dose = Dose(
            medicationId = medicationId,
            scheduleId = scheduleId,
            timestamp = at,
            amount = medication.dosage,
            unit = medication.unit,
            status = DoseStatus.PENDING
        )
        val id = (repository.insertDose(dose) as? Data.Success)?.data ?: return null
        return dose.copy(id = id)
    }
}
