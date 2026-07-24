package com.yhdista.dosetracker.domain.usecase

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * Single owner of the "schedules changed → today's doses must regenerate" invariant.
 * ViewModels used to repeat the doseGenerator.runForToday() afterthought at six call
 * sites — and the delete path cancelled pending alarms in one screen but not the other.
 */
class ManageScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
    private val doseRepository: DoseRepository,
    private val scheduler: DoseReminderScheduler,
    private val doseGenerator: DoseGenerator,
) {

    suspend fun addSchedule(schedule: ReminderSchedule): Data<Long> {
        val result = scheduleRepository.insertSchedule(schedule)
        if (result is Data.Success) doseGenerator.runForToday()
        return result
    }

    suspend fun updateSchedule(schedule: ReminderSchedule): Data<Unit> {
        val result = scheduleRepository.updateSchedule(schedule)
        if (result is Data.Success) doseGenerator.runForToday()
        return result
    }

    suspend fun deleteSchedule(schedule: ReminderSchedule): Data<Unit> {
        cancelPendingAlarms(schedule)
        val result = scheduleRepository.deleteSchedule(schedule)
        if (result is Data.Success) doseGenerator.runForToday()
        return result
    }

    suspend fun updatePeriodTime(period: DayPeriod, minutesOfDay: Int): Data<Unit> {
        val result = scheduleRepository.updatePeriodTime(period, minutesOfDay)
        // PERIOD-timed doses for today move with the new time.
        if (result is Data.Success) doseGenerator.runForToday()
        return result
    }

    /** Dose rows survive schedule deletion by design; their alarms must not. */
    private suspend fun cancelPendingAlarms(schedule: ReminderSchedule) {
        val doses = doseRepository.getDosesForMedication(schedule.medicationId).first { it !is Data.Loading }
        if (doses is Data.Success) {
            doses.data
                .filter { it.scheduleId == schedule.id && it.status == DoseStatus.PENDING }
                .forEach {
                    scheduler.cancelReminder(it.id)
                    scheduler.cancelMissedTimeout(it.id)
                }
        }
    }
}
