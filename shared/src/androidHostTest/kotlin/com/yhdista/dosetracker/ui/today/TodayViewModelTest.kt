package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
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
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

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
    fun `uiState emits success when repository returns data`() = runTest {
        val doses = listOf(
            Dose(id = 1, medicationId = 1, timestamp = Clock.System.now(), status = DoseStatus.PENDING)
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(doses)))
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(null)))

        val viewModel = TodayViewModel(repository, SavedStateHandle())

        val job = launch { viewModel.uiState.collect {} }

        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(finalState.doses is Data.Success)
        assertEquals(doses, (finalState.doses as Data.Success).data)

        job.cancel()
    }

    @Test
    fun `uiState includes the active cycle when one is running`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(emptyList())))
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))

        val viewModel = TodayViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(finalState.activeCycle is Data.Success)
        assertEquals(cycle, (finalState.activeCycle as Data.Success).data)

        job.cancel()
    }

    @Test
    fun `EndActiveCycle marks the active cycle COMPLETED without touching onCompleteAction routing`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2026, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(emptyList())))
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        val viewModel = TodayViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TodayEvent.EndActiveCycle)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(cycle.copy(status = CycleStatus.COMPLETED))

        job.cancel()
    }
}
