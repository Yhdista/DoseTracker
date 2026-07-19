package com.yhdista.dosetracker.ui.report

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val repository = mock<MedicationRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `summarizes counts and quantities per medication for the current week`() = runTest {
        val instant = Clock.System.now()
        val doses = listOf(
            Dose(id = 1, medicationId = 1, medicationName = "Aspirin", timestamp = instant, amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 2, medicationId = 1, medicationName = "Aspirin", timestamp = instant, amount = 500.0, unit = "mg", status = DoseStatus.MISSED),
            Dose(id = 3, medicationId = 1, medicationName = "Aspirin", timestamp = instant, amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 4, medicationId = 2, medicationName = "Ibuprofen", timestamp = instant, amount = 400.0, unit = "mg", status = DoseStatus.SKIPPED),
            Dose(id = 5, medicationId = 2, medicationName = "Ibuprofen", timestamp = instant, amount = 400.0, unit = "mg", status = DoseStatus.PENDING)
        )
        whenever(repository.getDosesInWeek(any())).thenReturn(flowOf(Data.Success(doses)))

        val viewModel = ReportViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val summaries = (viewModel.uiState.value.summaries as Data.Success).data
        val aspirin = summaries.first { it.medicationName == "Aspirin" }
        val ibuprofen = summaries.first { it.medicationName == "Ibuprofen" }

        assertEquals(1L, aspirin.medicationId)
        assertEquals(2, aspirin.taken)
        assertEquals(1, aspirin.missed)
        assertEquals(0, aspirin.skipped)
        assertEquals(1000.0, aspirin.totalAmountTaken, 0.0)
        assertEquals(1500.0, aspirin.totalAmountScheduled, 0.0)
        assertEquals("mg", aspirin.unit)

        assertEquals(2L, ibuprofen.medicationId)
        assertEquals(1, ibuprofen.skipped)
        assertEquals(1, ibuprofen.upcoming)
        assertEquals(0.0, ibuprofen.totalAmountTaken, 0.0)
        assertEquals(800.0, ibuprofen.totalAmountScheduled, 0.0)
        assertEquals("mg", ibuprofen.unit)

        job.cancel()
    }

    @Test
    fun `weekStartOf returns the Monday on or before the given date`() {
        assertEquals(LocalDate(2026, 7, 13), weekStartOf(LocalDate(2026, 7, 13))) // a Monday itself
        assertEquals(LocalDate(2026, 7, 13), weekStartOf(LocalDate(2026, 7, 19))) // the Sunday ending that week
        assertEquals(LocalDate(2026, 7, 20), weekStartOf(LocalDate(2026, 7, 20))) // the following Monday
    }

    @Test
    fun `PreviousWeek and NextWeek shift the queried week by 7 days`() = runTest {
        whenever(repository.getDosesInWeek(any())).thenReturn(flowOf(Data.Success(emptyList())))

        val viewModel = ReportViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val initialWeekStart = viewModel.uiState.value.weekStart

        viewModel.onEvent(ReportEvent.NextWeek)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialWeekStart.plus(kotlinx.datetime.DatePeriod(days = 7)), viewModel.uiState.value.weekStart)

        viewModel.onEvent(ReportEvent.PreviousWeek)
        viewModel.onEvent(ReportEvent.PreviousWeek)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialWeekStart.plus(kotlinx.datetime.DatePeriod(days = -7)), viewModel.uiState.value.weekStart)

        job.cancel()
    }
}
