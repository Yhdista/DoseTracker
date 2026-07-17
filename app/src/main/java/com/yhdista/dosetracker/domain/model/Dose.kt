package com.yhdista.dosetracker.domain.model

import java.time.Instant

data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val medicationName: String = "", // Added medication name
    val timestamp: Instant,
    val amount: Double? = null,
    val unit: String? = null,
    val status: DoseStatus = DoseStatus.PENDING
)

enum class DoseStatus {
    TAKEN,
    MISSED,
    SKIPPED,
    PENDING
}
