package com.yhdista.dosetracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.MedicationUnit
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CatalogState(
    val medications: Data<List<Medication>> = Data.Loading,
    val searchQuery: String = "",
    val showOnlyActive: Boolean = false,
    val activeMedicationIds: Set<Long> = emptySet()
)

sealed interface CatalogEvent {
    data class Search(val query: String) : CatalogEvent
    data class AddMedication(
        val name: String,
        val dosage: String,
        val unit: String
    ) : CatalogEvent
    data class ToggleOnlyActive(val onlyActive: Boolean) : CatalogEvent
}

class MedicationCatalogViewModel(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showOnlyActive = MutableStateFlow(false)
    val showOnlyActive = _showOnlyActive.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<CatalogState> = combine(
        _searchQuery.debounce(300),
        _showOnlyActive,
        scheduleRepository.getAllSchedules()
    ) { query, onlyActive, schedulesResult ->
        Triple(query, onlyActive, schedulesResult)
    }.flatMapLatest { (query, onlyActive, schedulesResult) ->
        val medsFlow = if (query.isEmpty()) {
            medicationRepository.getMedications()
        } else {
            medicationRepository.searchMedications(query)
        }
        medsFlow.map { medsResult ->
            val activeMedIds = if (schedulesResult is Data.Success) {
                schedulesResult.data.filter { it.enabled }.map { it.medicationId }.toSet()
            } else {
                emptySet()
            }
            val filteredMedsResult = if (onlyActive && medsResult is Data.Success) {
                Data.Success(medsResult.data.filter { it.id in activeMedIds })
            } else {
                medsResult
            }
            CatalogState(
                medications = filteredMedsResult,
                searchQuery = query,
                showOnlyActive = onlyActive,
                activeMedicationIds = activeMedIds
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogState()
    )

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val medsDesc = state.medications.describe { meds ->
                    meds.joinToString(prefix = "[", postfix = "]") { med ->
                        "${med.name} (${med.dosage} ${med.unit.symbol})"
                    }
                }
                com.yhdista.dosetracker.core.AppLogger.d("MedicationCatalogViewModel", "State updated: medications=$medsDesc, searchQuery='${state.searchQuery}', showOnlyActive=${state.showOnlyActive}")
            }
        }
    }

    fun onEvent(event: CatalogEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("MedicationCatalogViewModel", "onEvent: $event")
        when (event) {
            is CatalogEvent.Search -> _searchQuery.value = event.query
            is CatalogEvent.AddMedication -> addMedication(event)
            is CatalogEvent.ToggleOnlyActive -> _showOnlyActive.value = event.onlyActive
        }
    }

    private fun addMedication(event: CatalogEvent.AddMedication) {
        viewModelScope.launch {
            val dosageValue = event.dosage.toDoubleOrNull() ?: 0.0
            medicationRepository.insertMedication(
                Medication(
                    name = event.name,
                    dosage = dosageValue,
                    unit = MedicationUnit.fromSymbol(event.unit)
                )
            )
        }
    }
}
