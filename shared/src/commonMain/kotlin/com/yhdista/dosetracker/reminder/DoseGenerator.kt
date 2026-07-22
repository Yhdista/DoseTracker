package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
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
    private val scheduler: DoseReminderScheduler,
    private val cycleLifecycleManager: CycleLifecycleManager
) {
    suspend fun runForToday() {
        runForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    suspend fun runForDate(date: LocalDate) {
        com.yhdista.dosetracker.core.AppLogger.i("DoseGenerator", "runForDate(date=$date) starting...")
        cycleLifecycleManager.advance(date)

        val schedulesData = repository.getEnabledSchedules()
        if (schedulesData !is Data.Success) {
            com.yhdista.dosetracker.core.AppLogger.e("DoseGenerator", "runForDate failed to get enabled schedules: $schedulesData")
            return
        }
        val schedules = schedulesData.data
        val periodTimes = repository.getPeriodTimesOnce()
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val activeCycle = repository.getActiveCycleOnce()
        val activeCycleWeekId = resolveActiveCycleWeekId(activeCycle, date)

        com.yhdista.dosetracker.core.AppLogger.d("DoseGenerator", "Running for ${schedules.size} enabled schedules. Active cycle: ${activeCycle?.id}, cycleWeekId: $activeCycleWeekId")

        for (schedule in schedules) {
            val matches = matchesDate(schedule, date, activeCycleWeekId)
            if (!matches) continue

            val minutes = if (schedule.timeType == "PERIOD") {
                periodTimes[schedule.dayPeriod] ?: schedule.minutesOfDay
            } else {
                schedule.minutesOfDay
            }

            val hour = minutes / 60
            val minute = minutes % 60
            val scheduledInstant = date.atTime(hour, minute).toInstant(zone)
            val cycleId = if (schedule.cycleWeekId != null) activeCycle?.id else null

            // Look up if there's any dose for this schedule on this date
            val existingDose = repository.getDoseForScheduleOnDate(schedule.id, date)

            if (existingDose == null) {
                com.yhdista.dosetracker.core.AppLogger.d("DoseGenerator", "No existing dose for scheduleId=${schedule.id} on date=$date. Creating new dose...")
                val newDose = createDose(schedule.medicationId, schedule.id, scheduledInstant, cycleId)
                if (newDose != null) {
                    com.yhdista.dosetracker.core.AppLogger.i("DoseGenerator", "Created new dose: id=${newDose.id}, medicationId=${newDose.medicationId}, time=$scheduledInstant")
                    if (newDose.status == DoseStatus.PENDING && scheduledInstant > now) {
                        com.yhdista.dosetracker.core.AppLogger.d("DoseGenerator", "Scheduling reminder and missed timeout alarms for new dose id=${newDose.id}")
                        scheduler.scheduleReminder(newDose.id, scheduledInstant)
                        scheduler.scheduleMissedTimeout(
                            newDose.id,
                            scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                        )
                    }
                }
            } else {
                com.yhdista.dosetracker.core.AppLogger.d("DoseGenerator", "Found existing dose for scheduleId=${schedule.id} on date=$date: id=${existingDose.id}, status=${existingDose.status}")
                if (existingDose.status == DoseStatus.PENDING) {
                    if (existingDose.timestamp != scheduledInstant) {
                        com.yhdista.dosetracker.core.AppLogger.i("DoseGenerator", "Rescheduling pending dose id=${existingDose.id}: time changed from ${existingDose.timestamp} to $scheduledInstant")
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
                        com.yhdista.dosetracker.core.AppLogger.d("DoseGenerator", "Verifying scheduling for pending dose id=${existingDose.id} at time=$scheduledInstant")
                        scheduler.scheduleReminder(existingDose.id, scheduledInstant)
                        scheduler.scheduleMissedTimeout(
                            existingDose.id,
                            scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                        )
                    }
                }
            }
        }
        com.yhdista.dosetracker.core.AppLogger.i("DoseGenerator", "runForDate(date=$date) finished")
    }

    private suspend fun resolveActiveCycleWeekId(activeCycle: Cycle?, date: LocalDate): Long? {
        if (activeCycle == null) return null
        val weekIndex = if (activeCycle.type == CycleType.STANDARD) {
            0
        } else {
            activeCycle.startDate.daysUntil(date) / 7
        }
        return repository.getCycleWeek(activeCycle.id, weekIndex)?.id
    }

    private fun matchesDate(schedule: ReminderSchedule, date: LocalDate, activeCycleWeekId: Long?): Boolean {
        if (schedule.cycleWeekId != null && schedule.cycleWeekId != activeCycleWeekId) return false
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

    private suspend fun createDose(medicationId: Long, scheduleId: Long, at: Instant, cycleId: Long?): Dose? {
        val medication = repository.getMedicationOnce(medicationId) ?: return null
        val dose = Dose(
            medicationId = medicationId,
            scheduleId = scheduleId,
            cycleId = cycleId,
            timestamp = at,
            amount = medication.dosage,
            unit = medication.unit.symbol,
            status = DoseStatus.PENDING
        )
        val id = (repository.insertDose(dose) as? Data.Success)?.data ?: return null
        return dose.copy(id = id)
    }
}
