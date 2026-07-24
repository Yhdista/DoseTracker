package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.core.logEach
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.PlannedDose
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.domain.usecase.PlanAgendaUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
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
    val today: LocalDate? = null,
    val dosesInWindow: Data<List<Dose>> = Data.Loading,
    val activeCycle: Data<Cycle?> = Data.Loading,
    val completedCycles: Data<List<Cycle>> = Data.Loading,
    val futureCycles: List<Cycle> = emptyList(),
    /** Projected doses for the days ahead, which have no generated rows yet. */
    val plannedInWindow: Map<LocalDate, List<PlannedDose>> = emptyMap(),
    val selectedDoseId: Long? = null,
)

sealed interface TodayEvent {
    data class ToggleDoseStatus(val dose: Dose) : TodayEvent
    data class SelectDose(val id: Long?) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val doseRepository: DoseRepository,
    private val cycleRepository: CycleRepository,
    private val scheduleRepository: ScheduleRepository,
    private val planAgenda: PlanAgendaUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _selectedDoseId = savedStateHandle.getStateFlow<Long?>("selectedDoseId", null)

    /**
     * Emits the current date and re-emits after each local midnight, so the agenda window
     * and dose query follow the calendar instead of freezing at construction time.
     */
    private val todayFlow = flow {
        while (true) {
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val today = Clock.System.todayIn(zone)
            emit(today)
            val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            delay(nextMidnight - now)
        }
    }.distinctUntilChanged()

    private val dosesInWindowFlow = todayFlow.flatMapLatest { today ->
        doseRepository.getDosesInRange(today, today.plus(AGENDA_WINDOW_DAYS, DateTimeUnit.DAY))
    }

    /**
     * Projection of the days ahead. Recomputed whenever the day rolls over, the schedules or their
     * period times change, or the active cycle changes — anything that could move a future dose.
     */
    private val plannedInWindowFlow = combine(
        todayFlow,
        scheduleRepository.getAllSchedules(),
        scheduleRepository.getPeriodTimes(),
        cycleRepository.getActiveCycle(),
    ) { today, _, _, _ -> today }
        .mapLatest { today -> planAgenda(today, AGENDA_WINDOW_DAYS) }

    /** Resolve the chain of upcoming cycles (nextCycleId) so the timeline can draw them ahead of today. */
    private val futureCyclesFlow = cycleRepository.getActiveCycle().mapLatest { data ->
        val active = (data as? Data.Success)?.data
        val resolved = mutableListOf<Cycle>()
        val guard = mutableSetOf<Long>()
        var nextId = active?.nextCycleId
        while (nextId != null && guard.add(nextId)) {
            val chained = cycleRepository.getCycleById(nextId) ?: break
            resolved += chained
            nextId = chained.nextCycleId
        }
        resolved
    }

    val uiState: StateFlow<TodayState> = combine(
        combine(todayFlow, dosesInWindowFlow, plannedInWindowFlow, ::Triple),
        cycleRepository.getActiveCycle(),
        cycleRepository.getCompletedCycles(),
        futureCyclesFlow,
        _selectedDoseId
    ) { (today, doses, planned), activeCycle, completedCycles, futureCycles, selectedId ->
        TodayState(
            today = today,
            dosesInWindow = doses,
            activeCycle = activeCycle,
            completedCycles = completedCycles,
            futureCycles = futureCycles,
            plannedInWindow = planned,
            selectedDoseId = selectedId
        )
    }.logEach("TodayViewModel") { state ->
        val dosesDesc = state.dosesInWindow.describe { doses -> "count=${doses.size}" }
        val plannedDesc = "days=${state.plannedInWindow.size}, count=${state.plannedInWindow.values.sumOf { it.size }}"
        val cycleDesc = state.activeCycle.describe { cycle -> "name=${cycle?.name ?: "none"}" }
        val futureDesc = state.futureCycles.joinToString(prefix = "[", postfix = "]") { it.name }
        "State updated: today=${state.today}, doses=$dosesDesc, planned=$plannedDesc, activeCycle=$cycleDesc, futureCycles=$futureDesc, selectedDoseId=${state.selectedDoseId}"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayState()
    )

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
            doseRepository.updateDose(dose.copy(status = newStatus))
        }
    }

    private fun selectDose(id: Long?) {
        savedStateHandle["selectedDoseId"] = id
    }
}
