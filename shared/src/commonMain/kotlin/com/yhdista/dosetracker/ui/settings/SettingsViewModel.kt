package com.yhdista.dosetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val periodTimes: Data<Map<String, Int>> = Data.Loading,
    val defaultTimeType: TimeType = TimeType.PERIOD
)

sealed interface SettingsEvent {
    data class UpdatePeriodTime(val period: String, val minutesOfDay: Int) : SettingsEvent
    data class UpdateDefaultTimeType(val timeType: TimeType) : SettingsEvent
}

class SettingsViewModel(
    private val repository: MedicationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsState> = combine(
        repository.getPeriodTimes(),
        settingsRepository.getDefaultTimeType()
    ) { periodTimesResult, defaultTimeTypeResult ->
        SettingsState(
            periodTimes = periodTimesResult,
            defaultTimeType = defaultTimeTypeResult
        )
    }
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
            is SettingsEvent.UpdateDefaultTimeType -> {
                viewModelScope.launch {
                    settingsRepository.setDefaultTimeType(event.timeType)
                }
            }
        }
    }
}

