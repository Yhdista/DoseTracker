package com.yhdista.dosetracker.reminder

import kotlinx.datetime.DayOfWeek

object WeekDays {
    private val ORDER = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    const val ALL_DAYS: Int = 0b1111111

    fun toBitmask(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl ORDER.indexOf(day)) }

    fun fromBitmask(mask: Int): Set<DayOfWeek> =
        ORDER.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()

    fun contains(mask: Int, day: DayOfWeek): Boolean =
        mask and (1 shl ORDER.indexOf(day)) != 0
}
