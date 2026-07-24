package com.yhdista.dosetracker.ui.today

import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.PlannedDose
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayCalendarModelTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 7, 22)

    private fun cycle(
        id: Long,
        type: CycleType,
        totalWeeks: Int?,
        start: LocalDate,
        status: CycleStatus,
    ) = Cycle(
        id = id, name = "Cyklus $id", type = type, totalWeeks = totalWeeks,
        startDate = start, status = status, onCompleteAction = CycleCompleteAction.TO_NONE,
    )

    private fun doseAt(id: Long, date: LocalDate, hour: Int): Dose = Dose(
        id = id, medicationId = 1, medicationName = "Med",
        timestamp = date.atTime(LocalTime(hour, 0)).toInstant(zone),
        status = DoseStatus.PENDING,
    )

    @Test
    fun `cycleEndExclusive computes start plus totalWeeks, null when unbounded`() {
        val bounded = cycle(1, CycleType.NORMAL, totalWeeks = 4, start = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE)
        assertEquals(LocalDate(2026, 7, 29), cycleEndExclusive(bounded))

        val unbounded = cycle(2, CycleType.STANDARD, totalWeeks = null, start = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE)
        assertNull(cycleEndExclusive(unbounded))
    }

    @Test
    fun `active bounded cycle produces an ACTIVE band with a precise end`() {
        val active = cycle(1, CycleType.NORMAL, totalWeeks = 12, start = LocalDate(2026, 6, 3), status = CycleStatus.ACTIVE)
        val bands = buildTimelineBands(today, activeCycle = active, otherCycles = emptyList())

        val activeBand = bands.single { it.kind == BandKind.ACTIVE }
        assertEquals(1L, activeBand.cycleId)
        assertEquals(LocalDate(2026, 6, 3), activeBand.start)
        assertEquals(LocalDate(2026, 8, 26), activeBand.end)
        assertTrue(!activeBand.fadeEnd)
    }

    @Test
    fun `unbounded active STANDARD cycle fades and has no end`() {
        val active = cycle(1, CycleType.STANDARD, totalWeeks = null, start = LocalDate(2026, 6, 3), status = CycleStatus.ACTIVE)
        val bands = buildTimelineBands(today, activeCycle = active, otherCycles = emptyList())

        val activeBand = bands.single { it.kind == BandKind.ACTIVE }
        assertNull(activeBand.end)
        assertTrue(activeBand.fadeEnd)
        // An unbounded active cycle fills the future half, so no UNPLANNED band is appended.
        assertTrue(bands.none { it.kind == BandKind.UNPLANNED })
    }

    @Test
    fun `past cycle classified COMPLETED, future-reaching cycle classified FUTURE`() {
        val past = cycle(1, CycleType.NORMAL, totalWeeks = 4, start = LocalDate(2026, 4, 1), status = CycleStatus.COMPLETED)
        val future = cycle(2, CycleType.POST, totalWeeks = 4, start = LocalDate(2026, 8, 1), status = CycleStatus.DRAFT)

        val bands = buildTimelineBands(today, activeCycle = null, otherCycles = listOf(past, future))

        assertEquals(BandKind.COMPLETED, bands.single { it.cycleId == 1L }.kind)
        assertEquals(BandKind.FUTURE, bands.single { it.cycleId == 2L }.kind)
    }

    @Test
    fun `no active cycle still appends an UNPLANNED band across the future`() {
        val bands = buildTimelineBands(today, activeCycle = null, otherCycles = emptyList())
        val unplanned = bands.single { it.kind == BandKind.UNPLANNED }
        assertEquals(today, unplanned.start)
    }

    @Test
    fun `completed unbounded STANDARD cycle is capped at today and fades (unknown end)`() {
        val endedStandard = cycle(1, CycleType.STANDARD, totalWeeks = null, start = LocalDate(2026, 5, 1), status = CycleStatus.COMPLETED)
        val bands = buildTimelineBands(today, activeCycle = null, otherCycles = listOf(endedStandard))

        val band = bands.single { it.cycleId == 1L }
        assertEquals(BandKind.COMPLETED, band.kind)
        assertEquals(today, band.end)
        assertTrue(band.fadeEnd)
    }

    @Test
    fun `agenda has exactly 14 days, today first, doses grouped by local date`() {
        val doses = listOf(
            doseAt(1, today, hour = 20),
            doseAt(2, today, hour = 8),
            doseAt(3, LocalDate(2026, 7, 24), hour = 8),
        )
        val agenda = buildAgenda(today, doses, zone)

        assertEquals(AGENDA_WINDOW_DAYS, agenda.size)
        assertEquals(today, agenda.first().date)
        assertTrue(agenda.first().isToday)
        assertTrue(agenda.drop(1).none { it.isToday })

        // Today's two doses are time-sorted (08:00 before 20:00).
        assertEquals(listOf(2L, 1L), agenda.first().doses.map { it.id })
        // The dose two days out lands on the right agenda day.
        assertEquals(listOf(3L), agenda.first { it.date == LocalDate(2026, 7, 24) }.doses.map { it.id })
        // A day with nothing scheduled is still present, just empty.
        assertTrue(agenda.first { it.date == LocalDate(2026, 7, 23) }.doses.isEmpty())
    }

    @Test
    fun `days without generated doses fall back to the projected plan`() {
        val tomorrow = LocalDate(2026, 7, 23)
        val planned = mapOf(
            tomorrow to listOf(
                PlannedDose(
                    scheduleId = 1, medicationId = 1, medicationName = "Med",
                    amount = 500.0, unit = "mg", minutesOfDay = 8 * 60, cycleId = 7L,
                )
            )
        )

        val agenda = buildAgenda(today, doses = listOf(doseAt(1, today, 8)), zone = zone, planned = planned)

        val tomorrowDay = agenda.first { it.date == tomorrow }
        assertTrue(tomorrowDay.doses.isEmpty())
        assertEquals(listOf(8 * 60), tomorrowDay.entries.map { it.minutesOfDay })
        assertEquals(listOf(7L), tomorrowDay.entries.map { it.cycleId })
        assertNull(tomorrowDay.entries.single().dose)
    }

    @Test
    fun `generated doses win over the projection for the same day`() {
        val planned = mapOf(
            today to listOf(
                PlannedDose(
                    scheduleId = 1, medicationId = 1, medicationName = "Med",
                    amount = 500.0, unit = "mg", minutesOfDay = 21 * 60, cycleId = null,
                )
            )
        )

        val agenda = buildAgenda(today, doses = listOf(doseAt(1, today, 8)), zone = zone, planned = planned)

        // The 08:00 real dose is shown, not the 21:00 projection.
        assertEquals(listOf(8 * 60), agenda.first().entries.map { it.minutesOfDay })
        assertEquals(1L, agenda.first().entries.single().dose?.id)
    }
}
