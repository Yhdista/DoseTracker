package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.DoseRepository
import org.koin.core.context.GlobalContext

class MissedDoseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        com.yhdista.dosetracker.core.AppLogger.i("MissedDoseReceiver", "onReceive: doseId=$doseId")
        if (doseId == -1L) return

        runAsync("MissedDoseReceiver") {
            val koin = GlobalContext.get()
            val repository = koin.get<DoseRepository>()
            val dose = repository.getDoseOnce(doseId)
            if (dose != null && dose.status == DoseStatus.PENDING) {
                repository.updateDose(dose.copy(status = DoseStatus.MISSED))
                koin.get<NotificationHelper>().cancelNotification(doseId)
            }
        }
    }
}
