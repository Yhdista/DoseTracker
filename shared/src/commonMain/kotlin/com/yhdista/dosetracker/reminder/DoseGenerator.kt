package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant

class DoseGenerator(
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) {
    suspend fun runForToday() {
        runForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    suspend fun runForDate(date: LocalDate) {
        val schedules = (repository.getEnabledSchedules() as? Data.Success)?.data ?: return
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()

        for (schedule in schedules) {
            if (!WeekDays.contains(schedule.daysOfWeek, date.dayOfWeek)) continue

            val hour = schedule.minutesOfDay / 60
            val minute = schedule.minutesOfDay % 60
            val scheduledInstant = date.atTime(hour, minute).toInstant(zone)

            val dose = repository.getDoseForSchedule(schedule.id, scheduledInstant)
                ?: createDose(schedule.medicationId, schedule.id, scheduledInstant)
                ?: continue

            if (dose.status == DoseStatus.PENDING && scheduledInstant > now) {
                scheduler.scheduleReminder(dose.id, scheduledInstant)
            }
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
