package com.yhdista.dosetracker.ui.cycle

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.usecase.CreateCycleUseCase
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CreateCycleViewModelTest {

    private val repository = mock<CycleRepository>()
    private val doseGenerator = mock<DoseGenerator>()
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
    fun `creates and activates a NORMAL cycle immediately when there is no active cycle`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(1L))

        val viewModel = CreateCycleViewModel(repository, CreateCycleUseCase(repository, doseGenerator))
        testDispatcher.scheduler.advanceUntilIdle()

        val events = mutableListOf<CreateCycleUiEvent>()
        val eventJob = launch { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.onEvent(CreateCycleEvent.NameChanged("Cyklus"))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(4))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).createCycle(
            argThat { name == "Cyklus" && type == CycleType.NORMAL && totalWeeks == 4 && status == CycleStatus.ACTIVE }
        )
        assert(events.single() == CreateCycleUiEvent.Created(cycleId = 1L, weekCount = 4))
        eventJob.cancel()
    }

    @Test
    fun `editing the STANDARD template in place updates the existing row instead of creating a new one`() = runTest {
        val existing = Cycle(
            id = 9, name = "Stary nazev", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2025, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(existing)
        whenever(repository.getStandardCycle()).thenReturn(existing)

        val viewModel = CreateCycleViewModel(repository, CreateCycleUseCase(repository, doseGenerator))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Novy nazev"))
        viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.STANDARD))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(existing.copy(name = "Novy nazev"))
        verify(repository, never()).createCycle(any())
    }

    @Test
    fun `creating a POST cycle attaches it as the active cycle's next cycle`() = runTest {
        val active = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(active)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(5L))

        val viewModel = CreateCycleViewModel(repository, CreateCycleUseCase(repository, doseGenerator))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Post-cyklus"))
        viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.POST))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(2))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).createCycle(
            argThat { name == "Post-cyklus" && type == CycleType.POST && totalWeeks == 2 && status == CycleStatus.DRAFT }
        )
        verify(repository).updateCycle(
            active.copy(nextCycleId = 5L, onCompleteAction = CycleCompleteAction.TO_POST)
        )
    }
}
