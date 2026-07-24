package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.model.ScheduleType
import com.yhdista.dosetracker.domain.model.TimeType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The "does this schedule fire on this date, and at what time" rules.
 *
 * Two callers need the exact same answer: [DoseGenerator], which persists doses for a date, and
 * PlanAgendaUseCase, which projects the days ahead that have not been generated yet. Keeping the
 * rules here means the calendar can never disagree with what the generator will actually create.
 */

/** Week index (0-based) of [date] within [cycle], or null when [date] is before the cycle starts. */
fun cycleWeekIndexFor(cycle: Cycle, date: LocalDate): Int? {
    if (cycle.type == CycleType.STANDARD) return 0
    val days = cycle.startDate.daysUntil(date)
    if (days < 0) return null
    return days / 7
}

/** True when [schedule] fires on [date], given the cycle week that is current on that date. */
fun scheduleMatchesDate(
    schedule: ReminderSchedule,
    date: LocalDate,
    activeCycleWeekId: Long?,
): Boolean {
    if (schedule.cycleWeekId != null && schedule.cycleWeekId != activeCycleWeekId) return false
    return when (schedule.scheduleType) {
        ScheduleType.WEEKDAYS -> WeekDays.contains(schedule.daysOfWeek, date.dayOfWeek)
        ScheduleType.INTERVAL -> {
            val start = schedule.startDate ?: return false
            val days = start.daysUntil(date)
            days >= 0 && days % schedule.intervalDays == 0
        }
    }
}

/** Minutes-of-day the schedule fires at, resolving PERIOD schedules through the period times. */
fun effectiveMinutesOfDay(schedule: ReminderSchedule, periodTimes: Map<DayPeriod, Int>): Int =
    if (schedule.timeType == TimeType.PERIOD) {
        schedule.dayPeriod?.let { periodTimes[it] } ?: schedule.minutesOfDay
    } else {
        schedule.minutesOfDay
    }
