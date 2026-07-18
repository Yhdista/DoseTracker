package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: return
        val dosage = intent.getStringExtra("dosage") ?: ""
        val medicationId = intent.getLongOf("medicationId", -1L)

        if (medicationId != -1L) {
            val notificationHelper = GlobalContext.get().get<NotificationHelper>()
            notificationHelper.showNotification(medicationName, dosage, medicationId)
        }
    }

    private fun Intent.getLongOf(key: String, defaultValue: Long): Long {
        return if (hasExtra(key)) getLongExtra(key, defaultValue) else defaultValue
    }
}
