package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * State for the Today "Kalendář" screen.
 *
 * The screen derives the timeline bands and the 14-day agenda from these raw flows via the
 * pure functions in TodayCalendarModel (kept out of the ViewModel so they stay unit-testable
 * without a coroutine harness).
 *
 * @param futureCycles cycles reachable from the active cycle's onCompleteAction/nextCycleId
 *   chain (already resolved). Empty when nothing is chained — the honest common case.
 */
data class TodayState(
    val dosesInWindow: Data<List<Dose>> = Data.Loading,
    val activeCycle: Data<Cycle?> = Data.Loading,
    val completedCycles: Data<List<Cycle>> = Data.Loading,
    val futureCycles: List<Cycle> = emptyList(),
    val selectedDoseId: Long? = null,
)

sealed interface TodayEvent {
    data class ToggleDoseStatus(val dose: Dose) : TodayEvent
    data class SelectDose(val id: Long?) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _selectedDoseId = savedStateHandle.getStateFlow<Long?>("selectedDoseId", null)

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Resolve the chain of upcoming cycles (nextCycleId) so the timeline can draw them ahead of today. */
    private val futureCyclesFlow = repository.getActiveCycle().mapLatest { data ->
        val active = (data as? Data.Success)?.data
        val resolved = mutableListOf<Cycle>()
        val guard = mutableSetOf<Long>()
        var nextId = active?.nextCycleId
        while (nextId != null && guard.add(nextId)) {
            val chained = repository.getCycleById(nextId) ?: break
            resolved += chained
            nextId = chained.nextCycleId
        }
        resolved
    }

    val uiState: StateFlow<TodayState> = combine(
        repository.getDosesInRange(today, today.plus(AGENDA_WINDOW_DAYS, DateTimeUnit.DAY)),
        repository.getActiveCycle(),
        repository.getCompletedCycles(),
        futureCyclesFlow,
        _selectedDoseId
    ) { doses, activeCycle, completedCycles, futureCycles, selectedId ->
        TodayState(
            dosesInWindow = doses,
            activeCycle = activeCycle,
            completedCycles = completedCycles,
            futureCycles = futureCycles,
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
                val dosesDesc = state.dosesInWindow.describe { doses ->
                    "count=${doses.size}"
                }
                val cycleDesc = state.activeCycle.describe { cycle -> "name=${cycle?.name ?: "none"}" }
                val futureDesc = state.futureCycles.joinToString(prefix = "[", postfix = "]") { it.name }
                com.yhdista.dosetracker.core.AppLogger.d("TodayViewModel", "State updated: doses=$dosesDesc, activeCycle=$cycleDesc, futureCycles=$futureDesc, selectedDoseId=${state.selectedDoseId}")
            }
        }
    }

    fun onEvent(event: TodayEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("TodayViewModel", "onEvent: $event")
        when (event) {
            is TodayEvent.ToggleDoseStatus -> toggleDoseStatus(event.dose)
            is TodayEvent.SelectDose -> selectDose(event.id)
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
}
