package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import com.yhdista.dosetracker.domain.model.Medication

fun MedicationEntity.toDomain(): Medication {
    return Medication(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit
    )
}

fun Medication.toEntity(): MedicationEntity {
    return MedicationEntity(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit
    )
}
