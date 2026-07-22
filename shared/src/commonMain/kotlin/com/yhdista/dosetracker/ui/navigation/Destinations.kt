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
    data object CreateCycle : Destination

    @Serializable
    data class CycleWeekEditor(val cycleId: Long, val weekIndex: Int) : Destination

    @Serializable
    data class CycleWeekList(val cycleId: Long) : Destination

    @Serializable
    data object CycleHistory : Destination

    @Serializable
    data object CycleSettings : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object Debug : Destination

    @Serializable
    data object StyleManual : Destination

    @Serializable
    data object StyleTypography : Destination

    @Serializable
    data object StyleIcons : Destination

    @Serializable
    data object StyleColors : Destination

    @Serializable
    data object StyleButtons : Destination

    @Serializable
    data object StyleTexts : Destination

    @Serializable
    data object StyleComponents : Destination
}
