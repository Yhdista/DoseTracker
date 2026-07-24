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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.month_abbrev_1
import com.yhdista.dosetracker.shared.resources.month_abbrev_10
import com.yhdista.dosetracker.shared.resources.month_abbrev_11
import com.yhdista.dosetracker.shared.resources.month_abbrev_12
import com.yhdista.dosetracker.shared.resources.month_abbrev_2
import com.yhdista.dosetracker.shared.resources.month_abbrev_3
import com.yhdista.dosetracker.shared.resources.month_abbrev_4
import com.yhdista.dosetracker.shared.resources.month_abbrev_5
import com.yhdista.dosetracker.shared.resources.month_abbrev_6
import com.yhdista.dosetracker.shared.resources.month_abbrev_7
import com.yhdista.dosetracker.shared.resources.month_abbrev_8
import com.yhdista.dosetracker.shared.resources.month_abbrev_9
import com.yhdista.dosetracker.shared.resources.timeline_date_short
import com.yhdista.dosetracker.shared.resources.timeline_today_label
import com.yhdista.dosetracker.ui.theme.CycleColors
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

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

private const val AXIS_HEIGHT_DP = 26f
private const val BANDS_HEIGHT_DP = 100f
private const val STRIP_HEIGHT_DP = BANDS_HEIGHT_DP + AXIS_HEIGHT_DP
private const val ACTIVE_THICKNESS_DP = 46f
private const val PAST_THICKNESS_DP = 28f
private const val BAND_CORNER_DP = 8f
private const val EDGE_PADDING_DP = 20f

/** Minimum horizontal gap (dp) kept between two axis labels; closer labels are dropped. */
private const val LABEL_GAP_DP = 6f

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

/**
 * One labelled position on the time axis.
 *
 * [major] ticks are month starts (drawn with a gridline through the whole strip); minor ticks are
 * the weekly marks inside the dense window, which only make sense there because the compressed
 * far region has no room for per-week labels.
 */
private data class AxisTick(
    val dayOffset: Int,
    val major: Boolean,
    val monthIndex: Int, // 0-based, indexes into the localized month-abbreviation list
    val day: Int,
    val year: Int,
    val firstMonthOfYear: Boolean,
)

/** Month starts across the whole span, plus weekly marks inside the dense (±3 weeks) window. */
private fun buildAxisTicks(today: LocalDate): List<AxisTick> {
    val ticks = mutableListOf<AxisTick>()
    for (off in -TIMELINE_HALF_SPAN_DAYS..TIMELINE_HALF_SPAN_DAYS) {
        val date = today.plus(off, DateTimeUnit.DAY)
        if (date.day != 1) continue
        ticks += AxisTick(
            dayOffset = off,
            major = true,
            monthIndex = date.month.ordinal,
            day = date.day,
            year = date.year,
            firstMonthOfYear = date.month.ordinal == 0,
        )
    }
    for (off in listOf(-21, -14, -7, 7, 14, 21)) {
        val date = today.plus(off, DateTimeUnit.DAY)
        ticks += AxisTick(
            dayOffset = off,
            major = false,
            monthIndex = date.month.ordinal,
            day = date.day,
            year = date.year,
            firstMonthOfYear = false,
        )
    }
    return ticks
}

@Composable
private fun monthAbbreviations(): List<String> = listOf(
    stringResource(Res.string.month_abbrev_1),
    stringResource(Res.string.month_abbrev_2),
    stringResource(Res.string.month_abbrev_3),
    stringResource(Res.string.month_abbrev_4),
    stringResource(Res.string.month_abbrev_5),
    stringResource(Res.string.month_abbrev_6),
    stringResource(Res.string.month_abbrev_7),
    stringResource(Res.string.month_abbrev_8),
    stringResource(Res.string.month_abbrev_9),
    stringResource(Res.string.month_abbrev_10),
    stringResource(Res.string.month_abbrev_11),
    stringResource(Res.string.month_abbrev_12),
)

/** A label ready to draw: text, its centre on the axis in dp, and how hard it fights for space. */
private data class AxisLabel(
    val text: String,
    val xCenterDp: Float,
    val style: TextStyle,
    val priority: Int, // higher wins when two labels would overlap
)

