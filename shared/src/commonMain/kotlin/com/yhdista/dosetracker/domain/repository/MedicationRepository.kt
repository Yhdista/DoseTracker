package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

interface MedicationRepository {
    fun getMedications(): Flow<Data<List<Medication>>>
    fun getMedicationById(id: Long): Flow<Data<Medication>>
    suspend fun getMedicationOnce(id: Long): Medication?
    suspend fun insertMedication(medication: Medication): Data<Long>
    suspend fun updateMedication(medication: Medication): Data<Unit>
    suspend fun deleteMedication(medication: Medication): Data<Unit>
    fun searchMedications(query: String): Flow<Data<List<Medication>>>

    fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>>
    fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>>
    fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>>
    fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>>
    fun getDosesInRange(start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>>
    fun getAllDoses(): Flow<Data<List<Dose>>>
    suspend fun getDoseOnce(id: Long): Dose?
    fun getDoseById(id: Long): Flow<Data<Dose>>
    suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): Dose?
    suspend fun getDoseForScheduleOnDate(scheduleId: Long, date: LocalDate): Dose?
    suspend fun insertDose(dose: Dose): Data<Long>
    suspend fun updateDose(dose: Dose): Data<Unit>

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

    fun getActiveCycle(): Flow<Data<Cycle?>>
    suspend fun getActiveCycleOnce(): Cycle?
    suspend fun getStandardCycle(): Cycle?
    suspend fun getCycleById(id: Long): Cycle?
    fun getCompletedCycles(): Flow<Data<List<Cycle>>>
    suspend fun createCycle(cycle: Cycle): Data<Long>
    suspend fun updateCycle(cycle: Cycle): Data<Unit>
    fun getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>>
    suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek?
}
