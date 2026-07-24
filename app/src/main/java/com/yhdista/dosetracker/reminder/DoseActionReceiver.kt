package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import org.koin.core.context.GlobalContext

class DoseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN = "com.yhdista.dosetracker.ACTION_TAKEN"
        const val ACTION_SKIPPED = "com.yhdista.dosetracker.ACTION_SKIPPED"
        const val ACTION_SNOOZE = "com.yhdista.dosetracker.ACTION_SNOOZE"
        const val SNOOZE_MINUTES = 15
    }

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        com.yhdista.dosetracker.core.AppLogger.i("DoseActionReceiver", "onReceive: action=${intent.action}, doseId=$doseId")
        if (doseId == -1L) return

        runAsync("DoseActionReceiver") {
            val koin = GlobalContext.get()
            val repository = koin.get<MedicationRepository>()
            val scheduler = koin.get<DoseReminderScheduler>()
            val notificationHelper = koin.get<NotificationHelper>()
            val dose = repository.getDoseOnce(doseId) ?: return@runAsync

            when (intent.action) {
                ACTION_TAKEN -> {
                    repository.updateDose(dose.copy(status = DoseStatus.TAKEN))
                    scheduler.cancelMissedTimeout(doseId)
                    notificationHelper.cancelNotification(doseId)
                }
                ACTION_SKIPPED -> {
                    repository.updateDose(dose.copy(status = DoseStatus.SKIPPED))
                    scheduler.cancelMissedTimeout(doseId)
                    notificationHelper.cancelNotification(doseId)
                }
                ACTION_SNOOZE -> {
                    scheduler.scheduleReminder(doseId, Clock.System.now() + SNOOZE_MINUTES.minutes)
                    notificationHelper.cancelNotification(doseId)
                }
            }
        }
    }
}
