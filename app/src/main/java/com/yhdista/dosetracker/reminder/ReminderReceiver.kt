package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: return
        val dosage = intent.getStringExtra("dosage") ?: ""
        val medicationId = intent.getLongOf("medicationId", -1L)
        
        if (medicationId != -1L) {
            notificationHelper.showNotification(medicationName, dosage, medicationId)
        }
    }

    private fun Intent.getLongOf(key: String, defaultValue: Long): Long {
        return if (hasExtra(key)) getLongExtra(key, defaultValue) else defaultValue
    }
}
