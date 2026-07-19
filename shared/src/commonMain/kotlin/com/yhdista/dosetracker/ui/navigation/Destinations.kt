package com.yhdista.dosetracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Destination : NavKey {
    @Serializable
    data object Today : Destination

    @Serializable
    data object Medications : Destination

    @Serializable
    data object History : Destination

    @Serializable
    data object Report : Destination

    @Serializable
    data class MedicationDetail(val id: Long) : Destination

    @Serializable
    data class AddDose(val medicationId: Long) : Destination

    @Serializable
    data class ConfirmDose(val doseId: Long) : Destination

    @Serializable
    data class MedicationReport(val medicationId: Long) : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object Debug : Destination
}
