package com.yhdista.dosetracker.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Logs each emission while the flow is collected. Use this in front of stateIn instead of
 * a `viewModelScope.launch { uiState.collect { log } }` init block — that permanent
 * collector kept WhileSubscribed upstreams (Room queries) alive for the ViewModel's
 * whole lifetime, defeating the point of WhileSubscribed.
 */
fun <T> Flow<T>.logEach(tag: String, describe: (T) -> String): Flow<T> =
    onEach { AppLogger.d(tag, describe(it)) }
