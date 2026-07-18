package com.yhdista.dosetracker.reminder

import kotlinx.datetime.Instant

interface DoseReminderScheduler {
    fun scheduleReminder(doseId: Long, at: Instant)
    fun cancelReminder(doseId: Long)
}
