package com.yhdista.dosetracker.core

enum class LogPriority(val level: String) {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E")
}

interface LogEngine {
    fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable? = null)
}

object AppLogger {
    private val engines = mutableListOf<LogEngine>()

    fun init(vararg newEngines: LogEngine) {
        synchronized(engines) {
            engines.clear()
            engines.addAll(newEngines)
        }
        d("AppLogger", "Logger initialized with ${newEngines.size} engines")
    }

    fun addEngine(engine: LogEngine) {
        synchronized(engines) {
            engines.add(engine)
        }
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(LogPriority.VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(LogPriority.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LogPriority.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogPriority.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogPriority.ERROR, tag, message, throwable)
    }

    /**
     * Logs structured data objects with a clear prefix.
     */
    fun data(tag: String, message: String, data: Any?) {
        val dataStr = when (data) {
            null -> "null"
            is Collection<*> -> "Collection[size=${data.size}]: [${data.joinToString(limit = 20)}]"
            is Map<*, *> -> "Map[size=${data.size}]: {${data.entries.joinToString(limit = 20)}}"
            else -> data.toString()
        }
        d(tag, "$message | DATA: $dataStr")
    }

    private fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable?) {
        val currentEngines = synchronized(engines) { engines.toList() }
        for (engine in currentEngines) {
            engine.log(priority, tag, message, throwable)
        }
    }
}
