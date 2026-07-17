package com.yhdista.dosetracker.domain.model

data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: String,
    val frequency: String, // e.g., "Daily", "Twice a day"
    val reminderTime: String? = null // e.g., "08:00"
)
