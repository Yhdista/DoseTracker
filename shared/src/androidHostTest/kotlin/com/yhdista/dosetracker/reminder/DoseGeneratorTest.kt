package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.MedicationUnit
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DoseGeneratorTest {

    private val medicationRepository = mock<MedicationRepository>()
    private val doseRepository = mock<DoseRepository>()
    private val scheduleRepository = mock<ScheduleRepository>()
    private val cycleRepository = mock<CycleRepository>()
    private val scheduler = mock<DoseReminderScheduler>()
    private val cycleLifecycleManager = mock<CycleLifecycleManager>()
    private val generator = DoseGenerator(medicationRepository, doseRepository, scheduleRepository, cycleRepository, scheduler, cycleLifecycleManager)
    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate(2026, 7, 20)
    private val farFutureDate = LocalDate(2099, 1, 5)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `creates a PENDING dose for a schedule matching today's weekday`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(doseRepository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }

    @Test
    fun `skips a schedule that does not include today's weekday`() = runTest {
        val otherDay = DayOfWeek.entries.first { it != today.dayOfWeek }
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(otherDay))
        )
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())

        generator.runForDate(today)

        verify(doseRepository, never()).insertDose(any())
    }

    @Test
    fun `does not duplicate a dose that already exists for this schedule and time`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(farFutureDate.dayOfWeek))
        )
        val expectedInstant = farFutureDate.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.TAKEN)
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, farFutureDate)).thenReturn(existing)

        generator.runForDate(farFutureDate)

        verify(doseRepository, never()).insertDose(any())
        verify(scheduler, never()).scheduleReminder(any(), any())
    }

    @Test
    fun `re-arms the reminder alarm for an already-existing PENDING dose in the future`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(farFutureDate.dayOfWeek))
        )
        val expectedInstant = farFutureDate.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, farFutureDate)).thenReturn(existing)

        generator.runForDate(farFutureDate)

        verify(scheduler).scheduleReminder(5, expectedInstant)
    }

    @Test
    fun `does not re-arm a PENDING dose already in the past`() = runTest {
        val pastDate = LocalDate(2020, 1, 1)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(pastDate.dayOfWeek))
        )
        val expectedInstant = pastDate.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, pastDate)).thenReturn(existing)

        generator.runForDate(pastDate)

        verify(scheduler, never()).scheduleReminder(any(), any())
    }

    @Test
    fun `creates a PENDING dose for a schedule matching an interval matching today`() = runTest {
        val startDate = LocalDate(2026, 7, 18) // 2 days before today (20)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = 0,
            scheduleType = "INTERVAL",
            intervalDays = 2,
            startDate = startDate
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(doseRepository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }

    @Test
    fun `skips a schedule matching an interval not matching today`() = runTest {
        val startDate = LocalDate(2026, 7, 19) // 1 day before today (20)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = 0,
            scheduleType = "INTERVAL",
            intervalDays = 2,
            startDate = startDate
        )
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())

        generator.runForDate(today)

        verify(doseRepository, never()).insertDose(any())
    }

    @Test
    fun `creates a PENDING dose at the configured period time if timeType is PERIOD`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 0,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek)),
            timeType = "PERIOD",
            dayPeriod = "MORNING"
        )
        // Configure morning time to 8:30 (510 minutes)
        val periodTimes = mapOf("MORNING" to 510)
        val expectedInstant = today.atTime(8, 30).toInstant(TimeZone.currentSystemDefault())

        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(periodTimes)
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(doseRepository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }

    private fun fixedClock(at: Instant): Clock = object : Clock {
        override fun now(): Instant = at
    }

    private val zone = TimeZone.currentSystemDefault()

    @Test
    fun `sweeps overdue PENDING doses to MISSED before generating`() = runTest {
        val now = today.atTime(9, 0).toInstant(zone)
        val gen = DoseGenerator(medicationRepository, doseRepository, scheduleRepository, cycleRepository, scheduler, cycleLifecycleManager, fixedClock(now))
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(emptyList()))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())

        gen.runForDate(today)

        verify(doseRepository).markPendingDosesMissedBefore(now - DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes)
    }

    @Test
    fun `arms an immediate reminder for a dose created after its time but still inside the missed window`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val scheduledInstant = today.atTime(8, 0).toInstant(zone)
        val now = today.atTime(9, 0).toInstant(zone)
        val gen = DoseGenerator(medicationRepository, doseRepository, scheduleRepository, cycleRepository, scheduler, cycleLifecycleManager, fixedClock(now))
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        gen.runForDate(today)

        verify(scheduler).scheduleReminder(99, now)
        verify(scheduler).scheduleMissedTimeout(99, scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes)
    }

    @Test
    fun `marks a dose created past the missed window as MISSED with no alarms`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val now = today.atTime(11, 0).toInstant(zone) // 8:00 dose + 120 min window ended 10:00
        val gen = DoseGenerator(medicationRepository, doseRepository, scheduleRepository, cycleRepository, scheduler, cycleLifecycleManager, fixedClock(now))
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        gen.runForDate(today)

        verify(doseRepository).updateDose(argThat { id == 99L && status == DoseStatus.MISSED })
        verify(scheduler, never()).scheduleReminder(any(), any())
        verify(scheduler, never()).scheduleMissedTimeout(any(), any())
    }

    @Test
    fun `re-arms an immediate reminder for an existing PENDING dose still inside the missed window`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val scheduledInstant = today.atTime(8, 0).toInstant(zone)
        val now = today.atTime(9, 0).toInstant(zone)
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = scheduledInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        val gen = DoseGenerator(medicationRepository, doseRepository, scheduleRepository, cycleRepository, scheduler, cycleLifecycleManager, fixedClock(now))
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(existing)

        gen.runForDate(today)

        verify(scheduler).scheduleReminder(5, now)
        verify(scheduler).scheduleMissedTimeout(5, scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes)
    }

    @Test
    fun `two concurrent runs do not create a duplicate dose for the same schedule and date`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        var insertedDose: Dose? = null
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        // Models the real DB race: the existence check reads its result, then suspends
        // before returning, so two unsynchronized runs both observe "no dose yet".
        whenever(doseRepository.getDoseForScheduleOnDate(eq(1L), eq(today))).doSuspendableAnswer {
            val current = insertedDose
            delay(10)
            current
        }
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).doSuspendableAnswer { invocation ->
            insertedDose = (invocation.arguments[0] as Dose).copy(id = 99L)
            Data.Success(99L)
        }

        val first = launch { generator.runForDate(today) }
        val second = launch { generator.runForDate(today) }
        first.join()
        second.join()

        verify(doseRepository, times(1)).insertDose(any())
    }

    @Test
    fun `advances the cycle lifecycle before generating doses`() = runTest {
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(emptyList()))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())

        generator.runForDate(today)

        verify(cycleLifecycleManager).advance(today)
    }

    @Test
    fun `generates a dose for a cycle-linked schedule when its week is the active cycle's current week`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek)),
            cycleWeekId = 100
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(cycleRepository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(cycleRepository.getCycleWeek(1, 0)).thenReturn(week0)
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(doseRepository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, cycleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }

    @Test
    fun `skips a cycle-linked schedule whose week is not the active cycle's current week`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek)),
            cycleWeekId = 200
        )
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(cycleRepository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(cycleRepository.getCycleWeek(1, 0)).thenReturn(week0)

        generator.runForDate(today)

        verify(doseRepository, never()).insertDose(any())
    }

    @Test
    fun `still generates a standalone schedule while a cycle is active`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(scheduleRepository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(cycleRepository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(cycleRepository.getCycleWeek(1, 0)).thenReturn(week0)
        whenever(doseRepository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(medicationRepository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(doseRepository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(doseRepository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, cycleId = null, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }
}
