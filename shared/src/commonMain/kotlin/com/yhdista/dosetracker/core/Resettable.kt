package com.yhdista.dosetracker.core

/**
 * Interface for classes that hold user state and need to be reset.
 */
interface Resettable {
    fun reset()
}
