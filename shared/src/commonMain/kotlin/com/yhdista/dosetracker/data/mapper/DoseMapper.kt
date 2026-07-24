package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.dao.DoseWithMedication
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.domain.model.Dose

internal fun DoseEntity.toDomain(medicationName: String = ""): Dose {
    return Dose(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        cycleId = cycleId,
        medicationName = medicationName,
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}

internal fun DoseWithMedication.toDomain(): Dose {
    return dose.toDomain(medicationName)
}

internal fun Dose.toEntity(): DoseEntity {
    return DoseEntity(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        cycleId = cycleId,
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}
