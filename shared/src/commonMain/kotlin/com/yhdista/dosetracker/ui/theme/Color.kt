package com.yhdista.dosetracker.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Cycle-type colors used by the Today "Kalendář" timeline to color-code cycle bands.
 * Derived to sit alongside the Material3 primary/tertiary/secondary hue families so the
 * timeline stays readable in both light and dark themes without a bespoke palette.
 * Kept as a single value per type (rather than a full tonal range) because the timeline
 * draws bands directly on a Canvas rather than through the MaterialTheme color scheme.
 */
object CycleColors {
    val Normal = Color(0xFF6650A4)   // NORMAL cycle — aligns with the primary hue family
    val Standard = Color(0xFF1F7A6C) // STANDARD cycle — teal, distinct "baseline/ongoing" feel
    val Post = Color(0xFFB5562F)     // POST cycle — warm terracotta, "wind-down" feel
}
