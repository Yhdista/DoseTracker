package com.yhdista.dosetracker.ui.medicationdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

data class MedicationDetailState(
    val medication: Data<Medication> = Data.Loading,
    val schedules: Data<List<ReminderSchedule>> = Data.Loading
)

sealed interface MedicationDetailEvent {
    data class AddSchedule(val minutesOfDay: Int, val daysOfWeek: Set<DayOfWeek>) : MedicationDetailEvent
    data class DeleteSchedule(val schedule: ReminderSchedule) : MedicationDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationDetailViewModel(
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler,
    private val doseGenerator: DoseGenerator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)

    val uiState: StateFlow<MedicationDetailState> = medicationIdFlow
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                repository.getMedicationById(id),
                repository.getSchedulesForMedication(id)
            ) { medication, schedules -> MedicationDetailState(medication, schedules) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicationDetailState()
        )

    fun setMedicationId(id: Long) {
        if (savedStateHandle.get<Long>("medicationId") == null) {
            savedStateHandle["medicationId"] = id
        }
    }

    fun onEvent(event: MedicationDetailEvent) {
        when (event) {
            is MedicationDetailEvent.AddSchedule -> addSchedule(event)
            is MedicationDetailEvent.DeleteSchedule -> deleteSchedule(event.schedule)
        }
    }

    private fun addSchedule(event: MedicationDetailEvent.AddSchedule) {
        val medicationId = savedStateHandle.get<Long>("medicationId") ?: return
        viewModelScope.launch {
            repository.insertSchedule(
                ReminderSchedule(
                    medicationId = medicationId,
                    minutesOfDay = event.minutesOfDay,
                    daysOfWeek = WeekDays.toBitmask(event.daysOfWeek)
                )
            )
            doseGenerator.runForToday()
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            val doses = repository.getDosesForMedication(schedule.medicationId).first { it !is Data.Loading }
            if (doses is Data.Success) {
                doses.data
                    .filter { it.scheduleId == schedule.id && it.status == DoseStatus.PENDING }
                    .forEach {
                        scheduler.cancelReminder(it.id)
                        scheduler.cancelMissedTimeout(it.id)
                    }
            }
            repository.deleteSchedule(schedule)
        }
    }
}
