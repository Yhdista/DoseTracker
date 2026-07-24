package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.Data
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart

/**
 * Standard repository flow envelope: emits Loading first, then whatever the upstream
 * maps to, and turns upstream failures into a logged [Data.Error] with [errorMessage].
 */
internal fun <R> Flow<Data<R>>.withLoadingAndErrors(errorMessage: String): Flow<Data<R>> =
    onStart { emit(Data.Loading) }
        .catch { e ->
            AppLogger.e("Database", errorMessage, e)
            emit(Data.Error(errorMessage, e))
        }
