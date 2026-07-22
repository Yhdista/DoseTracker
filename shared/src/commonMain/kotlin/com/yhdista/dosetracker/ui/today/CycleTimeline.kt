package com.yhdista.dosetracker.ui.today

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.ui.theme.CycleColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Horizontally scrollable "river" timeline of cycles across ±180 days around [today].
 *
 * Uses a focus+context scale: the ±[DENSE_HALF_DAYS] days around today are rendered at full
 * per-day density (legible, individually placed), while days further out compress into weekly
 * blocks, so the full 360-day span stays pannable in a couple of swipes on a phone. The active
 * cycle band is drawn taller with a primary outline; completed bands are shorter and dimmer;
 * the "unplanned" future is a hatched region so it never reads as a real scheduled cycle.
 */
private const val DENSE_HALF_DAYS = 21
private const val DENSE_DAY_DP = 8f
private const val FAR_DAY_DP = 10f / 7f // ~1.43dp per day => ~10dp per compressed week

private const val STRIP_HEIGHT_DP = 100f
private const val ACTIVE_THICKNESS_DP = 46f
private const val PAST_THICKNESS_DP = 28f
private const val BAND_CORNER_DP = 8f
private const val EDGE_PADDING_DP = 20f

/** Raw x (dp) for a day offset relative to today, measured from the far-left edge (offset -180). */
private fun rawXDp(off: Int): Float {
    val o = off.coerceIn(-TIMELINE_HALF_SPAN_DAYS, TIMELINE_HALF_SPAN_DAYS)
    val h = DENSE_HALF_DAYS
    return when {
        o >= 0 -> if (o <= h) o * DENSE_DAY_DP else h * DENSE_DAY_DP + (o - h) * FAR_DAY_DP
        else -> if (o >= -h) o * DENSE_DAY_DP else -(h * DENSE_DAY_DP + (-o - h) * FAR_DAY_DP)
    }
}

private val leftEdgeRaw = rawXDp(-TIMELINE_HALF_SPAN_DAYS)

/** x (dp) for a day offset, shifted so the far-left edge is 0 (plus the leading edge padding). */
private fun xDp(off: Int): Float = EDGE_PADDING_DP + (rawXDp(off) - leftEdgeRaw)

/** Total content width in dp of the strip (both edges padded). */
private val contentWidthDp: Float = xDp(TIMELINE_HALF_SPAN_DAYS) + EDGE_PADDING_DP

private fun bandColor(type: CycleType): Color = when (type) {
    CycleType.NORMAL -> CycleColors.Normal
    CycleType.STANDARD -> CycleColors.Standard
    CycleType.POST -> CycleColors.Post
}

@Composable
fun CycleTimeline(
    today: LocalDate,
    bands: List<CycleBand>,
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFF9E9E9E),
    hatchColor: Color = Color(0xFF9E9E9E),
    primaryColor: Color = Color(0xFF6650A4),
    onBandClick: (CycleBand) -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    // Open with today pinned ~30% from the left, so slightly more future than past shows first.
    LaunchedEffect(Unit) {
        with(density) {
            val target = (xDp(0).dp.toPx() - 130.dp.toPx()).toInt().coerceAtLeast(0)
            scrollState.scrollTo(target)
        }
    }

    // Precompute each band's dp x-range once for both drawing and tap hit-testing.
    val ranges = remember(today, bands) {
        bands.map { band ->
            val startOff = today.daysUntil(band.start)
            val endOff = band.end?.let { today.daysUntil(it) } ?: TIMELINE_HALF_SPAN_DAYS
            band to (xDp(startOff) to xDp(endOff))
        }
    }

    Box(
        modifier = modifier
            .height(STRIP_HEIGHT_DP.dp)
            .horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .height(STRIP_HEIGHT_DP.dp)
                .width(contentWidthDp.dp)
                .pointerInput(ranges) {
                    detectTapGestures { offset ->
                        val xDpTapped = with(density) { offset.x.toDp().value }
                        ranges.firstOrNull { (_, r) -> xDpTapped in r.first..r.second }
                            ?.let { (band, _) -> if (band.kind != BandKind.UNPLANNED) onBandClick(band) }
                    }
                }
        ) {
            val midY = size.height / 2f
            val corner = with(density) { BAND_CORNER_DP.dp.toPx() }

            ranges.forEach { (band, r) ->
                val x1 = with(density) { r.first.dp.toPx() }
                val x2 = with(density) { r.second.dp.toPx() }
                val w = (x2 - x1).coerceAtLeast(with(density) { 3.dp.toPx() })
                val active = band.kind == BandKind.ACTIVE
                val thicknessDp = if (active) ACTIVE_THICKNESS_DP else PAST_THICKNESS_DP
                val th = with(density) { thicknessDp.dp.toPx() }
                val top = midY - th / 2f

                when (band.kind) {
                    BandKind.UNPLANNED -> {
                        // Hatched, low-alpha "neplánováno" region.
                        clipRect(left = x1, top = top, right = x1 + w, bottom = top + th) {
                            val step = with(density) { 8.dp.toPx() }
                            var x = x1 - th
                            while (x < x1 + w + th) {
                                drawLine(
                                    color = hatchColor.copy(alpha = 0.35f),
                                    start = Offset(x, top - 4f),
                                    end = Offset(x + th, top + th + 4f),
                                    strokeWidth = with(density) { 1.dp.toPx() }
                                )
                                x += step
                            }
                        }
                        drawRoundRect(
                            color = hatchColor.copy(alpha = 0.12f),
                            topLeft = Offset(x1, top),
                            size = Size(w, th),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    }
                    else -> {
                        val base = bandColor(band.type)
                        val alpha = if (active) 1f else 0.55f
                        if (band.fadeEnd) {
                            // Dissolve the right edge instead of a hard stop (unbounded / unknown end).
                            val fadeStart = (x1 + w * 0.55f)
                            drawRoundRect(
                                color = base.copy(alpha = alpha),
                                topLeft = Offset(x1, top),
                                size = Size((fadeStart - x1).coerceAtLeast(1f), th),
                                cornerRadius = CornerRadius(corner, corner)
                            )
                            clipRect(left = fadeStart, top = top, right = x1 + w, bottom = top + th) {
                                drawRect(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(base.copy(alpha = alpha), base.copy(alpha = 0f)),
                                        startX = fadeStart,
                                        endX = x1 + w
                                    ),
                                    topLeft = Offset(fadeStart, top),
                                    size = Size((x1 + w) - fadeStart, th)
                                )
                            }
                        } else {
                            drawRoundRect(
                                color = base.copy(alpha = alpha),
                                topLeft = Offset(x1, top),
                                size = Size(w, th),
                                cornerRadius = CornerRadius(corner, corner)
                            )
                        }
                        if (active) {
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = Offset(x1, top),
                                size = Size(w, th),
                                cornerRadius = CornerRadius(corner, corner),
                                style = Stroke(width = with(density) { 2.dp.toPx() })
                            )
                        }
                    }
                }
            }

            // "TEĎ" marker line at today.
            val todayX = with(density) { xDp(0).dp.toPx() }
            drawLine(
                color = primaryColor,
                start = Offset(todayX, 4f),
                end = Offset(todayX, size.height - 4f),
                strokeWidth = with(density) { 2.dp.toPx() }
            )
            drawCircle(
                color = primaryColor,
                radius = with(density) { 3.5.dp.toPx() },
                center = Offset(todayX, with(density) { 8.dp.toPx() })
            )
        }
    }
}
