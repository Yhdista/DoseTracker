package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
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
import kotlin.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
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

        val viewModel = TodayViewModel(repository, SavedStateHandle())

        val job = launch { viewModel.uiState.collect {} }

        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(finalState.doses is Data.Success)
        assertEquals(doses, (finalState.doses as Data.Success).data)

        job.cancel()
    }
}
