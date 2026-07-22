package com.yhdista.dosetracker.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yhdista.dosetracker.MainActivity
import com.yhdista.dosetracker.R

class NotificationHelper(
    private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "medication_reminders"
        const val CHANNEL_NAME = "Medication Reminders"
        const val CHANNEL_DESCRIPTION = "Notifications for medication dose reminders"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(doseId: Long, medicationName: String, dosage: String) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("doseId", doseId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            doseId.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication Reminder")
            .setContentText("It's time to take your $medicationName ($dosage)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Taken", doseActionIntent(doseId, DoseActionReceiver.ACTION_TAKEN))
            .addAction(0, "Skip", doseActionIntent(doseId, DoseActionReceiver.ACTION_SKIPPED))
            .addAction(0, "Snooze", doseActionIntent(doseId, DoseActionReceiver.ACTION_SNOOZE))
            .build()

        notificationManager.notify(doseId.toInt(), notification)
    }

    private fun doseActionIntent(doseId: Long, action: String): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            putExtra("doseId", doseId)
        }
        return PendingIntent.getBroadcast(
            context,
            (doseId.toString() + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelNotification(doseId: Long) {
        notificationManager.cancel(doseId.toInt())
    }
}
