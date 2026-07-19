package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MissedDoseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        if (doseId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val repository = koin.get<MedicationRepository>()
                val dose = repository.getDoseOnce(doseId)
                if (dose != null && dose.status == DoseStatus.PENDING) {
                    repository.updateDose(dose.copy(status = DoseStatus.MISSED))
                    koin.get<NotificationHelper>().cancelNotification(doseId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
