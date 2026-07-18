package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.dao.DoseWithMedication
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.domain.model.Dose

fun DoseEntity.toDomain(medicationName: String = ""): Dose {
    return Dose(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        medicationName = medicationName,
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}

fun DoseWithMedication.toDomain(): Dose {
    return dose.toDomain(medicationName)
}

fun Dose.toEntity(): DoseEntity {
    return DoseEntity(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}
