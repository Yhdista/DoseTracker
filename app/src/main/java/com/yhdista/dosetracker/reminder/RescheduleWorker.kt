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
        doseGenerator.runForToday()
        return Result.success()
    }
}
