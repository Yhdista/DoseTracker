package com.yhdista.dosetracker.ui.medicationdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.DoseReminderScheduler
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

data class MedicationDetailState(
    val medication: Data<Medication> = Data.Loading,
    val schedules: Data<List<ReminderSchedule>> = Data.Loading,
    val periodTimes: Data<Map<DayPeriod, Int>> = Data.Loading,
    val defaultTimeType: TimeType = TimeType.PERIOD
)

sealed interface MedicationDetailEvent {
    data class AddSchedule(
        val minutesOfDay: Int,
        val daysOfWeek: Set<DayOfWeek>,
        val scheduleType: ScheduleType,
        val intervalDays: Int,
        val startDate: LocalDate?,
        val timeType: TimeType,
        val dayPeriod: DayPeriod?
    ) : MedicationDetailEvent

    data class UpdateSchedule(val schedule: ReminderSchedule) : MedicationDetailEvent
    data class DeleteSchedule(val schedule: ReminderSchedule) : MedicationDetailEvent
    data class UpdatePeriodTime(val period: DayPeriod, val minutesOfDay: Int) : MedicationDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationDetailViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseRepository: DoseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: DoseReminderScheduler,
    private val doseGenerator: DoseGenerator,
    medicationId: Long,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    init {
        savedStateHandle["medicationId"] = medicationId
    }

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)

    val uiState: StateFlow<MedicationDetailState> = medicationIdFlow
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                medicationRepository.getMedicationById(id),
                scheduleRepository.getSchedulesForMedication(id),
                scheduleRepository.getPeriodTimes(),
                settingsRepository.getDefaultTimeType()
            ) { medication, schedules, periodTimes, defaultTimeType ->
                MedicationDetailState(medication, schedules, periodTimes, defaultTimeType)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicationDetailState()
        )

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val medDesc = state.medication.describe { med -> "name='${med.name}', dosage=${med.dosage} ${med.unit.symbol}" }
                val schedulesDesc = state.schedules.describe { schedules ->
                    schedules.joinToString(prefix = "[", postfix = "]") { sch ->
                        val timeStr = if (sch.timeType == TimeType.PERIOD) sch.dayPeriod.toString() else "${sch.minutesOfDay / 60}:${(sch.minutesOfDay % 60).toString().padStart(2, '0')}"
                        "Schedule(id=${sch.id}, type=${sch.scheduleType}, time=$timeStr, enabled=${sch.enabled})"
                    }
                }
                com.yhdista.dosetracker.core.AppLogger.d("MedicationDetailViewModel", "State updated: medication=$medDesc, schedules=$schedulesDesc, timeType=${state.defaultTimeType}")
            }
        }
    }

    fun onEvent(event: MedicationDetailEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("MedicationDetailViewModel", "onEvent: $event")
        when (event) {
            is MedicationDetailEvent.AddSchedule -> addSchedule(event)
            is MedicationDetailEvent.UpdateSchedule -> updateSchedule(event.schedule)
            is MedicationDetailEvent.DeleteSchedule -> deleteSchedule(event.schedule)
            is MedicationDetailEvent.UpdatePeriodTime -> updatePeriodTime(event.period, event.minutesOfDay)
        }
    }

    private fun addSchedule(event: MedicationDetailEvent.AddSchedule) {
        val medicationId = savedStateHandle.get<Long>("medicationId") ?: return
        viewModelScope.launch {
            scheduleRepository.insertSchedule(
                ReminderSchedule(
                    medicationId = medicationId,
                    minutesOfDay = event.minutesOfDay,
                    daysOfWeek = WeekDays.toBitmask(event.daysOfWeek),
                    scheduleType = event.scheduleType,
                    intervalDays = event.intervalDays,
                    startDate = event.startDate,
                    timeType = event.timeType,
                    dayPeriod = event.dayPeriod
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

    private fun updatePeriodTime(period: DayPeriod, minutesOfDay: Int) {
        viewModelScope.launch {
            scheduleRepository.updatePeriodTime(period, minutesOfDay)
            doseGenerator.runForToday()
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            val doses = doseRepository.getDosesForMedication(schedule.medicationId).first { it !is Data.Loading }
            if (doses is Data.Success) {
                doses.data
                    .filter { it.scheduleId == schedule.id && it.status == DoseStatus.PENDING }
                    .forEach {
                        scheduler.cancelReminder(it.id)
                        scheduler.cancelMissedTimeout(it.id)
                    }
            }
            scheduleRepository.deleteSchedule(schedule)
        }
    }
}
