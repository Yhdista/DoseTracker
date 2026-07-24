package com.yhdista.dosetracker.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import com.yhdista.dosetracker.core.AppLogger

internal class LoggingSQLiteDriver(private val delegate: SQLiteDriver) : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection {
        val conn = delegate.open(fileName)
        return LoggingSQLiteConnection(conn)
    }
}

internal class LoggingSQLiteConnection(private val delegate: SQLiteConnection) : SQLiteConnection {
    override fun prepare(sql: String): SQLiteStatement {
        val stmt = delegate.prepare(sql)
        return LoggingSQLiteStatement(sql, stmt)
    }

    override fun close() {
        delegate.close()
    }

    override fun inTransaction(): Boolean {
        return delegate.inTransaction()
    }
}

internal class LoggingSQLiteStatement(
    private val sql: String,
    private val delegate: SQLiteStatement
) : SQLiteStatement {
    
    private val boundArgs = mutableMapOf<Int, Any?>()
    private var stepCount = 0
    private val isSelect = sql.trim().uppercase().let { it.startsWith("SELECT") || it.startsWith("WITH") }
    private var hasLogged = false

    override fun bindBlob(index: Int, value: ByteArray) {
        boundArgs[index] = "Blob[${value.size} bytes]"
        delegate.bindBlob(index, value)
    }

    override fun bindDouble(index: Int, value: Double) {
        boundArgs[index] = value
        delegate.bindDouble(index, value)
    }

    override fun bindLong(index: Int, value: Long) {
        boundArgs[index] = value
        delegate.bindLong(index, value)
    }

    override fun bindNull(index: Int) {
        boundArgs[index] = null
        delegate.bindNull(index)
    }

    override fun bindText(index: Int, value: String) {
        boundArgs[index] = value
        delegate.bindText(index, value)
    }

    override fun close() {
        if (!hasLogged) {
            hasLogged = true
            try {
                val simplifiedSql = sql.replace(Regex("\\s+"), " ").trim()
                val argsStr = if (boundArgs.isNotEmpty()) " | Args: $boundArgs" else ""
                val resultStr = if (isSelect) " | Found: $stepCount rows" else " | Executed write"
                AppLogger.d("Database", "SQL: $simplifiedSql$argsStr$resultStr")
            } catch (e: Exception) {
                // Ignore logging failures
            }
        }
        delegate.close()
    }

    override fun getColumnCount(): Int = delegate.getColumnCount()

    override fun getDouble(index: Int): Double = delegate.getDouble(index)

    override fun getLong(index: Int): Long = delegate.getLong(index)

    override fun getText(index: Int): String = delegate.getText(index)

    override fun getBlob(index: Int): ByteArray = delegate.getBlob(index)

    override fun isNull(index: Int): Boolean = delegate.isNull(index)

    override fun getColumnName(index: Int): String = delegate.getColumnName(index)

    override fun getColumnType(index: Int): Int = delegate.getColumnType(index)

    override fun step(): Boolean {
        val result = delegate.step()
        if (result) {
            stepCount++
        }
        return result
    }

    override fun reset() {
        delegate.reset()
    }

    override fun clearBindings() {
        boundArgs.clear()
        delegate.clearBindings()
    }
}
