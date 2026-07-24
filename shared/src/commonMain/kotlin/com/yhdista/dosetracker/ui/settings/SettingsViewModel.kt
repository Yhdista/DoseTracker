package com.yhdista.dosetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.logEach
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val periodTimes: Data<Map<DayPeriod, Int>> = Data.Loading,
    val defaultTimeType: TimeType = TimeType.PERIOD
)

sealed interface SettingsEvent {
    data class UpdatePeriodTime(val period: DayPeriod, val minutesOfDay: Int) : SettingsEvent
    data class UpdateDefaultTimeType(val timeType: TimeType) : SettingsEvent
}

class SettingsViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsState> = combine(
        scheduleRepository.getPeriodTimes(),
        settingsRepository.getDefaultTimeType()
    ) { periodTimesResult, defaultTimeTypeResult ->
        SettingsState(
            periodTimes = periodTimesResult,
            defaultTimeType = defaultTimeTypeResult
        )
    }
    .logEach("SettingsViewModel") { state ->
        val periodTimesDesc = state.periodTimes.describe { it.toString() }
        "State updated: periodTimes=$periodTimesDesc, defaultTimeType=${state.defaultTimeType}"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )


    fun onEvent(event: SettingsEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("SettingsViewModel", "onEvent: $event")
        when (event) {
            is SettingsEvent.UpdatePeriodTime -> {
                viewModelScope.launch {
                    scheduleRepository.updatePeriodTime(event.period, event.minutesOfDay)
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

