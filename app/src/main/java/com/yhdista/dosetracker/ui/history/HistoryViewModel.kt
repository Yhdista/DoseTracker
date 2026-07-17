package com.yhdista.dosetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HistoryState(
    val dosesWithMeds: Data<List<DoseWithMedication>> = Data.Loading
)

data class DoseWithMedication(
    val dose: Dose,
    val medication: Medication?
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
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
}
