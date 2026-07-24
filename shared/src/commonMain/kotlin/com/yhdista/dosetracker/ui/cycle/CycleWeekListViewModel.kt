package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.repository.CycleRepository
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
    private val cycleRepository: CycleRepository,
    cycleId: Long,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    init {
        savedStateHandle["cycleId"] = cycleId
    }

    private val cycleIdFlow = savedStateHandle.getStateFlow<Long?>("cycleId", null)

    val uiState: StateFlow<CycleWeekListState> = cycleIdFlow
        .filterNotNull()
        .flatMapLatest { cycleId ->
            cycleRepository.getWeeksForCycle(cycleId).map { weeks -> CycleWeekListState(cycleId, weeks) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CycleWeekListState()
        )

}
