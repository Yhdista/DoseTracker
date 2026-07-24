package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.repository.DoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

internal class DoseRepositoryImpl(
    private val doseDao: DoseDao
) : DoseRepository {

    override fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>> {
        return doseDao.getDosesForMedication(medicationId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) }
            .withLoadingAndErrors("Failed to fetch doses")
    }

    override fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone).toEpochMilliseconds()

        return doseDao.getDosesInTimeRange(startOfDay, endOfDay)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) }
            .withLoadingAndErrors("Failed to fetch doses for date")
    }

    override fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfWeek = weekStart.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfWeek = weekStart.plus(7, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesInTimeRange(startOfWeek, endOfWeek)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) }
            .withLoadingAndErrors("Failed to fetch doses for week")
    }

    override fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startMillis = start.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMillis = endExclusive.atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesForMedicationInTimeRange(medicationId, startMillis, endMillis)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) }
            .withLoadingAndErrors("Failed to fetch doses for medication in range")
    }

    override fun getDosesInRange(start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startMillis = start.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMillis = endExclusive.atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesInTimeRange(startMillis, endMillis)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) }
            .withLoadingAndErrors("Failed to fetch doses in range")
    }

    override fun getAllDoses(): Flow<Data<List<Dose>>> {
        return doseDao.getAllDoses()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) }
            .withLoadingAndErrors("Failed to fetch all doses")
    }

    override suspend fun getDoseOnce(id: Long): Dose? {
        return doseDao.getDoseWithMedicationById(id)?.toDomain()
    }

    override fun getDoseById(id: Long): Flow<Data<Dose>> {
        return doseDao.getDoseWithMedicationByIdFlow(id)
            .map { entity ->
                if (entity != null) Data.Success(entity.toDomain())
                else Data.Error("Dose not found")
            }
            .withLoadingAndErrors("Failed to fetch dose")
    }

    override suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): Dose? {
        return doseDao.getDoseForSchedule(scheduleId, timestamp)?.toDomain()
    }

    override suspend fun getDoseForScheduleOnDate(scheduleId: Long, date: LocalDate): Dose? {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone)
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone)
        return doseDao.getDoseForScheduleOnDate(scheduleId, startOfDay, endOfDay)?.toDomain()
    }

    override suspend fun insertDose(dose: Dose): Data<Long> {
        return try {
            val id = doseDao.insertDose(dose.toEntity())
            AppLogger.i("Database", "Inserted dose: id=$id, medicationId=${dose.medicationId}, timestamp=${dose.timestamp}")
            Data.Success(id)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to insert dose for medicationId=${dose.medicationId}", e)
            Data.Error("Failed to insert dose", e)
        }
    }

    override suspend fun updateDose(dose: Dose): Data<Unit> {
        return try {
            doseDao.updateDose(dose.toEntity())
            AppLogger.i("Database", "Updated dose: id=${dose.id}, status=${dose.status}, timestamp=${dose.timestamp}")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to update dose id=${dose.id}", e)
            Data.Error("Failed to update dose", e)
        }
    }

    override suspend fun markPendingDosesMissedBefore(cutoff: Instant): Data<Int> {
        return try {
            val count = doseDao.markPendingDosesMissedBefore(cutoff)
            if (count > 0) AppLogger.i("Database", "Swept $count overdue PENDING doses to MISSED (cutoff=$cutoff)")
            Data.Success(count)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to sweep overdue doses", e)
            Data.Error("Failed to sweep overdue doses", e)
        }
    }
}
