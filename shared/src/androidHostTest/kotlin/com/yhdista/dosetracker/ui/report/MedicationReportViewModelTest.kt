package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.SavedStateHandle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReportViewModelTest {

    private val repository = mock<MedicationRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private val zone = TimeZone.currentSystemDefault()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buckets TAKEN doses by week and fills weeks with no doses as zero`() = runTest {
        // July 2026's weeks (Monday-aligned): 06-29, 07-06, 07-13, 07-20, 07-27
        val doses = listOf(
            Dose(id = 1, medicationId = 10, timestamp = LocalDate(2026, 7, 1).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 2, medicationId = 10, timestamp = LocalDate(2026, 7, 14).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 3, medicationId = 10, timestamp = LocalDate(2026, 7, 15).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 4, medicationId = 10, timestamp = LocalDate(2026, 7, 21).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.MISSED)
        )
        whenever(repository.getMedicationById(10)).thenReturn(flowOf(Data.Success(Medication(id = 10, name = "Aspirin", dosage = 500.0, unit = "mg"))))
        whenever(repository.getDosesForMedicationInRange(eq(10L), any(), any())).thenReturn(flowOf(Data.Success(doses)))

        val viewModel = MedicationReportViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        viewModel.setMedicationId(10)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val weeks = (state.weeks as Data.Success).data

        assertEquals("Aspirin", state.medicationName)
        assertEquals("mg", state.unit)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate(2026, 6, 29), weeks[0].weekStart)
        assertEquals(500.0, weeks[0].totalTaken, 0.0)
        assertEquals(LocalDate(2026, 7, 6), weeks[1].weekStart)
        assertEquals(0.0, weeks[1].totalTaken, 0.0)
        assertEquals(LocalDate(2026, 7, 13), weeks[2].weekStart)
        assertEquals(1000.0, weeks[2].totalTaken, 0.0)
        assertEquals(LocalDate(2026, 7, 20), weeks[3].weekStart)
        assertEquals(0.0, weeks[3].totalTaken, 0.0) // MISSED dose doesn't count
        assertEquals(LocalDate(2026, 7, 27), weeks[4].weekStart)
        assertEquals(0.0, weeks[4].totalTaken, 0.0)

        job.cancel()
    }

    @Test
    fun `PreviousPeriod and NextPeriod shift by one month, ToggleMode switches to year anchored on the current period`() = runTest {
        whenever(repository.getMedicationById(any())).thenReturn(flowOf(Data.Success(Medication(id = 10, name = "Aspirin", dosage = 500.0, unit = "mg"))))
        whenever(repository.getDosesForMedicationInRange(any(), any(), any())).thenReturn(flowOf(Data.Success(emptyList())))

        val viewModel = MedicationReportViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        viewModel.setMedicationId(10)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReportRangeMode.MONTH, viewModel.uiState.value.mode)
        val initialPeriodStart = viewModel.uiState.value.periodStart

        viewModel.onEvent(MedicationReportEvent.NextPeriod)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialPeriodStart.plus(1, DateTimeUnit.MONTH), viewModel.uiState.value.periodStart)

        viewModel.onEvent(MedicationReportEvent.PreviousPeriod)
        viewModel.onEvent(MedicationReportEvent.PreviousPeriod)
        testDispatcher.scheduler.advanceUntilIdle()
        val monthBefore = initialPeriodStart.minus(1, DateTimeUnit.MONTH)
        assertEquals(monthBefore, viewModel.uiState.value.periodStart)

        viewModel.onEvent(MedicationReportEvent.ToggleMode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReportRangeMode.YEAR, viewModel.uiState.value.mode)
        assertEquals(LocalDate(monthBefore.year, 1, 1), viewModel.uiState.value.periodStart)

        job.cancel()
    }
}
