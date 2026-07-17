package com.yhdista.dosetracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogState(
    val medications: Data<List<Medication>> = Data.Loading,
    val searchQuery: String = ""
)

sealed interface CatalogEvent {
    data class Search(val query: String) : CatalogEvent
    data class AddMedication(
        val name: String,
        val dosage: String,
        val unit: String,
        val frequency: String
    ) : CatalogEvent
}

@HiltViewModel
class MedicationCatalogViewModel @Inject constructor(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<CatalogState> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.getMedications()
            } else {
                repository.searchMedications(query)
            }
        }
        .map { result ->
            CatalogState(medications = result, searchQuery = _searchQuery.value)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CatalogState()
        )

    fun onEvent(event: CatalogEvent) {
        when (event) {
            is CatalogEvent.Search -> _searchQuery.value = event.query
            is CatalogEvent.AddMedication -> addMedication(event)
        }
    }

    private fun addMedication(event: CatalogEvent.AddMedication) {
        viewModelScope.launch {
            val dosageValue = event.dosage.toDoubleOrNull() ?: 0.0
            repository.insertMedication(
                Medication(
                    name = event.name,
                    dosage = dosageValue,
                    unit = event.unit,
                    frequency = event.frequency
                )
            )
        }
    }
}
