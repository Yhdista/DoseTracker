package com.yhdista.dosetracker.reminder

import kotlinx.datetime.Instant

interface DoseReminderScheduler {
    fun scheduleReminder(doseId: Long, at: Instant)
    fun cancelReminder(doseId: Long)
    fun scheduleMissedTimeout(doseId: Long, at: Instant)
    fun cancelMissedTimeout(doseId: Long)

    companion object {
        const val MISSED_TIMEOUT_MINUTES = 120
    }
}
