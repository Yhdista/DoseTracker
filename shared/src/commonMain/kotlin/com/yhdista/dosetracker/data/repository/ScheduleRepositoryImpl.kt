package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.PeriodTimeDao
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.local.entity.PeriodTimeEntity
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class ScheduleRepositoryImpl(
    private val scheduleDao: ReminderScheduleDao,
    private val periodTimeDao: PeriodTimeDao
) : ScheduleRepository {

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
}
