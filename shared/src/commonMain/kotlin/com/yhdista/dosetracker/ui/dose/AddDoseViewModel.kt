package com.yhdista.dosetracker.ui.dose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class AddDoseState(
    val medication: Data<Medication> = Data.Loading,
    val amount: String = "",
    val time: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val isSuccess: Boolean = false,
    val error: String? = null
)

sealed interface AddDoseEvent {
    data class UpdateAmount(val amount: String) : AddDoseEvent
    data class UpdateTime(val time: LocalDateTime) : AddDoseEvent
    data object SaveDose : AddDoseEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddDoseViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)

    private val _state = MutableStateFlow(AddDoseState())
    val state = _state.asStateFlow()

    init {
        medicationIdFlow
            .filterNotNull()
            .flatMapLatest { id -> repository.getMedicationById(id) }
            .onEach { result ->
                _state.update { state ->
                    state.copy(
                        medication = result,
                        amount = if (result is Data.Success) result.data.dosage.toString() else state.amount
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            state.collect { s ->
                val medDesc = s.medication.describe { med -> "name='${med.name}', dosage=${med.dosage} ${med.unit.symbol}" }
                com.yhdista.dosetracker.core.AppLogger.d(
                    "AddDoseViewModel",
                    "State updated: medication=$medDesc, amount=${s.amount}, time=${s.time}, isSuccess=${s.isSuccess}, error=${s.error}"
                )
            }
        }
    }

    fun setMedicationId(id: Long) {
        if (savedStateHandle.get<Long>("medicationId") == null) {
            savedStateHandle["medicationId"] = id
        }
    }

    fun onEvent(event: AddDoseEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("AddDoseViewModel", "onEvent: $event")
        when (event) {
            is AddDoseEvent.UpdateAmount -> _state.update { it.copy(amount = event.amount) }
            is AddDoseEvent.UpdateTime -> _state.update { it.copy(time = event.time) }
            is AddDoseEvent.SaveDose -> saveDose()
        }
    }

    private fun saveDose() {
        val currentState = _state.value
        val medication = (currentState.medication as? Data.Success)?.data ?: return

        viewModelScope.launch {
            val dose = Dose(
                medicationId = medication.id,
                timestamp = currentState.time.toInstant(TimeZone.currentSystemDefault()),
                amount = currentState.amount.toDoubleOrNull(),
                unit = medication.unit.symbol,
                status = DoseStatus.TAKEN
            )
            when (val result = repository.insertDose(dose)) {
                is Data.Success -> _state.update { it.copy(isSuccess = true) }
                is Data.Error -> _state.update { it.copy(error = result.message) }
                else -> Unit
            }
        }
    }
}
