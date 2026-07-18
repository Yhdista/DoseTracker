package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DoseGeneratorTest {

    private val repository = mock<MedicationRepository>()
    private val scheduler = mock<DoseReminderScheduler>()
    private val generator = DoseGenerator(repository, scheduler)
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
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(null)
        whenever(repository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = "mg"))
        whenever(repository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(repository).insertDose(
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
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))

        generator.runForDate(today)

        verify(repository, never()).insertDose(any())
        verify(repository, never()).getDoseForSchedule(any(), any())
    }

    @Test
    fun `does not duplicate a dose that already exists for this schedule and time`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.TAKEN)
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(existing)

        generator.runForDate(today)

        verify(repository, never()).insertDose(any())
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
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(existing)

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
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(existing)

        generator.runForDate(pastDate)

        verify(scheduler, never()).scheduleReminder(any(), any())
    }
}
