package com.yhdista.dosetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val periodTimes: Data<Map<String, Int>> = Data.Loading
)

sealed interface SettingsEvent {
    data class UpdatePeriodTime(val period: String, val minutesOfDay: Int) : SettingsEvent
}

class SettingsViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsState> = repository.getPeriodTimes()
        .map { result -> SettingsState(periodTimes = result) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsState()
        )

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.UpdatePeriodTime -> {
                viewModelScope.launch {
                    repository.updatePeriodTime(event.period, event.minutesOfDay)
                }
            }
        }
    }
}
