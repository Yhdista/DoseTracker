package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "period_times")
internal data class PeriodTimeEntity(
    @PrimaryKey
    val period: String, // "MORNING", "NOON", "EVENING", "NIGHT"
    val minutesOfDay: Int
)
