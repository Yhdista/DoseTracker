package com.yhdista.dosetracker.domain.model

enum class TimeType(val value: String) {
    EXACT("EXACT"),
    PERIOD("PERIOD");

    companion object {
        fun fromValue(value: String): TimeType {
            return entries.find { it.value == value } ?: EXACT
        }
    }
}
