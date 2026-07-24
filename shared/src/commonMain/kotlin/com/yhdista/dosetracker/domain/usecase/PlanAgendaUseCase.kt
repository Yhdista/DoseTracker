package com.yhdista.dosetracker.domain.usecase

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.PlannedDose
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.reminder.cycleWeekIndexFor
import com.yhdista.dosetracker.reminder.effectiveMinutesOfDay
import com.yhdista.dosetracker.reminder.scheduleMatchesDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Projects the doses the enabled schedules will produce over a date range.
 *
 * Doses are generated one day at a time (midnight worker / app start), so every day after today is
 * empty in the database — which the agenda used to render as "no doses" even for a fully planned
 * cycle. This use case answers what those days will contain, using the same matching rules as
 * [com.yhdista.dosetracker.reminder.DoseGenerator] so the preview cannot drift from reality.
 */
class PlanAgendaUseCase(
    private val scheduleRepository: ScheduleRepository,
    private val medicationRepository: MedicationRepository,
    private val cycleRepository: CycleRepository,
) {

    /** @return planned doses per date (time-sorted); dates with nothing planned are absent. */
    suspend operator fun invoke(from: LocalDate, days: Int): Map<LocalDate, List<PlannedDose>> {
        val schedules = (scheduleRepository.getEnabledSchedules() as? Data.Success)?.data.orEmpty()
        if (schedules.isEmpty() || days <= 0) return emptyMap()

        val periodTimes = scheduleRepository.getPeriodTimesOnce()
        val activeCycle = cycleRepository.getActiveCycleOnce()
        val medications = schedules.map { it.medicationId }.distinct()
            .mapNotNull { medicationRepository.getMedicationOnce(it) }
            .associateBy { it.id }

        // The window spans at most a couple of cycle weeks, so resolve each week id once.
        val weekIdByIndex = mutableMapOf<Int, Long?>()

        val result = mutableMapOf<LocalDate, List<PlannedDose>>()
        for (offset in 0 until days) {
            val date = from.plus(offset, DateTimeUnit.DAY)
            val cycleWeekId = activeCycle?.let { cycle ->
                cycleWeekIndexFor(cycle, date)?.let { index ->
                    weekIdByIndex.getOrPut(index) { cycleRepository.getCycleWeek(cycle.id, index)?.id }
                }
            }

            val planned = schedules
                .filter { scheduleMatchesDate(it, date, cycleWeekId) }
                .mapNotNull { schedule ->
                    val medication = medications[schedule.medicationId] ?: return@mapNotNull null
                    PlannedDose(
                        scheduleId = schedule.id,
                        medicationId = medication.id,
                        medicationName = medication.name,
                        amount = medication.dosage,
                        unit = medication.unit.symbol,
                        minutesOfDay = effectiveMinutesOfDay(schedule, periodTimes),
                        cycleId = if (schedule.cycleWeekId != null) activeCycle?.id else null,
                    )
                }
                .sortedBy { it.minutesOfDay }

            if (planned.isNotEmpty()) result[date] = planned
        }
        return result
    }
}
