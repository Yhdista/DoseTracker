package com.yhdista.dosetracker.domain.usecase

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.MedicationUnit
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlanAgendaUseCaseTest {

    private val scheduleRepository = mock<ScheduleRepository>()
    private val medicationRepository = mock<MedicationRepository>()
    private val cycleRepository = mock<CycleRepository>()
    private val useCase = PlanAgendaUseCase(scheduleRepository, medicationRepository, cycleRepository)

    private val monday = LocalDate(2026, 7, 20)
    private val medication = Medication(id = 10, name = "Paracetamol", dosage = 500.0, unit = MedicationUnit.MG)

    @Before
    fun setup() = runTest {
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(medication)
    }

    private fun cycle(start: LocalDate) = Cycle(
        id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
        startDate = start, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE,
    )

    @Test
    fun `projects a weekday schedule onto every matching day ahead`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        )
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(cycleRepository.getActiveCycleOnce()).thenReturn(null)

        val planned = useCase(monday, days = 14)

        assertEquals(
            listOf(
                LocalDate(2026, 7, 20), LocalDate(2026, 7, 22),
                LocalDate(2026, 7, 27), LocalDate(2026, 7, 29),
            ),
            planned.keys.sorted()
        )
        val entry = planned.getValue(LocalDate(2026, 7, 22)).single()
        assertEquals("Paracetamol", entry.medicationName)
        assertEquals(480, entry.minutesOfDay)
        assertNull(entry.cycleId)
    }

    @Test
    fun `a cycle-week schedule is projected only inside its own week`() = runTest {
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val week1 = CycleWeek(id = 101, cycleId = 1, weekIndex = 1)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 420,
            daysOfWeek = WeekDays.toBitmask(DayOfWeek.entries.toSet()),
            cycleWeekId = week1.id,
        )
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(cycleRepository.getActiveCycleOnce()).thenReturn(cycle(start = monday))
        whenever(cycleRepository.getCycleWeek(eq(1L), eq(0))).thenReturn(week0)
        whenever(cycleRepository.getCycleWeek(eq(1L), eq(1))).thenReturn(week1)
        whenever(cycleRepository.getCycleWeek(eq(1L), eq(2))).thenReturn(null)

        val planned = useCase(monday, days = 14)

        // Week 1 of the cycle = the seven days starting a week after its start.
        assertEquals((7..13).map { monday.plusDays(it) }, planned.keys.sorted())
        assertEquals(1L, planned.values.first().single().cycleId)
    }

    @Test
    fun `nothing is projected before a cycle that has not started yet`() = runTest {
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 420,
            daysOfWeek = WeekDays.toBitmask(DayOfWeek.entries.toSet()),
            cycleWeekId = week0.id,
        )
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(cycleRepository.getActiveCycleOnce()).thenReturn(cycle(start = monday.plusDays(5)))
        whenever(cycleRepository.getCycleWeek(any(), any())).thenReturn(week0)

        val planned = useCase(monday, days = 14)

        assertTrue(planned.keys.none { it < monday.plusDays(5) })
        assertEquals(monday.plusDays(5), planned.keys.min())
    }

    @Test
    fun `no enabled schedules means nothing planned`() = runTest {
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(emptyList()))

        assertTrue(useCase(monday, days = 14).isEmpty())
    }

    private fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)
}
