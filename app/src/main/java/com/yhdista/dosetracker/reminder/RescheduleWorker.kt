package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val medicationsResult = repository.getMedications()
            .filter { it is Data.Success }
            .first()
        
        if (medicationsResult is Data.Success) {
            medicationsResult.data.forEach { medication ->
                if (medication.reminderTime != null) {
                    scheduler.scheduleReminder(medication)
                }
            }
        }
        
        return Result.success()
    }
}
