package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class CreateCycleState(
    val hasActiveCycle: Boolean? = null, // null while unknown
    val name: String = "",
    val type: CycleType = CycleType.NORMAL,
    val totalWeeks: Int = 4,
    val onCompleteAction: CycleCompleteAction = CycleCompleteAction.TO_STANDARD,
)

sealed interface CreateCycleUiEvent {
    data class Created(val cycleId: Long, val weekCount: Int) : CreateCycleUiEvent
}

sealed interface CreateCycleEvent {
    data class NameChanged(val name: String) : CreateCycleEvent
    data class TypeChanged(val type: CycleType) : CreateCycleEvent
    data class TotalWeeksChanged(val weeks: Int) : CreateCycleEvent
    data class OnCompleteActionChanged(val action: CycleCompleteAction) : CreateCycleEvent
    object Save : CreateCycleEvent
}

class CreateCycleViewModel(
    private val cycleRepository: CycleRepository,
    private val doseGenerator: DoseGenerator
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCycleState())
    val uiState: StateFlow<CreateCycleState> = _state.asStateFlow()

    private val _uiEvents = Channel<CreateCycleUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val active = cycleRepository.getActiveCycleOnce()
            _state.value = _state.value.copy(
                hasActiveCycle = active != null,
                type = if (active != null) CycleType.POST else CycleType.NORMAL
            )
        }
        viewModelScope.launch {
            uiState.collect { state ->
                com.yhdista.dosetracker.core.AppLogger.d(
                    "CreateCycleViewModel",
                    "State updated: hasActiveCycle=${state.hasActiveCycle}, name='${state.name}', type=${state.type}, totalWeeks=${state.totalWeeks}, onCompleteAction=${state.onCompleteAction}"
                )
            }
        }
    }

    fun onEvent(event: CreateCycleEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("CreateCycleViewModel", "onEvent: $event")
        when (event) {
            is CreateCycleEvent.NameChanged -> _state.value = _state.value.copy(name = event.name)
            is CreateCycleEvent.TypeChanged -> _state.value = _state.value.copy(type = event.type)
            is CreateCycleEvent.TotalWeeksChanged -> _state.value = _state.value.copy(totalWeeks = event.weeks)
            is CreateCycleEvent.OnCompleteActionChanged -> _state.value = _state.value.copy(onCompleteAction = event.action)
            is CreateCycleEvent.Save -> save()
        }
    }

    private fun save() {
        viewModelScope.launch {
            val current = _state.value
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val active = cycleRepository.getActiveCycleOnce()

            val result: Data<Long>? = when {
                active == null -> {
                    val cycle = Cycle(
                        name = current.name,
                        type = current.type,
                        totalWeeks = if (current.type == CycleType.STANDARD) null else current.totalWeeks,
                        startDate = today,
                        status = CycleStatus.ACTIVE,
                        onCompleteAction = current.onCompleteAction
                    )
                    cycleRepository.createCycle(cycle).also {
                        if (it is Data.Success) doseGenerator.runForToday()
                    }
                }
                current.type == CycleType.STANDARD -> {
                    val existing = cycleRepository.getStandardCycle()
                    if (existing != null) {
                        cycleRepository.updateCycle(existing.copy(name = current.name))
                        Data.Success(existing.id)
                    } else {
                        val cycle = Cycle(
                            name = current.name,
                            type = CycleType.STANDARD,
                            totalWeeks = null,
                            startDate = today,
                            status = CycleStatus.DRAFT,
                            onCompleteAction = CycleCompleteAction.TO_NONE
                        )
                        cycleRepository.createCycle(cycle)
                    }
                }
                current.type == CycleType.POST -> {
                    val cycle = Cycle(
                        name = current.name,
                        type = CycleType.POST,
                        totalWeeks = current.totalWeeks,
                        startDate = today,
                        status = CycleStatus.DRAFT,
                        onCompleteAction = CycleCompleteAction.TO_NONE
                    )
                    val created = cycleRepository.createCycle(cycle)
                    if (created is Data.Success) {
                        cycleRepository.updateCycle(
                            active.copy(nextCycleId = created.data, onCompleteAction = CycleCompleteAction.TO_POST)
                        )
                    }
                    created
                }
                else -> null // NORMAL type while a cycle is already active is not offered by the UI.
            }

            if (result is Data.Success) {
                val weekCount = if (current.type == CycleType.STANDARD) 1 else current.totalWeeks
                _uiEvents.send(CreateCycleUiEvent.Created(result.data, weekCount))
            }
        }
    }
}
