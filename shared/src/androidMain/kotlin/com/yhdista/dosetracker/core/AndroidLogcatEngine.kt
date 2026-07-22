package com.yhdista.dosetracker.core

import android.util.Log
import timber.log.Timber

class AndroidLogcatEngine : LogEngine {
    override fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable?) {
        val level = when (priority) {
            LogPriority.VERBOSE -> Log.VERBOSE
            LogPriority.DEBUG -> Log.DEBUG
            LogPriority.INFO -> Log.INFO
            LogPriority.WARN -> Log.WARN
            LogPriority.ERROR -> Log.ERROR
        }
        // Logs to Logcat via Timber prepending 'DT_' for universal filtering (e.g. tag:DT_)
        Timber.tag("DT_$tag").log(level, throwable, message)
    }
}
