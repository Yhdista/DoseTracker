package com.yhdista.dosetracker.ui.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.DoseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class ConfirmDoseState(
    val dose: Data<Dose> = Data.Loading,
    val amount: String = "",
    val time: LocalDateTime? = null,
    val isSuccess: Boolean = false,
    val error: String? = null
)

sealed interface ConfirmDoseEvent {
    data class UpdateAmount(val amount: String) : ConfirmDoseEvent
    data object Save : ConfirmDoseEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmDoseViewModel(
    private val doseRepository: DoseRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val doseIdFlow = savedStateHandle.getStateFlow<Long?>("doseId", null)

    private val _state = MutableStateFlow(ConfirmDoseState())
    val state = _state.asStateFlow()

    init {
        doseIdFlow
            .filterNotNull()
            .flatMapLatest { id -> doseRepository.getDoseById(id) }
            .onEach { result ->
                _state.update { state ->
                    state.copy(
                        dose = result,
                        amount = if (result is Data.Success && state.amount.isEmpty())
                            result.data.amount?.toString() ?: state.amount
                        else state.amount,
                        time = if (result is Data.Success && state.time == null)
                            result.data.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                        else state.time
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            state.collect { s ->
                val doseDesc = s.dose.describe { dose ->
                    val timeStr = try {
                        dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace("T", " ").substring(0, 16)
                    } catch (e: Exception) {
                        "????-??-?? ??:??"
                    }
                    "${dose.medicationName} ${dose.amount ?: ""}${dose.unit ?: ""} scheduled @ $timeStr, status=${dose.status}"
                }
                com.yhdista.dosetracker.core.AppLogger.d("ConfirmDoseViewModel", "State updated: dose=$doseDesc, amount=${s.amount}, time=${s.time}, isSuccess=${s.isSuccess}, error=${s.error}")
            }
        }
    }

    fun setDoseId(id: Long) {
        if (savedStateHandle.get<Long>("doseId") == null) {
            savedStateHandle["doseId"] = id
        }
    }

    fun onEvent(event: ConfirmDoseEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("ConfirmDoseViewModel", "onEvent: $event")
        when (event) {
            is ConfirmDoseEvent.UpdateAmount -> _state.update { it.copy(amount = event.amount) }
            is ConfirmDoseEvent.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        val dose = (current.dose as? Data.Success)?.data ?: return
        val time = current.time ?: return

        viewModelScope.launch {
            val result = doseRepository.updateDose(
                dose.copy(
                    amount = current.amount.toDoubleOrNull(),
                    timestamp = time.toInstant(TimeZone.currentSystemDefault()),
                    status = DoseStatus.TAKEN
                )
            )
            when (result) {
                is Data.Success -> _state.update { it.copy(isSuccess = true) }
                is Data.Error -> _state.update { it.copy(error = result.message) }
                else -> Unit
            }
        }
    }
}
