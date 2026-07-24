package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class MedicationRepositoryImpl(
    private val medicationDao: MedicationDao
) : MedicationRepository {

    override fun getMedications(): Flow<Data<List<Medication>>> {
        return medicationDao.getAllMedications()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Medication>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch medications", e)
                emit(Data.Error("Failed to fetch medications", e))
            }
    }

    override fun getMedicationById(id: Long): Flow<Data<Medication>> {
        return medicationDao.getMedicationById(id)
            .map { entity ->
                if (entity != null) Data.Success(entity.toDomain()) as Data<Medication>
                else Data.Error("Medication not found")
            }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch medication $id", e)
                emit(Data.Error("Failed to fetch medication", e))
            }
    }

    override suspend fun getMedicationOnce(id: Long): Medication? {
        return medicationDao.getMedicationById(id).first()?.toDomain()
    }

    override suspend fun insertMedication(medication: Medication): Data<Long> {
        return try {
            val id = medicationDao.insertMedication(medication.toEntity())
            AppLogger.i("Database", "Inserted medication: id=$id, name='${medication.name}'")
            Data.Success(id)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to insert medication '${medication.name}'", e)
            Data.Error("Failed to insert medication", e)
        }
    }

    override suspend fun updateMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.updateMedication(medication.toEntity())
            AppLogger.i("Database", "Updated medication: id=${medication.id}, name='${medication.name}'")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to update medication id=${medication.id}", e)
            Data.Error("Failed to update medication", e)
        }
    }

    override suspend fun deleteMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.deleteMedication(medication.toEntity())
            AppLogger.i("Database", "Deleted medication: id=${medication.id}, name='${medication.name}'")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to delete medication id=${medication.id}", e)
            Data.Error("Failed to delete medication", e)
        }
    }

    override fun searchMedications(query: String): Flow<Data<List<Medication>>> {
        return medicationDao.searchMedications(query)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Medication>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to search medications with query '$query'", e)
                emit(Data.Error("Failed to search medications", e))
            }
    }
}
