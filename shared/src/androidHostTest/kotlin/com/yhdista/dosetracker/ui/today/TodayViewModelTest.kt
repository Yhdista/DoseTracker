package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.domain.usecase.PlanAgendaUseCase
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val doseRepository = mock<DoseRepository>()
    private val cycleRepository = mock<CycleRepository>()
    private val scheduleRepository = mock<ScheduleRepository>()
    private val medicationRepository = mock<MedicationRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private fun viewModel() = TodayViewModel(
        doseRepository,
        cycleRepository,
        scheduleRepository,
        PlanAgendaUseCase(scheduleRepository, medicationRepository, cycleRepository),
        SavedStateHandle(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubCyclesEmpty() {
        whenever(cycleRepository.getCompletedCycles()).thenReturn(flowOf(Data.Success(emptyList())))
    }

    private suspend fun stubSchedulesEmpty() {
        whenever(scheduleRepository.getAllSchedules()).thenReturn(flowOf(Data.Success(emptyList())))
        whenever(scheduleRepository.getPeriodTimes()).thenReturn(flowOf(Data.Success(emptyMap())))
        whenever(scheduleRepository.getEnabledSchedules()).thenReturn(Data.Success(emptyList()))
    }

    @Test
    fun `uiState emits success when repository returns data`() = runTest {
        val doses = listOf(
            Dose(id = 1, medicationId = 1, timestamp = Clock.System.now(), status = DoseStatus.PENDING)
        )
        whenever(doseRepository.getDosesInRange(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(flowOf(Data.Success(doses)))
        whenever(cycleRepository.getActiveCycle()).thenReturn(flowOf(Data.Success(null)))
        stubCyclesEmpty()
        stubSchedulesEmpty()

        val viewModel = viewModel()

        val job = launch { viewModel.uiState.collect {} }

        testDispatcher.scheduler.runCurrent()

        val finalState = viewModel.uiState.value
        assert(finalState.dosesInWindow is Data.Success)
        assertEquals(doses, (finalState.dosesInWindow as Data.Success).data)

        job.cancel()
    }

    @Test
    fun `uiState includes the active cycle when one is running`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(doseRepository.getDosesInRange(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(flowOf(Data.Success(emptyList())))
        whenever(cycleRepository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        stubCyclesEmpty()
        stubSchedulesEmpty()

        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        val finalState = viewModel.uiState.value
        assert(finalState.activeCycle is Data.Success)
        assertEquals(cycle, (finalState.activeCycle as Data.Success).data)

        job.cancel()
    }
}
