package com.yhdista.dosetracker.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yhdista.dosetracker.MainActivity
import com.yhdista.dosetracker.R

internal class NotificationHelper(
    private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "medication_reminders"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
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
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text, medicationName, dosage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, context.getString(R.string.notification_action_taken), doseActionIntent(doseId, DoseActionReceiver.ACTION_TAKEN))
            .addAction(0, context.getString(R.string.notification_action_skip), doseActionIntent(doseId, DoseActionReceiver.ACTION_SKIPPED))
            .addAction(0, context.getString(R.string.notification_action_snooze), doseActionIntent(doseId, DoseActionReceiver.ACTION_SNOOZE))
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
