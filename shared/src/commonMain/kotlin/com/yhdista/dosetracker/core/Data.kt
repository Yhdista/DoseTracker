package com.yhdista.dosetracker.core

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
sealed class Data<out T> {
    data class Success<out T>(val data: T) : Data<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Data<Nothing>()
    object Loading : Data<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
}
