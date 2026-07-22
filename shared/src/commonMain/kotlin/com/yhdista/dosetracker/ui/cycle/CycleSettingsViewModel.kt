package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CycleSettingsViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    val uiState: StateFlow<Data<Cycle?>> = repository.getActiveCycle()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Data.Loading
        )

    fun rename(name: String) {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleOnce() ?: return@launch
            repository.updateCycle(cycle.copy(name = name))
        }
    }

    fun endCycle() {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleOnce() ?: return@launch
            repository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))
        }
    }
}
