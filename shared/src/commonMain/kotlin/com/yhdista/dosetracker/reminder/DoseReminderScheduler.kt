package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.Medication

interface DoseReminderScheduler {
    fun scheduleReminder(medication: Medication)
    fun cancelReminder(medicationId: Long)
}
