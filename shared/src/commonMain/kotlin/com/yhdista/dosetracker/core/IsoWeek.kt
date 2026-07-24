package com.yhdista.dosetracker.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * ISO-8601 week arithmetic (weeks start on Monday, week 1 is the week containing January 4th).
 *
 * kotlinx-datetime has no week-of-year support, and cycles are planned in whole weeks, so cycle
 * creation needs a stable "week number in year" ↔ "start date" mapping. Everything here is pure
 * so it can be unit-tested without a clock (see IsoWeekTest).
 */

/** 1 = Monday … 7 = Sunday. */
private val LocalDate.isoDayNumber: Int get() = dayOfWeek.ordinal + 1

/** The ISO week-year and week number [date] falls in. The week-year can differ from [LocalDate.year]. */
fun isoWeekOf(date: LocalDate): IsoWeek {
    // The ISO week-year of a week is the year of its Thursday, by definition.
    val thursday = date.plus(4 - date.isoDayNumber, DateTimeUnit.DAY)
    return IsoWeek(year = thursday.year, week = (thursday.dayOfYear - 1) / 7 + 1)
}

/** Monday of ISO week [week] in ISO week-year [year]. */
fun isoWeekStart(year: Int, week: Int): LocalDate {
    val jan4 = LocalDate(year, 1, 4)
    val week1Monday = jan4.plus(1 - jan4.isoDayNumber, DateTimeUnit.DAY)
    return week1Monday.plus((week - 1) * 7, DateTimeUnit.DAY)
}

/** 52 or 53 — the number of ISO weeks in week-year [year]. */
fun isoWeeksInYear(year: Int): Int {
    val jan1 = LocalDate(year, 1, 1)
    val isLeap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    // A year has 53 weeks when it starts on a Thursday, or on a Wednesday in a leap year.
    return if (jan1.isoDayNumber == 4 || (isLeap && jan1.isoDayNumber == 3)) 53 else 52
}

data class IsoWeek(val year: Int, val week: Int)
