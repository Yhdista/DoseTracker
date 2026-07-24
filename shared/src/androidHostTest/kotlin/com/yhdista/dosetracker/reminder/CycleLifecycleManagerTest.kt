package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CycleLifecycleManagerTest {

    private val repository = mock<MedicationRepository>()
    private val manager = CycleLifecycleManager(repository)
    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate(2026, 7, 20)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `does nothing when there is no active cycle`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)

        manager.advance(today)

        verify(repository, never()).completeAndActivateCycle(any(), anyOrNull())
    }

    @Test
    fun `does nothing while a STANDARD cycle is active`() = runTest {
        val standard = Cycle(
            id = 1, name = "Standardni cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(standard)

        manager.advance(today)

        verify(repository, never()).completeAndActivateCycle(any(), anyOrNull())
    }

    @Test
    fun `does nothing while the active cycle still has weeks remaining`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        manager.advance(today)

        verify(repository, never()).completeAndActivateCycle(any(), anyOrNull())
    }

    @Test
    fun `completes a NORMAL cycle and activates the STANDARD cycle in one atomic call`() = runTest {
        val startDate = LocalDate(2026, 6, 22) // 4 full weeks (28 days) before today
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val standard = Cycle(
            id = 2, name = "Standardni cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.DRAFT, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getStandardCycle()).thenReturn(standard)

        manager.advance(today)

        verify(repository).completeAndActivateCycle(
            cycle.copy(status = CycleStatus.COMPLETED),
            standard.copy(status = CycleStatus.ACTIVE, startDate = today)
        )
        verify(repository, never()).updateCycle(any())
    }

    @Test
    fun `completes a cycle and activates its attached POST cycle in one atomic call`() = runTest {
        val startDate = LocalDate(2026, 6, 22)
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_POST,
            nextCycleId = 5
        )
        val post = Cycle(
            id = 5, name = "Post-cyklus", type = CycleType.POST, totalWeeks = 2,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.DRAFT, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getCycleById(5)).thenReturn(post)

        manager.advance(today)

        verify(repository).completeAndActivateCycle(
            cycle.copy(status = CycleStatus.COMPLETED),
            post.copy(status = CycleStatus.ACTIVE, startDate = today)
        )
    }

    @Test
    fun `completes a POST cycle into no active cycle`() = runTest {
        val startDate = LocalDate(2026, 7, 6) // 2 full weeks (14 days) before today
        val post = Cycle(
            id = 5, name = "Post-cyklus", type = CycleType.POST, totalWeeks = 2,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(post)

        manager.advance(today)

        verify(repository).completeAndActivateCycle(post.copy(status = CycleStatus.COMPLETED), null)
        verify(repository, never()).getStandardCycle()
        verify(repository, never()).getCycleById(any())
    }

    @Test
    fun `completes the cycle even when the TO_STANDARD target is missing`() = runTest {
        val startDate = LocalDate(2026, 6, 22)
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getStandardCycle()).thenReturn(null)

        manager.advance(today)

        verify(repository).completeAndActivateCycle(cycle.copy(status = CycleStatus.COMPLETED), null)
    }

    @Test
    fun `recomputes correctly after several idle days past the cycle boundary`() = runTest {
        val startDate = LocalDate(2026, 6, 12) // 38 days before today, well past a 4-week (28 day) cycle
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val standard = Cycle(
            id = 2, name = "Standardni cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.DRAFT, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getStandardCycle()).thenReturn(standard)

        manager.advance(today)

        verify(repository).completeAndActivateCycle(
            cycle.copy(status = CycleStatus.COMPLETED),
            standard.copy(status = CycleStatus.ACTIVE, startDate = today)
        )
    }
}
