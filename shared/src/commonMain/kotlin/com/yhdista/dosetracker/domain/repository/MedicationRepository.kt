package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface MedicationRepository {
    fun getMedications(): Flow<Data<List<Medication>>>
    fun getMedicationById(id: Long): Flow<Data<Medication>>
    suspend fun insertMedication(medication: Medication): Data<Long>
    suspend fun updateMedication(medication: Medication): Data<Unit>
    suspend fun deleteMedication(medication: Medication): Data<Unit>

    fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>>
    fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>>
    fun getAllDoses(): Flow<Data<List<Dose>>>
    suspend fun insertDose(dose: Dose): Data<Long>
    suspend fun updateDose(dose: Dose): Data<Unit>

    fun searchMedications(query: String): Flow<Data<List<Medication>>>
}
