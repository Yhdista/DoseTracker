package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.isoWeekOf
import com.yhdista.dosetracker.core.isoWeekStart
import com.yhdista.dosetracker.core.isoWeeksInYear
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.usecase.CreateCycleUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** Where the new cycle's first day comes from. */
enum class CycleStartMode {
    /** Starts today (the previous, only behaviour). */
    TODAY,

    /** Starts on the Monday of a picked ISO week — may lie in the past or the future. */
    WEEK,
}

data class CreateCycleState(
    val hasActiveCycle: Boolean? = null, // null while unknown
    val name: String = "",
    val type: CycleType = CycleType.NORMAL,
    val totalWeeks: Int = 4,
    val onCompleteAction: CycleCompleteAction = CycleCompleteAction.TO_STANDARD,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val startMode: CycleStartMode = CycleStartMode.TODAY,
    val startYear: Int = 1970,
    val startWeek: Int = 1,
) {
    /**
     * Only cycles that really get a start date offer the week picker: a STANDARD cycle is a
     * template, and a POST cycle is chained after the active one (its start date is overwritten
     * when it is activated), so picking a week there would be a lie.
     */
    val canPickStartWeek: Boolean
        get() = hasActiveCycle == false && type != CycleType.STANDARD

    /** The date the cycle would start with the current selection. */
    val startDate: LocalDate
        get() = if (canPickStartWeek && startMode == CycleStartMode.WEEK) {
            isoWeekStart(startYear, startWeek)
        } else {
            today
        }

    /** True when the picked week is so far back that the cycle would already be over. */
    val startsInThePast: Boolean
        get() = canPickStartWeek &&
            startMode == CycleStartMode.WEEK &&
            totalWeeks > 0 &&
            startDate.plus(totalWeeks * 7, DateTimeUnit.DAY) <= today

    val isValid: Boolean
        get() = name.isNotBlank() &&
            (type == CycleType.STANDARD || totalWeeks > 0) &&
            !startsInThePast
}

sealed interface CreateCycleUiEvent {
    data class Created(val cycleId: Long, val weekCount: Int) : CreateCycleUiEvent
}

sealed interface CreateCycleEvent {
    data class NameChanged(val name: String) : CreateCycleEvent
    data class TypeChanged(val type: CycleType) : CreateCycleEvent
    data class TotalWeeksChanged(val weeks: Int) : CreateCycleEvent
    data class OnCompleteActionChanged(val action: CycleCompleteAction) : CreateCycleEvent
    data class StartModeChanged(val mode: CycleStartMode) : CreateCycleEvent
    data class StartYearChanged(val year: Int) : CreateCycleEvent
    data class StartWeekChanged(val week: Int) : CreateCycleEvent
    object Save : CreateCycleEvent
}

class CreateCycleViewModel(
    private val cycleRepository: CycleRepository,
    private val createCycle: CreateCycleUseCase,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today = clock.todayIn(zone)
    private val currentWeek = isoWeekOf(today)

    private val _state = MutableStateFlow(
        CreateCycleState(
            today = today,
            startYear = currentWeek.year,
            startWeek = currentWeek.week,
        )
    )
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
            is CreateCycleEvent.StartModeChanged -> _state.value = _state.value.copy(startMode = event.mode)
            is CreateCycleEvent.StartYearChanged -> {
                // A year switch can leave the week out of range (53-week year → 52-week year).
                val week = _state.value.startWeek.coerceAtMost(isoWeeksInYear(event.year))
                _state.value = _state.value.copy(startYear = event.year, startWeek = week)
            }
            is CreateCycleEvent.StartWeekChanged ->
                _state.value = _state.value.copy(
                    startWeek = event.week.coerceIn(1, isoWeeksInYear(_state.value.startYear))
                )
            is CreateCycleEvent.Save -> save()
        }
    }

    private fun save() {
        viewModelScope.launch {
            val current = _state.value
            if (!current.isValid) return@launch
            val result = createCycle(
                name = current.name,
                type = current.type,
                totalWeeks = current.totalWeeks,
                onCompleteAction = current.onCompleteAction,
                startDate = current.startDate,
            )
            if (result is Data.Success) {
                val weekCount = if (current.type == CycleType.STANDARD) 1 else current.totalWeeks
                _uiEvents.send(CreateCycleUiEvent.Created(result.data, weekCount))
            }
        }
    }
}
