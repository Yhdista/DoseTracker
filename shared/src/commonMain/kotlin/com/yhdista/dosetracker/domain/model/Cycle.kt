package com.yhdista.dosetracker.domain.model

import kotlinx.datetime.LocalDate

enum class CycleType { NORMAL, STANDARD, POST }

enum class CycleStatus { DRAFT, ACTIVE, COMPLETED }

enum class CycleCompleteAction { TO_STANDARD, TO_POST, TO_NONE }

data class Cycle(
    val id: Long = 0,
    val name: String,
    val type: CycleType,
    val totalWeeks: Int? = null,
    val startDate: LocalDate,
    val status: CycleStatus,
    val onCompleteAction: CycleCompleteAction,
    val nextCycleId: Long? = null
)

data class CycleWeek(
    val id: Long = 0,
    val cycleId: Long,
    val weekIndex: Int
)
