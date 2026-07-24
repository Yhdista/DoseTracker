package com.yhdista.dosetracker.ui.cycle

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.CycleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CycleSettingsViewModelTest {

    private val repository = mock<CycleRepository>()
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
    fun `rename updates only the name, leaving other fields untouched`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 12,
            startDate = LocalDate(2026, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        val viewModel = CycleSettingsViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.rename("primo")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(cycle.copy(name = "primo"))

        job.cancel()
    }

    @Test
    fun `endCycle marks the active cycle COMPLETED without touching onCompleteAction routing`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2026, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        val viewModel = CycleSettingsViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.endCycle()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(cycle.copy(status = CycleStatus.COMPLETED))

        job.cancel()
    }
}
