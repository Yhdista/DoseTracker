package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.WeekDays
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
    val periodTimes: Data<Map<String, Int>> = Data.Loading,
    val defaultTimeType: TimeType = TimeType.PERIOD,
    val cycle: com.yhdista.dosetracker.domain.model.Cycle? = null
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
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val cycleRepository: CycleRepository,
    private val settingsRepository: SettingsRepository,
    private val doseGenerator: DoseGenerator,
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CycleWeekEditorState()
        )

    init {
        viewModelScope.launch {
            cycleIdFlow.filterNotNull().collect { id ->
                _cycle.value = cycleRepository.getCycleById(id)
            }
        }
        viewModelScope.launch {
            uiState.collect { state ->
                val cycleDesc = state.cycle?.let { "Cycle(id=${it.id}, name='${it.name}', type=${it.type}, totalWeeks=${it.totalWeeks})" } ?: "null"
                val schedulesDesc = when (val s = state.schedules) {
                    is Data.Success -> {
                        val listStr = s.data.joinToString(prefix = "[", postfix = "]") { sch ->
                            val timeStr = if (sch.timeType == "PERIOD") sch.dayPeriod else "${sch.minutesOfDay / 60}:${(sch.minutesOfDay % 60).toString().padStart(2, '0')}"
                            "Schedule(id=${sch.id}, medId=${sch.medicationId}, type=${sch.scheduleType}, time=$timeStr, enabled=${sch.enabled})"
                        }
                        "Success($listStr)"
                    }
                    is Data.Error -> "Error('${s.message}')"
                    Data.Loading -> "Loading"
                }
                val medicationsDesc = when (val m = state.medications) {
                    is Data.Success -> {
                        val listStr = m.data.joinToString(prefix = "[", postfix = "]") { med ->
                            "${med.name} (${med.dosage} ${med.unit.symbol})"
                        }
                        "Success($listStr)"
                    }
                    is Data.Error -> "Error('${m.message}')"
                    Data.Loading -> "Loading"
                }
                val periodTimesDesc = state.periodTimes.describe { it.toString() }
                com.yhdista.dosetracker.core.AppLogger.d(
                    "CycleWeekEditorViewModel",
                    "State updated: weekId=${state.weekId}, weekIndex=${weekIndexFlow.value}, cycle=$cycleDesc, schedules=$schedulesDesc, medications=$medicationsDesc, periodTimes=$periodTimesDesc, defaultTimeType=${state.defaultTimeType}"
                )
            }
        }
    }

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
            scheduleRepository.insertSchedule(
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
            scheduleRepository.updateSchedule(schedule)
            doseGenerator.runForToday()
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            scheduleRepository.deleteSchedule(schedule)
            doseGenerator.runForToday()
        }
    }
}
