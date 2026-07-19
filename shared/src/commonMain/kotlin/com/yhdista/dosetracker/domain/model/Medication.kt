package com.yhdista.dosetracker.domain.model

enum class MedicationUnit(val symbol: String) {
    MG("mg"),
    MCG("mcg"),
    ML("ml"),
    G("g"),
    PCS("pcs");

    companion object {
        fun fromSymbol(symbol: String): MedicationUnit {
            return entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(symbol, ignoreCase = true) }
                ?: MG
        }
    }
}

data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: MedicationUnit
)

