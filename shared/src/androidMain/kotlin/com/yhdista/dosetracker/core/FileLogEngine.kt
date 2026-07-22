package com.yhdista.dosetracker.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogEngine(context: Context) : LogEngine {

    private val logDirectory: File? = context.getExternalFilesDir("debug_log")
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logChannel = Channel<String>(capacity = 1000)

    init {
        // Start background coroutine to write logs sequentially without resource contention or locks
        scope.launch {
            for (logLine in logChannel) {
                writeLineToFile(logLine)
            }
        }
        // Clean up log files older than 7 days on startup
        deleteOldLogs()
    }

    override fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val throwableStr = throwable?.let { "\n" + getStackTraceString(it) } ?: ""
        val formattedLine = "[$timestamp] [${priority.level}] [$tag]: $message$throwableStr\n"
        
        // Asynchronously send to the channel (non-blocking)
        logChannel.trySend(formattedLine)
    }

    private fun writeLineToFile(line: String) {
        val dir = logDirectory ?: return
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val fileName = "log_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".txt"
        val file = File(dir, fileName)
        try {
            FileWriter(file, true).use { writer ->
                writer.write(line)
            }
        } catch (e: Exception) {
            Log.e("FileLogEngine", "Failed to write log to file", e)
        }
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    private fun deleteOldLogs() {
        scope.launch {
            try {
                val dir = logDirectory ?: return@launch
                val now = System.currentTimeMillis()
                val maxAgeMs = 7L * 24 * 60 * 60 * 1000 // 7 days limit
                val files = dir.listFiles() ?: return@launch
                for (file in files) {
                    if (file.isFile && file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                        if (now - file.lastModified() > maxAgeMs) {
                            val deleted = file.delete()
                            Log.d("FileLogEngine", "Deleted old log file: ${file.name}, success=$deleted")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileLogEngine", "Failed to delete old log files", e)
            }
        }
    }
}
