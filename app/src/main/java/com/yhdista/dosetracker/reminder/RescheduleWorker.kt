package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
    private val doseGenerator: DoseGenerator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        com.yhdista.dosetracker.core.AppLogger.i("RescheduleWorker", "Starting daily dose generation and alarm rescheduling")
        return try {
            doseGenerator.runForToday()
            com.yhdista.dosetracker.core.AppLogger.i("RescheduleWorker", "Daily dose generation and alarm rescheduling successfully finished")
            Result.success()
        } catch (e: Exception) {
            com.yhdista.dosetracker.core.AppLogger.e("RescheduleWorker", "Failed daily dose generation and alarm rescheduling", e)
            Result.failure()
        }
    }
}
