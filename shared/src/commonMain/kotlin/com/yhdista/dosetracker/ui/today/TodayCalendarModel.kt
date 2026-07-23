package com.yhdista.dosetracker.ui.today

import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Pure, Compose-free model + computations backing the Today "Kalendář" screen.
 *
 * Kept deliberately free of any Android/Compose types so the band and agenda derivation
 * can be unit-tested directly (see TodayCalendarModelTest), mirroring the project's
 * preference for fast, deterministic ViewModel/logic tests.
 */

/** How many days forward the agenda shows (today + the next 13 days). */
const val AGENDA_WINDOW_DAYS = 14

/** How many days on each side of today the timeline spans. */
const val TIMELINE_HALF_SPAN_DAYS = 180

enum class BandKind {
    /** The single ACTIVE cycle. */
    ACTIVE,

    /** A cycle whose span lies entirely in the past. */
    COMPLETED,

    /** A cycle that continues into (or starts in) the future — e.g. a chained post-cycle. */
    FUTURE,

    /** The synthetic "nothing planned yet" region drawn after the last known cycle. */
    UNPLANNED,
}

/**
 * A single band on the cycle timeline.
 *
 * @param end exclusive end date, or null for an unbounded band (active STANDARD cycle) that
 *   should extend to the window edge.
 * @param fadeEnd true when [end] is imprecise or unbounded, so the band should dissolve at its
 *   right edge rather than stopping hard. This is how we stay honest about STANDARD cycles
 *   (unbounded when active; unknown end date when manually completed — no endDate is stored).
 */
data class CycleBand(
    val cycleId: Long,
    val name: String,
    val type: CycleType,
    val kind: BandKind,
    val start: LocalDate,
    val end: LocalDate?,
    val fadeEnd: Boolean,
)

/** One day in the 14-day agenda. */
data class AgendaDay(
    val date: LocalDate,
    val doses: List<Dose>,
    val isToday: Boolean,
)

/** Computed end (exclusive) of a cycle's span given its [Cycle.totalWeeks], or null if unbounded. */
fun cycleEndExclusive(cycle: Cycle): LocalDate? {
    val weeks = cycle.totalWeeks ?: return null
    return cycle.startDate.plus(weeks * 7, DateTimeUnit.DAY)
}

/**
 * Build the ordered list of timeline bands from the active cycle, all other known cycles
 * (completed and any resolved future/chained cycles), and today's date.
 *
 * Classification is purely date-driven, not status-driven: a cycle whose computed span reaches
 * beyond [today] is FUTURE, otherwise COMPLETED (the ACTIVE cycle is always singled out first).
 * A trailing UNPLANNED band is appended from the latest known cycle end up to today+180 whenever
 * there is a gap, so the "unknown future" reads as deliberately unplanned rather than empty.
 */
fun buildTimelineBands(
    today: LocalDate,
    activeCycle: Cycle?,
    otherCycles: List<Cycle>,
): List<CycleBand> {
    val bands = mutableListOf<CycleBand>()

    // Latest concrete date any band is known to cover, used to size the trailing UNPLANNED region.
    var latestKnownEnd: LocalDate = today

    activeCycle?.let { cycle ->
        val end = cycleEndExclusive(cycle)
        bands += CycleBand(
            cycleId = cycle.id,
            name = cycle.name,
            type = cycle.type,
            kind = BandKind.ACTIVE,
            start = cycle.startDate,
            end = end,                 // null = unbounded STANDARD → fades to the right
            fadeEnd = end == null,
        )
        if (end != null && end > latestKnownEnd) latestKnownEnd = end
        // An unbounded active cycle covers the whole future half of the window.
        if (end == null) latestKnownEnd = today.plus(TIMELINE_HALF_SPAN_DAYS, DateTimeUnit.DAY)
    }

    for (cycle in otherCycles) {
        if (activeCycle != null && cycle.id == activeCycle.id) continue
        val concreteEnd = cycleEndExclusive(cycle)
        val reachesFuture = concreteEnd == null || concreteEnd > today
        val kind = if (reachesFuture) BandKind.FUTURE else BandKind.COMPLETED

        // A completed cycle with no computable end (manually-ended STANDARD, unknown end date):
        // cap it at today and fade, since we genuinely don't know when it stopped.
        val end = when {
            concreteEnd != null -> concreteEnd
            kind == BandKind.COMPLETED -> today
            else -> null
        }
        val fadeEnd = concreteEnd == null
        bands += CycleBand(
            cycleId = cycle.id,
            name = cycle.name,
            type = cycle.type,
            kind = kind,
            start = cycle.startDate,
            end = end,
            fadeEnd = fadeEnd,
        )
        if (end != null && end > latestKnownEnd) latestKnownEnd = end
        if (end == null) latestKnownEnd = today.plus(TIMELINE_HALF_SPAN_DAYS, DateTimeUnit.DAY)
    }

    // Trailing "nothing planned yet" region up to the window edge.
    val windowEnd = today.plus(TIMELINE_HALF_SPAN_DAYS, DateTimeUnit.DAY)
    if (latestKnownEnd < windowEnd) {
        bands += CycleBand(
            cycleId = -1L,
            name = "",
            type = CycleType.NORMAL,
            kind = BandKind.UNPLANNED,
            start = latestKnownEnd,
            end = windowEnd,
            fadeEnd = false,
        )
    }

    return bands
}

/**
 * Build the 14-day agenda: today plus the next [AGENDA_WINDOW_DAYS] - 1 days, each carrying the
 * doses scheduled on that local calendar day (time-sorted). Every day in the window is present,
 * including empty ones, so the agenda stays a fixed-length calendar rather than a variable list.
 */
fun buildAgenda(
    today: LocalDate,
    doses: List<Dose>,
    zone: TimeZone,
): List<AgendaDay> {
    val byDate: Map<LocalDate, List<Dose>> = doses
        .groupBy { it.timestamp.toLocalDateTime(zone).date }

    return (0 until AGENDA_WINDOW_DAYS).map { offset ->
        val date = today.plus(offset, DateTimeUnit.DAY)
        AgendaDay(
            date = date,
            doses = (byDate[date] ?: emptyList()).sortedBy { it.timestamp },
            isToday = offset == 0,
        )
    }
}
