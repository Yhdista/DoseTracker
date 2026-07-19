package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.CycleEntity
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import kotlinx.datetime.LocalDate

fun CycleEntity.toDomain(): Cycle = Cycle(
    id = id,
    name = name,
    type = type,
    totalWeeks = totalWeeks,
    startDate = LocalDate.parse(startDate),
    status = status,
    onCompleteAction = onCompleteAction,
    nextCycleId = nextCycleId
)

fun Cycle.toEntity(): CycleEntity = CycleEntity(
    id = id,
    name = name,
    type = type,
    totalWeeks = totalWeeks,
    startDate = startDate.toString(),
    status = status,
    onCompleteAction = onCompleteAction,
    nextCycleId = nextCycleId
)

fun CycleWeekEntity.toDomain(): CycleWeek = CycleWeek(id = id, cycleId = cycleId, weekIndex = weekIndex)

fun CycleWeek.toEntity(): CycleWeekEntity = CycleWeekEntity(id = id, cycleId = cycleId, weekIndex = weekIndex)
