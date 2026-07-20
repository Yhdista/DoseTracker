package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

data class CycleWeekEditorState(
    val weekId: Long? = null,
    val schedules: Data<List<ReminderSchedule>> = Data.Loading,
    val medications: Data<List<Medication>> = Data.Loading,
    val periodTimes: Data<Map<String, Int>> = Data.Loading
)

sealed interface CycleWeekEditorEvent {
    data class AddSchedule(
        val medicationId: Long,
        val minutesOfDay: Int,
        val daysOfWeek: Set<DayOfWeek>,
        val scheduleType: String,
        val intervalDays: Int,
        val startDate: LocalDate?,
        val timeType: String,
        val dayPeriod: String?
    ) : CycleWeekEditorEvent

    data class UpdateSchedule(val schedule: ReminderSchedule) : CycleWeekEditorEvent
    data class DeleteSchedule(val schedule: ReminderSchedule) : CycleWeekEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CycleWeekEditorViewModel(
    private val repository: MedicationRepository,
    private val doseGenerator: DoseGenerator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cycleIdFlow = savedStateHandle.getStateFlow<Long?>("cycleId", null)
    private val weekIndexFlow = savedStateHandle.getStateFlow<Int?>("weekIndex", null)

    val uiState: StateFlow<CycleWeekEditorState> = combine(cycleIdFlow, weekIndexFlow) { cycleId, weekIndex ->
        cycleId to weekIndex
    }
        .filter { (cycleId, weekIndex) -> cycleId != null && weekIndex != null }
        .map { (cycleId, weekIndex) -> repository.getCycleWeek(cycleId!!, weekIndex!!)?.id }
        .filterNotNull()
        .flatMapLatest { weekId ->
            combine(
                repository.getSchedulesForCycleWeek(weekId),
                repository.getMedications(),
                repository.getPeriodTimes()
            ) { schedules, medications, periodTimes ->
                CycleWeekEditorState(weekId, schedules, medications, periodTimes)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CycleWeekEditorState()
        )

    fun setCycleWeek(cycleId: Long, weekIndex: Int) {
        if (savedStateHandle.get<Long>("cycleId") == null) {
            savedStateHandle["cycleId"] = cycleId
            savedStateHandle["weekIndex"] = weekIndex
        }
    }

    fun onEvent(event: CycleWeekEditorEvent) {
        when (event) {
            is CycleWeekEditorEvent.AddSchedule -> addSchedule(event)
            is CycleWeekEditorEvent.UpdateSchedule -> updateSchedule(event.schedule)
            is CycleWeekEditorEvent.DeleteSchedule -> deleteSchedule(event.schedule)
        }
    }

    private fun addSchedule(event: CycleWeekEditorEvent.AddSchedule) {
        viewModelScope.launch {
            val weekId = uiState.value.weekId ?: return@launch
            repository.insertSchedule(
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
            doseGenerator.runForToday()
        }
    }

    private fun updateSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
            doseGenerator.runForToday()
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule)
            doseGenerator.runForToday()
        }
    }
}
