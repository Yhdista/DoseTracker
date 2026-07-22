package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.CycleDao
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

import com.yhdista.dosetracker.data.local.dao.PeriodTimeDao
import com.yhdista.dosetracker.data.local.entity.PeriodTimeEntity

class MedicationRepositoryImpl(
    private val medicationDao: MedicationDao,
    private val doseDao: DoseDao,
    private val scheduleDao: ReminderScheduleDao,
    private val periodTimeDao: PeriodTimeDao,
    private val cycleDao: CycleDao
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

    override fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>> {
        return doseDao.getDosesForMedication(medicationId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch doses for medication $medicationId", e)
                emit(Data.Error("Failed to fetch doses", e))
            }
    }

    override fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone).toEpochMilliseconds()

        return doseDao.getDosesInTimeRange(startOfDay, endOfDay)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch doses for date $date", e)
                emit(Data.Error("Failed to fetch doses for date", e))
            }
    }

    override fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfWeek = weekStart.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfWeek = weekStart.plus(7, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesInTimeRange(startOfWeek, endOfWeek)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch doses for week starting $weekStart", e)
                emit(Data.Error("Failed to fetch doses for week", e))
            }
    }

    override fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startMillis = start.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMillis = endExclusive.atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesForMedicationInTimeRange(medicationId, startMillis, endMillis)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch doses for medication $medicationId in range $start..$endExclusive", e)
                emit(Data.Error("Failed to fetch doses for medication in range", e))
            }
    }

    override fun getAllDoses(): Flow<Data<List<Dose>>> {
        return doseDao.getAllDoses()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch all doses", e)
                emit(Data.Error("Failed to fetch all doses", e))
            }
    }

    override suspend fun getDoseOnce(id: Long): Dose? {
        return doseDao.getDoseWithMedicationById(id)?.toDomain()
    }

    override fun getDoseById(id: Long): Flow<Data<Dose>> {
        return doseDao.getDoseWithMedicationByIdFlow(id)
            .map { entity ->
                if (entity != null) Data.Success(entity.toDomain()) as Data<Dose>
                else Data.Error("Dose not found")
            }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch dose by id $id", e)
                emit(Data.Error("Failed to fetch dose", e))
            }
    }

    override suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): Dose? {
        return doseDao.getDoseForSchedule(scheduleId, timestamp)?.toDomain()
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

    override fun getSchedulesForMedication(medicationId: Long): Flow<Data<List<ReminderSchedule>>> {
        return scheduleDao.getSchedulesForMedication(medicationId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<ReminderSchedule>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch schedules for medication $medicationId", e)
                emit(Data.Error("Failed to fetch schedules", e))
            }
    }

    override fun getSchedulesForCycleWeek(cycleWeekId: Long): Flow<Data<List<ReminderSchedule>>> {
        return scheduleDao.getSchedulesForCycleWeek(cycleWeekId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<ReminderSchedule>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch schedules for cycle week $cycleWeekId", e)
                emit(Data.Error("Failed to fetch schedules for cycle week", e))
            }
    }

    override suspend fun getEnabledSchedules(): Data<List<ReminderSchedule>> {
        return try {
            Data.Success(scheduleDao.getAllEnabledSchedules().map { it.toDomain() })
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to fetch enabled schedules", e)
            Data.Error("Failed to fetch schedules", e)
        }
    }

    override fun getAllSchedules(): Flow<Data<List<ReminderSchedule>>> {
        return scheduleDao.getAllSchedulesFlow()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<ReminderSchedule>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch all schedules", e)
                emit(Data.Error("Failed to fetch all schedules", e))
            }
    }

    override suspend fun insertSchedule(schedule: ReminderSchedule): Data<Long> {
        return try {
            val id = scheduleDao.insertSchedule(schedule.toEntity())
            AppLogger.i("Database", "Inserted schedule: id=$id, medicationId=${schedule.medicationId}")
            Data.Success(id)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to insert schedule for medicationId=${schedule.medicationId}", e)
            Data.Error("Failed to insert schedule", e)
        }
    }

    override suspend fun deleteSchedule(schedule: ReminderSchedule): Data<Unit> {
        return try {
            scheduleDao.deleteSchedule(schedule.toEntity())
            AppLogger.i("Database", "Deleted schedule: id=${schedule.id}, medicationId=${schedule.medicationId}")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to delete schedule id=${schedule.id}", e)
            Data.Error("Failed to delete schedule", e)
        }
    }

    override suspend fun getDoseForScheduleOnDate(scheduleId: Long, date: LocalDate): Dose? {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone)
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone)
        return doseDao.getDoseForScheduleOnDate(scheduleId, startOfDay, endOfDay)?.toDomain()
    }

    override suspend fun updateSchedule(schedule: ReminderSchedule): Data<Unit> {
        return try {
            scheduleDao.updateSchedule(schedule.toEntity())
            AppLogger.i("Database", "Updated schedule: id=${schedule.id}, enabled=${schedule.enabled}")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to update schedule id=${schedule.id}", e)
            Data.Error("Failed to update schedule", e)
        }
    }

    override fun getPeriodTimes(): Flow<Data<Map<String, Int>>> {
        return periodTimeDao.getAllPeriodTimesFlow()
            .map { entities ->
                val map = entities.associate { it.period to it.minutesOfDay }
                Data.Success(map) as Data<Map<String, Int>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch period times", e)
                emit(Data.Error("Failed to fetch period times", e))
            }
     }

     override suspend fun getPeriodTimesOnce(): Map<String, Int> {
         return periodTimeDao.getAllPeriodTimes().associate { it.period to it.minutesOfDay }
     }

     override suspend fun updatePeriodTime(period: String, minutesOfDay: Int): Data<Unit> {
         return try {
             periodTimeDao.insertPeriodTime(PeriodTimeEntity(period, minutesOfDay))
             AppLogger.i("Database", "Updated period time: period=$period, minutesOfDay=$minutesOfDay")
             Data.Success(Unit)
         } catch (e: Exception) {
             AppLogger.e("Database", "Failed to update period time for period=$period", e)
             Data.Error("Failed to update period time", e)
         }
     }

    override fun getActiveCycle(): Flow<Data<Cycle?>> {
        return cycleDao.getActiveCycleFlow()
            .map { entity -> Data.Success(entity?.toDomain()) as Data<Cycle?> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch active cycle", e)
                emit(Data.Error("Failed to fetch active cycle", e))
            }
    }

    override suspend fun getActiveCycleOnce(): Cycle? {
        return cycleDao.getActiveCycleOnce()?.toDomain()
    }

    override suspend fun getStandardCycle(): Cycle? {
        return cycleDao.getStandardCycle()?.toDomain()
    }

    override suspend fun getCycleById(id: Long): Cycle? {
        return cycleDao.getCycleById(id)?.toDomain()
    }

    override fun getCompletedCycles(): Flow<Data<List<Cycle>>> {
        return cycleDao.getCompletedCycles()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Cycle>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch completed cycles", e)
                emit(Data.Error("Failed to fetch completed cycles", e))
            }
    }

    override suspend fun createCycle(cycle: Cycle): Data<Long> {
        return try {
            val cycleId = cycleDao.insertCycle(cycle.toEntity())
            val weekCount = cycle.totalWeeks ?: 1
            for (weekIndex in 0 until weekCount) {
                cycleDao.insertCycleWeek(CycleWeekEntity(cycleId = cycleId, weekIndex = weekIndex))
            }
            AppLogger.i("Database", "Created cycle: id=$cycleId, name='${cycle.name}', weeks=$weekCount")
            Data.Success(cycleId)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to create cycle '${cycle.name}'", e)
            Data.Error("Failed to create cycle", e)
        }
    }

    override suspend fun updateCycle(cycle: Cycle): Data<Unit> {
        return try {
            cycleDao.updateCycle(cycle.toEntity())
            AppLogger.i("Database", "Updated cycle: id=${cycle.id}, name='${cycle.name}', status=${cycle.status}")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to update cycle id=${cycle.id}", e)
            Data.Error("Failed to update cycle", e)
        }
    }

    override fun getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>> {
        return cycleDao.getWeeksForCycle(cycleId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<CycleWeek>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch cycle weeks for cycleId=$cycleId", e)
                emit(Data.Error("Failed to fetch cycle weeks", e))
            }
    }

    override suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek? {
        return cycleDao.getCycleWeek(cycleId, weekIndex)?.toDomain()
    }
}
