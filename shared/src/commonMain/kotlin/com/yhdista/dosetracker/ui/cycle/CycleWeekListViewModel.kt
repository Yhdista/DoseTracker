package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CycleWeekListState(
    val cycleId: Long? = null,
    val weeks: Data<List<CycleWeek>> = Data.Loading
)

@OptIn(ExperimentalCoroutinesApi::class)
class CycleWeekListViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cycleIdFlow = savedStateHandle.getStateFlow<Long?>("cycleId", null)

    val uiState: StateFlow<CycleWeekListState> = cycleIdFlow
        .filterNotNull()
        .flatMapLatest { cycleId ->
            repository.getWeeksForCycle(cycleId).map { weeks -> CycleWeekListState(cycleId, weeks) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CycleWeekListState()
        )

    fun setCycleId(cycleId: Long) {
        if (savedStateHandle.get<Long>("cycleId") == null) {
            savedStateHandle["cycleId"] = cycleId
        }
    }
}
