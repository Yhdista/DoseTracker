package com.yhdista.dosetracker.domain.model

import kotlinx.datetime.Instant

data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long? = null,
    val cycleId: Long? = null,
    val medicationName: String = "",
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
