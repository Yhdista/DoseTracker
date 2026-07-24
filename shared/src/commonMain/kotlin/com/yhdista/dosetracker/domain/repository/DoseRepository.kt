package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

interface DoseRepository {
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

    /** Marks every PENDING dose scheduled at or before [cutoff] as MISSED; returns the count. */
    suspend fun markPendingDosesMissedBefore(cutoff: Instant): Data<Int>
}
