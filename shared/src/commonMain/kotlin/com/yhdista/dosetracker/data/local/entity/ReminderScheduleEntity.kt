package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
data class ReminderScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int,
    val daysOfWeek: Int,
    val enabled: Boolean = true,
    val scheduleType: String = "WEEKDAYS",
    val intervalDays: Int = 1,
    val startDate: String? = null,
    val timeType: String = "EXACT",
    val dayPeriod: String? = null
)
