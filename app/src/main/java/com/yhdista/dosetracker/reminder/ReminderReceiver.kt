package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        if (doseId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val repository = koin.get<MedicationRepository>()
                val dose = repository.getDoseOnce(doseId) ?: return@launch
                val medication = repository.getMedicationOnce(dose.medicationId) ?: return@launch

                koin.get<NotificationHelper>().showNotification(
                    doseId = doseId,
                    medicationName = medication.name,
                    dosage = "${dose.amount ?: medication.dosage} ${dose.unit ?: medication.unit}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
