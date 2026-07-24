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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
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

    /** ViewModel whose "today" is pinned, so week-of-year selections are deterministic. */
    private fun viewModelAt(today: LocalDate): CreateCycleViewModel {
        val fixedClock = object : Clock {
            override fun now(): Instant = today.atTime(12, 0).toInstant(TimeZone.UTC)
        }
        return CreateCycleViewModel(
            repository,
            CreateCycleUseCase(repository, doseGenerator),
            clock = fixedClock,
            zone = TimeZone.UTC,
        )
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
    fun `a picked start week sets the cycle's start date instead of today`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(2L))

        val viewModel = viewModelAt(LocalDate(2026, 7, 24))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Cyklus"))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(4))
        viewModel.onEvent(CreateCycleEvent.StartModeChanged(CycleStartMode.WEEK))
        viewModel.onEvent(CreateCycleEvent.StartWeekChanged(33))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        // Monday of ISO week 33 of 2026.
        verify(repository).createCycle(argThat { startDate == LocalDate(2026, 8, 10) })
    }

    @Test
    fun `defaults to today when the start mode is left on TODAY`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(3L))

        val today = LocalDate(2026, 7, 24)
        val viewModel = viewModelAt(today)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Cyklus"))
        viewModel.onEvent(CreateCycleEvent.StartWeekChanged(10)) // ignored while mode is TODAY
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).createCycle(argThat { startDate == today })
    }

    @Test
    fun `a start week whose whole cycle already elapsed is rejected`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)

        val viewModel = viewModelAt(LocalDate(2026, 7, 24))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Cyklus"))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(4))
        viewModel.onEvent(CreateCycleEvent.StartModeChanged(CycleStartMode.WEEK))
        viewModel.onEvent(CreateCycleEvent.StartWeekChanged(10)) // March 2026, long over
        testDispatcher.scheduler.advanceUntilIdle()

        assert(viewModel.uiState.value.startsInThePast)
        assert(!viewModel.uiState.value.isValid)

        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository, never()).createCycle(any())
    }

    @Test
    fun `a backdated start week that is still running is accepted`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(4L))

        val viewModel = viewModelAt(LocalDate(2026, 7, 24))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Cyklus"))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(4))
        viewModel.onEvent(CreateCycleEvent.StartModeChanged(CycleStartMode.WEEK))
        viewModel.onEvent(CreateCycleEvent.StartWeekChanged(29)) // started 2026-07-13, 4 weeks
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).createCycle(argThat { startDate == LocalDate(2026, 7, 13) })
    }

    @Test
    fun `the week picker is not offered for a chained POST cycle`() = runTest {
        val active = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(active)

        val viewModel = viewModelAt(LocalDate(2026, 7, 24))
        testDispatcher.scheduler.advanceUntilIdle()

        assert(!viewModel.uiState.value.canPickStartWeek)
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
