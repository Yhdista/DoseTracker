package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        com.yhdista.dosetracker.core.AppLogger.i("ReminderReceiver", "onReceive: doseId=$doseId")
        if (doseId == -1L) return

        runAsync("ReminderReceiver") {
            val koin = GlobalContext.get()
            val repository = koin.get<MedicationRepository>()
            val dose = repository.getDoseOnce(doseId) ?: return@runAsync
            if (dose.status != DoseStatus.PENDING) return@runAsync
            val medication = repository.getMedicationOnce(dose.medicationId) ?: return@runAsync

            koin.get<NotificationHelper>().showNotification(
                doseId = doseId,
                medicationName = medication.name,
                dosage = "${dose.amount ?: medication.dosage} ${dose.unit ?: medication.unit}"
            )
        }
    }
}
