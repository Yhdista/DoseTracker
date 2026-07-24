package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.logEach
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.domain.usecase.ManageScheduleUseCase
import com.yhdista.dosetracker.reminder.WeekDays
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.ScheduleType
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

data class CycleWeekEditorState(
    val weekId: Long? = null,
    val schedules: Data<List<ReminderSchedule>> = Data.Loading,
    val medications: Data<List<Medication>> = Data.Loading,
    val periodTimes: Data<Map<DayPeriod, Int>> = Data.Loading,
    val defaultTimeType: TimeType = TimeType.PERIOD,
    val cycle: com.yhdista.dosetracker.domain.model.Cycle? = null
)

sealed interface CycleWeekEditorEvent {
    data class AddSchedule(
        val medicationId: Long,
        val minutesOfDay: Int,
        val daysOfWeek: Set<DayOfWeek>,
        val scheduleType: ScheduleType,
        val intervalDays: Int,
        val startDate: LocalDate?,
        val timeType: TimeType,
        val dayPeriod: DayPeriod?
    ) : CycleWeekEditorEvent

    data class UpdateSchedule(val schedule: ReminderSchedule) : CycleWeekEditorEvent
    data class DeleteSchedule(val schedule: ReminderSchedule) : CycleWeekEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CycleWeekEditorViewModel(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val cycleRepository: CycleRepository,
    private val settingsRepository: SettingsRepository,
    private val manageSchedule: ManageScheduleUseCase,
    cycleId: Long,
    weekIndex: Int,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    init {
        savedStateHandle["cycleId"] = cycleId
        savedStateHandle["weekIndex"] = weekIndex
    }

    private val cycleIdFlow = savedStateHandle.getStateFlow<Long?>("cycleId", null)
    private val weekIndexFlow = savedStateHandle.getStateFlow<Int?>("weekIndex", null)
    private val _cycle = MutableStateFlow<com.yhdista.dosetracker.domain.model.Cycle?>(null)

    val uiState: StateFlow<CycleWeekEditorState> = combine(cycleIdFlow, weekIndexFlow) { cycleId, weekIndex ->
        cycleId to weekIndex
    }
        .filter { (cycleId, weekIndex) -> cycleId != null && weekIndex != null }
        .map { (cycleId, weekIndex) -> cycleRepository.getCycleWeek(cycleId!!, weekIndex!!)?.id }
        .filterNotNull()
        .flatMapLatest { weekId ->
            combine(
                scheduleRepository.getSchedulesForCycleWeek(weekId),
                medicationRepository.getMedications(),
                scheduleRepository.getPeriodTimes(),
                settingsRepository.getDefaultTimeType(),
                _cycle
            ) { schedules, medications, periodTimes, defaultTimeType, cycle ->
                CycleWeekEditorState(weekId, schedules, medications, periodTimes, defaultTimeType, cycle)
            }
        }
        .logEach("CycleWeekEditorViewModel") { state ->
        val cycleDesc = state.cycle?.let { "Cycle(id=${it.id}, name='${it.name}')" } ?: "null"
        val schedulesDesc = state.schedules.describe { list -> "count=${list.size}" }
        "State updated: weekId=${state.weekId}, cycle=$cycleDesc, schedules=$schedulesDesc, defaultTimeType=${state.defaultTimeType}"
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CycleWeekEditorState()
        )


    fun onEvent(event: CycleWeekEditorEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("CycleWeekEditorViewModel", "onEvent: $event")
        when (event) {
            is CycleWeekEditorEvent.AddSchedule -> addSchedule(event)
            is CycleWeekEditorEvent.UpdateSchedule -> updateSchedule(event.schedule)
            is CycleWeekEditorEvent.DeleteSchedule -> deleteSchedule(event.schedule)
        }
    }

    private fun addSchedule(event: CycleWeekEditorEvent.AddSchedule) {
        viewModelScope.launch {
            val weekId = uiState.value.weekId ?: return@launch
            manageSchedule.addSchedule(
                ReminderSchedule(
                    medicationId = event.medicationId,
                    minutesOfDay = event.minutesOfDay,
                    daysOfWeek = WeekDays.toBitmask(event.daysOfWeek),
                    scheduleType = event.scheduleType,
                    intervalDays = event.intervalDays,
                    startDate = event.startDate,
                    timeType = event.timeType,
                    dayPeriod = event.dayPeriod,
                    cycleWeekId = weekId
                )
            )
        }
    }

    private fun updateSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            manageSchedule.updateSchedule(schedule)
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            manageSchedule.deleteSchedule(schedule)
        }
    }
}
