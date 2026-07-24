package com.yhdista.dosetracker.ui.common

import androidx.compose.runtime.Composable
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.period_evening
import com.yhdista.dosetracker.shared.resources.period_morning
import com.yhdista.dosetracker.shared.resources.period_night
import com.yhdista.dosetracker.shared.resources.period_noon
import org.jetbrains.compose.resources.stringResource

/** User-facing label for a day period — single source for the dialogs that list them. */
val DayPeriod.label: String
    @Composable get() = stringResource(
        when (this) {
            DayPeriod.MORNING -> Res.string.period_morning
            DayPeriod.NOON -> Res.string.period_noon
            DayPeriod.EVENING -> Res.string.period_evening
            DayPeriod.NIGHT -> Res.string.period_night
        }
    )
