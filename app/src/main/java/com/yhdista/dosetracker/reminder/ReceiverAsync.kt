package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs [block] off the main thread inside the ~10 s window a BroadcastReceiver gets
 * after goAsync(). Failures are logged instead of crashing the process in the
 * background — an uncaught Room/Koin exception here would take the whole app down.
 */
internal fun BroadcastReceiver.runAsync(tag: String, block: suspend () -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            withTimeout(9_000) { block() }
        } catch (e: Exception) {
            com.yhdista.dosetracker.core.AppLogger.e(tag, "Broadcast handling failed", e)
        } finally {
            pendingResult.finish()
        }
    }
}