@Composable
fun CycleTimeline(
    today: LocalDate,
    bands: List<CycleBand>,
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFF9E9E9E),
    hatchColor: Color = Color(0xFF9E9E9E),
    primaryColor: Color = Color(0xFF6650A4),
    axisColor: Color = Color(0xFF757575),
    onBandClick: (CycleBand) -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()

    // Open with today pinned ~30% from the left, so slightly more future than past shows first.
    LaunchedEffect(Unit) {
        with(density) {
            val target = (xDp(0).dp.toPx() - 130.dp.toPx()).toInt().coerceAtLeast(0)
            scrollState.scrollTo(target)
        }
    }

    // Axis labels: month names across the whole span, weekly dates inside the dense window, and
    // "today" itself, which always wins the space it needs.
    val monthNames = monthAbbreviations()
    val ticks = remember(today) { buildAxisTicks(today) }
    val monthStyle = TextStyle(fontSize = 10.sp, color = axisColor, fontWeight = FontWeight.Medium)
    val dayStyle = TextStyle(fontSize = 9.sp, color = axisColor.copy(alpha = 0.75f))
    val todayStyle = TextStyle(fontSize = 10.sp, color = primaryColor, fontWeight = FontWeight.Bold)

    val labels = buildList {
        add(
            AxisLabel(
                text = "${stringResource(Res.string.timeline_today_label)} " +
                    stringResource(Res.string.timeline_date_short, today.day.toString(), (today.month.ordinal + 1).toString()),
                xCenterDp = xDp(0),
                style = todayStyle,
                priority = 2,
            )
        )
        ticks.forEach { tick ->
            val text = if (tick.major) {
                if (tick.firstMonthOfYear) "${monthNames[tick.monthIndex]} ${tick.year}" else monthNames[tick.monthIndex]
            } else {
                stringResource(Res.string.timeline_date_short, tick.day.toString(), (tick.monthIndex + 1).toString())
            }
            add(
                AxisLabel(
                    text = text,
                    xCenterDp = xDp(tick.dayOffset),
                    style = if (tick.major) monthStyle else dayStyle,
                    priority = if (tick.major) 1 else 0,
                )
            )
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
            val axisTop = size.height - with(density) { AXIS_HEIGHT_DP.dp.toPx() }
            val midY = axisTop / 2f
            val corner = with(density) { BAND_CORNER_DP.dp.toPx() }

            // Behind the bands: the dense-window shading and the month gridlines that give the
            // bands a readable time reference instead of a bare colour strip.
            drawAxisBackground(
                density = density,
                axisTop = axisTop,
                ticks = ticks,
                axisColor = axisColor,
                outlineColor = outlineColor,
            )

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
                end = Offset(todayX, axisTop),
                strokeWidth = with(density) { 2.dp.toPx() }
            )
            drawCircle(
                color = primaryColor,
                radius = with(density) { 3.5.dp.toPx() },
                center = Offset(todayX, with(density) { 8.dp.toPx() })
            )

            drawTimeAxis(
                density = density,
                textMeasurer = textMeasurer,
                axisTop = axisTop,
                ticks = ticks,
                labels = labels,
                axisColor = axisColor,
                primaryColor = primaryColor,
            )
        }
    }
}

/**
 * Month gridlines and the shaded dense window, drawn behind the bands so a band's horizontal
 * extent can be read against calendar months rather than guessed.
 */
private fun DrawScope.drawAxisBackground(
    density: Density,
    axisTop: Float,
    ticks: List<AxisTick>,
    axisColor: Color,
    outlineColor: Color,
) {
    val onePx = with(density) { 1.dp.toPx() }
    val denseLeft = with(density) { xDp(-DENSE_HALF_DAYS).dp.toPx() }
    val denseRight = with(density) { xDp(DENSE_HALF_DAYS).dp.toPx() }

    // The dense window runs at ~5.6× the far-region scale; shading it makes that deliberate
    // distortion visible instead of silently misrepresenting distances.
    drawRect(
        color = axisColor.copy(alpha = 0.06f),
        topLeft = Offset(denseLeft, 0f),
        size = Size(denseRight - denseLeft, axisTop)
    )
    val dash = PathEffect.dashPathEffect(floatArrayOf(3f * onePx, 3f * onePx))
    listOf(denseLeft, denseRight).forEach { x ->
        drawLine(
            color = axisColor.copy(alpha = 0.35f),
            start = Offset(x, 0f),
            end = Offset(x, axisTop),
            strokeWidth = onePx,
            pathEffect = dash
        )
    }

    ticks.filter { it.major }.forEach { tick ->
        val x = with(density) { xDp(tick.dayOffset).dp.toPx() }
        drawLine(
            color = outlineColor.copy(alpha = 0.22f),
            start = Offset(x, 0f),
            end = Offset(x, axisTop),
            strokeWidth = onePx
        )
    }
}

/**
 * The axis lane itself: baseline, ticks, and labels. Labels are placed highest-priority first
 * (today, then months, then weekly dates) and any label that would collide with one already
 * placed is dropped, so the compressed far region degrades to month labels only.
 */
private fun DrawScope.drawTimeAxis(
    density: Density,
    textMeasurer: TextMeasurer,
    axisTop: Float,
    ticks: List<AxisTick>,
    labels: List<AxisLabel>,
    axisColor: Color,
    primaryColor: Color,
) {
    val onePx = with(density) { 1.dp.toPx() }
    drawLine(
        color = axisColor.copy(alpha = 0.4f),
        start = Offset(0f, axisTop),
        end = Offset(size.width, axisTop),
        strokeWidth = onePx
    )

    ticks.forEach { tick ->
        val x = with(density) { xDp(tick.dayOffset).dp.toPx() }
        drawLine(
            color = axisColor.copy(alpha = if (tick.major) 0.55f else 0.35f),
            start = Offset(x, axisTop),
            end = Offset(x, axisTop + (if (tick.major) 5f else 3f) * onePx),
            strokeWidth = onePx
        )
    }
    val todayX = with(density) { xDp(0).dp.toPx() }
    drawLine(
        color = primaryColor,
        start = Offset(todayX, axisTop),
        end = Offset(todayX, axisTop + 6f * onePx),
        strokeWidth = 2f * onePx
    )

    val labelTop = axisTop + 7f * onePx
    val gap = with(density) { LABEL_GAP_DP.dp.toPx() }
    val taken = mutableListOf<ClosedFloatingPointRange<Float>>()
    labels.sortedByDescending { it.priority }.forEach { label ->
        val measured = textMeasurer.measure(label.text, label.style)
        val cx = with(density) { label.xCenterDp.dp.toPx() }
        val left = (cx - measured.size.width / 2f)
            .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
        val span = (left - gap)..(left + measured.size.width + gap)
        val collides = taken.any { it.start < span.endInclusive && span.start < it.endInclusive }
        if (collides) return@forEach
        taken += span
        drawText(measured, topLeft = Offset(left, labelTop))
    }
}
