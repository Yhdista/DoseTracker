package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yhdista.dosetracker.domain.model.DoseStatus
import kotlinx.datetime.Instant

@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"]), Index(value = ["scheduleId", "timestamp"], unique = true)]
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    // No FK to reminder_schedules: deleting a schedule must not cascade-delete dose history.
    val scheduleId: Long?,
    val timestamp: Instant,
    val amount: Double?,
    val unit: String?,
    val status: DoseStatus,
    // No FK to cycles either, for the same reason: dose history must survive cycle deletion.
    val cycleId: Long? = null
)
