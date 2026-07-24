package com.yhdista.dosetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.logEach
import com.yhdista.dosetracker.core.describe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.DoseRepository
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
    private val medicationRepository: MedicationRepository,
    private val doseRepository: DoseRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryState> = combine(
        doseRepository.getAllDoses(),
        medicationRepository.getMedications()
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
    }.logEach("HistoryViewModel") { state ->
        val dosesDesc = state.dosesWithMeds.describe { items -> "count=${items.size}" }
        "State updated: dosesWithMeds=$dosesDesc"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryState()
    )

}
