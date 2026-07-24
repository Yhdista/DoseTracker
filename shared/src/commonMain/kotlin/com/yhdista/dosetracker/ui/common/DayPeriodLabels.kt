package com.yhdista.dosetracker.ui.common

import com.yhdista.dosetracker.domain.model.DayPeriod

/** User-facing label for a day period — single source for the three dialogs that list them. */
val DayPeriod.label: String
    get() = when (this) {
        DayPeriod.MORNING -> "Morning (Ráno)"
        DayPeriod.NOON -> "Noon (Poledne)"
        DayPeriod.EVENING -> "Evening (Večer)"
        DayPeriod.NIGHT -> "Night (Noc)"
    }
