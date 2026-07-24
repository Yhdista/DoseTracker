package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getSchedulesForMedication(medicationId: Long): Flow<Data<List<ReminderSchedule>>>
    fun getSchedulesForCycleWeek(cycleWeekId: Long): Flow<Data<List<ReminderSchedule>>>
    suspend fun getEnabledSchedules(): Data<List<ReminderSchedule>>
    fun getAllSchedules(): Flow<Data<List<ReminderSchedule>>>
    suspend fun insertSchedule(schedule: ReminderSchedule): Data<Long>
    suspend fun updateSchedule(schedule: ReminderSchedule): Data<Unit>
    suspend fun deleteSchedule(schedule: ReminderSchedule): Data<Unit>

    fun getPeriodTimes(): Flow<Data<Map<String, Int>>>
    suspend fun getPeriodTimesOnce(): Map<String, Int>
    suspend fun updatePeriodTime(period: String, minutesOfDay: Int): Data<Unit>
}
