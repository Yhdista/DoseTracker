package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.repository.CycleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CycleSettingsUiEvent {
    data object CycleEnded : CycleSettingsUiEvent
}

class CycleSettingsViewModel(
    private val cycleRepository: CycleRepository
) : ViewModel() {

    private val _uiEvents = Channel<CycleSettingsUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    val uiState: StateFlow<Data<Cycle?>> = cycleRepository.getActiveCycle()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Data.Loading
        )

    fun rename(name: String) {
        viewModelScope.launch {
            val cycle = cycleRepository.getActiveCycleOnce() ?: return@launch
            cycleRepository.updateCycle(cycle.copy(name = name))
        }
    }

    fun endCycle() {
        viewModelScope.launch {
            val cycle = cycleRepository.getActiveCycleOnce() ?: return@launch
            val result = cycleRepository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))
            if (result is Data.Success) {
                _uiEvents.send(CycleSettingsUiEvent.CycleEnded)
            }
        }
    }
}
