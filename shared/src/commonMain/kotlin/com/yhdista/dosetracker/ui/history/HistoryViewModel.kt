package com.yhdista.dosetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.*

data class HistoryState(
    val dosesWithMeds: Data<List<DoseWithMedication>> = Data.Loading
)

data class DoseWithMedication(
    val dose: Dose,
    val medication: Medication?
)

class HistoryViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryState> = combine(
        repository.getAllDoses(),
        repository.getMedications()
    ) { dosesResult, medsResult ->
        if (dosesResult is Data.Success && medsResult is Data.Success) {
            val medsMap = medsResult.data.associateBy { it.id }
            val combined = dosesResult.data.map { dose ->
                DoseWithMedication(dose, medsMap[dose.medicationId])
            }
            HistoryState(Data.Success(combined))
        } else if (dosesResult is Data.Error) {
            HistoryState(Data.Error(dosesResult.message))
        } else if (medsResult is Data.Error) {
            HistoryState(Data.Error(medsResult.message))
        } else {
            HistoryState(Data.Loading)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryState()
    )

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val dosesDesc = state.dosesWithMeds.describe { items ->
                    items.joinToString(prefix = "[", postfix = "]") { item ->
                        val timeStr = try {
                            item.dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace("T", " ").substring(0, 16)
                        } catch (e: Exception) {
                            "????-??-?? ??:??"
                        }
                        "${item.dose.medicationName} ${item.dose.amount ?: ""}${item.dose.unit ?: ""} @ $timeStr (${item.dose.status})"
                    }
                }
                com.yhdista.dosetracker.core.AppLogger.d("HistoryViewModel", "State updated: dosesWithMeds=$dosesDesc")
            }
        }
    }
}
