package com.yhdista.dosetracker.domain.model

/** How a reminder schedule picks its days: fixed weekdays or an every-N-days interval. */
enum class ScheduleType {
    WEEKDAYS,
    INTERVAL,
}
