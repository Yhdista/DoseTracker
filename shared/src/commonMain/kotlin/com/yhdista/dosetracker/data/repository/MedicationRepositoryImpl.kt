package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

class MedicationRepositoryImpl(
    private val medicationDao: MedicationDao,
    private val doseDao: DoseDao,
    private val reminderScheduler: DoseReminderScheduler
) : MedicationRepository {

    override fun getMedications(): Flow<Data<List<Medication>>> {
        return medicationDao.getAllMedications()
            .map { entities ->
                val medications = entities.map { it.toDomain() }
                Data.Success(medications) as Data<List<Medication>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch medications", e)) }
    }

    override fun getMedicationById(id: Long): Flow<Data<Medication>> {
        return medicationDao.getMedicationById(id)
            .map { entity ->
                if (entity != null) {
                    Data.Success(entity.toDomain()) as Data<Medication>
                } else {
                    Data.Error("Medication not found")
                }
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch medication", e)) }
    }

    override suspend fun insertMedication(medication: Medication): Data<Long> {
        return try {
            val id = medicationDao.insertMedication(medication.toEntity())
            if (medication.reminderTime != null) {
                reminderScheduler.scheduleReminder(medication.copy(id = id))
            }
            Data.Success(id)
        } catch (e: Exception) {
            Data.Error("Failed to insert medication", e)
        }
    }

    override suspend fun updateMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.updateMedication(medication.toEntity())
            if (medication.reminderTime != null) {
                reminderScheduler.scheduleReminder(medication)
            } else {
                reminderScheduler.cancelReminder(medication.id)
            }
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update medication", e)
        }
    }

    override suspend fun deleteMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.deleteMedication(medication.toEntity())
            reminderScheduler.cancelReminder(medication.id)
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to delete medication", e)
        }
    }

    override fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>> {
        return doseDao.getDosesForMedication(medicationId)
            .map { entities ->
                val doses = entities.map { it.toDomain() }
                Data.Success(doses) as Data<List<Dose>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses", e)) }
    }

    override fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone).toEpochMilliseconds()

        return doseDao.getDosesInTimeRange(startOfDay, endOfDay)
            .map { entities ->
                val doses = entities.map { it.toDomain() }
                Data.Success(doses) as Data<List<Dose>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for date", e)) }
    }

    override fun getAllDoses(): Flow<Data<List<Dose>>> {
        return doseDao.getAllDoses()
            .map { entities ->
                val doses = entities.map { it.toDomain() }
                Data.Success(doses) as Data<List<Dose>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch all doses", e)) }
    }

    override suspend fun insertDose(dose: Dose): Data<Long> {
        return try {
            val id = doseDao.insertDose(dose.toEntity())
            Data.Success(id)
        } catch (e: Exception) {
            Data.Error("Failed to insert dose", e)
        }
    }

    override suspend fun updateDose(dose: Dose): Data<Unit> {
        return try {
            doseDao.updateDose(dose.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update dose", e)
        }
    }

    override fun searchMedications(query: String): Flow<Data<List<Medication>>> {
        return medicationDao.searchMedications(query)
            .map { entities ->
                val medications = entities.map { it.toDomain() }
                Data.Success(medications) as Data<List<Medication>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to search medications", e)) }
    }
}
