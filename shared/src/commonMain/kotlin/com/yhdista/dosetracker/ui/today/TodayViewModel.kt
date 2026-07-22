package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

data class TodayState(
    val doses: Data<List<Dose>> = Data.Loading,
    val activeCycle: Data<Cycle?> = Data.Loading,
    val selectedDoseId: Long? = null
)

sealed interface TodayEvent {
    data class ToggleDoseStatus(val dose: Dose) : TodayEvent
    data class SelectDose(val id: Long?) : TodayEvent
    object EndActiveCycle : TodayEvent
}

class TodayViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _selectedDoseId = savedStateHandle.getStateFlow<Long?>("selectedDoseId", null)

    val uiState: StateFlow<TodayState> = combine(
        repository.getDosesForDate(Clock.System.todayIn(TimeZone.currentSystemDefault())),
        repository.getActiveCycle(),
        _selectedDoseId
    ) { doses, activeCycle, selectedId ->
        TodayState(
            doses = doses,
            activeCycle = activeCycle,
            selectedDoseId = selectedId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayState()
    )

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val dosesDesc = state.doses.describe { doses ->
                    doses.joinToString(prefix = "[", postfix = "]") { dose ->
                        val timeStr = try {
                            dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).time.toString().substring(0, 5)
                        } catch (e: Exception) {
                            "??:??"
                        }
                        "${dose.medicationName} ${dose.amount ?: ""}${dose.unit ?: ""} @ $timeStr (${dose.status})"
                    }
                }
                val cycleDesc = state.activeCycle.describe { cycle -> "name=${cycle?.name ?: "none"}" }
                com.yhdista.dosetracker.core.AppLogger.d("TodayViewModel", "State updated: doses=$dosesDesc, activeCycle=$cycleDesc, selectedDoseId=${state.selectedDoseId}")
            }
        }
    }

    fun onEvent(event: TodayEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("TodayViewModel", "onEvent: $event")
        when (event) {
            is TodayEvent.ToggleDoseStatus -> toggleDoseStatus(event.dose)
            is TodayEvent.SelectDose -> selectDose(event.id)
            is TodayEvent.EndActiveCycle -> endActiveCycle()
        }
    }

    private fun toggleDoseStatus(dose: Dose) {
        viewModelScope.launch {
            val newStatus = if (dose.status == DoseStatus.TAKEN) DoseStatus.PENDING else DoseStatus.TAKEN
            repository.updateDose(dose.copy(status = newStatus))
        }
    }

    private fun selectDose(id: Long?) {
        savedStateHandle["selectedDoseId"] = id
    }

    private fun endActiveCycle() {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleOnce() ?: return@launch
            repository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))
        }
    }
}
