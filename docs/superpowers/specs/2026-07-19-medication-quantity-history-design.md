# Per-Medication Quantity History (Month/Year, Weekly Bars)

## Context

The weekly Report screen (`docs/superpowers/specs/2026-07-19-medication-assistant-v2-design.md`,
extended by `docs/superpowers/specs/2026-07-19-report-quantity-metric-design.md`)
shows one week at a time, aggregated across all medications into one card
per medication. The user now wants to drill into a single medication and
see a longer-range trend: one bar per week, bar height = total quantity
(mg/ml/etc) actually taken that week, browsable by calendar month or
calendar year.

The user also mentioned a future want — splitting history into named
"cycles" (e.g. a bulking cycle taking more medications vs. a standard
cycle) instead of raw calendar month/year. That is explicitly **not**
built here (see Non-Goals) — this feature only needs to not paint the data
model into a corner, and it doesn't: cycles would be a separate,
user-defined date range + label layered on top of the same `Dose` history
this feature already queries by plain calendar range. No schema changes
here anticipate or block that later work.

## Current state (reference)

- `MedicationWeekSummary` (`shared/.../ui/report/ReportViewModel.kt`)
  currently groups the week's doses by **`medicationName`** (a string, not
  an id) and carries no `medicationId` field — there's no way to navigate
  from a summary card to "the medication behind this card" today.
- `ReportScreen.kt`'s `MedicationSummaryCard` is a static, non-clickable
  `Card` inside a `LazyColumn`.
- `MedicationRepository`/`MedicationRepositoryImpl` have `getDosesInWeek
  (weekStart: LocalDate): Flow<Data<List<Dose>>>` (all medications, one
  week) and `getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>>`
  (one medication, all time, unbounded) — nothing bounds a query to both
  one medication AND a date range at once.
- `DoseDao.getDosesInTimeRange(startTime: Long, endTime: Long)` is
  all-medications; there's no medication-scoped equivalent.
- `Destination` (`shared/.../ui/navigation/Destinations.kt`) has no entry
  for a per-medication report screen.
- The working tree currently has unrelated, unstaged, user-authored
  changes to `ReminderSchedule`/`MedicationDetailScreen`/`DoseGenerator`/
  `AppDatabase` (a "period time" concept and schedule editing) — confirmed
  intentional, out of scope for this feature, and not touched by anything
  below. This feature only touches Report-related files, adds one new
  screen, and adds one repository/DAO method.

## Goal

Tapping a medication's card on the weekly Report screen opens a new
per-medication screen showing, as a horizontally-scrollable bar chart, one
bar per calendar week: the total quantity (mg/ml/etc, matching that
medication's unit) actually taken that week. A Month/Year toggle switches
between browsing by calendar month (bars = that month's weeks, ~4-6) and
by calendar year (bars = that year's weeks, ~52-53), each with its own
previous/next navigation.

## Non-Goals

- No "cycles" (user-defined named periods) — noted above, deferred
  entirely.
- No comparison view across medications on this screen (it's one
  medication at a time, entered from its own card).
- No editing/logging doses from this screen — view-only, matching the
  existing Report screen's read-only nature.
- No caching/pagination optimization for the year view's ~52 data points
  — this is a personal-scale app; a plain bounded date-range query is
  enough (see Data model).
- A week whose Monday falls just before the queried month/year boundary
  is intentionally *not* extended backward to include those extra days —
  its bar only reflects however many of that week's days actually fall
  inside the currently-viewed month/year. This is a deliberate
  simplification (querying past the boundary to complete a partial week
  would mean a "July" bar quietly includes late-June data); see Data
  model.

## Data model

`MedicationWeekSummary` gains a `medicationId: Long` field, and its
grouping key changes from the medication's name to its id (more correct
regardless of this feature — two medications that happen to share a name
no longer merge into one card):

```kotlin
data class MedicationWeekSummary(
    val medicationId: Long,
    val medicationName: String,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val upcoming: Int,
    val totalAmountTaken: Double,
    val totalAmountScheduled: Double,
    val unit: String
)
```

`ReportViewModel.summarize()`'s `groupBy { it.medicationName }` becomes
`groupBy { it.medicationId }`, with `medicationName`/`unit` read off the
first dose in each group (as `unit` already is).

New repository/DAO method, mirroring `getDosesInWeek`'s existing
LocalDate-range-to-epoch-millis shape (including its `- 1` exclusive-end
fix, applied here from the start rather than discovered later):

```kotlin
// MedicationRepository
fun getDosesForMedicationInRange(
    medicationId: Long,
    start: LocalDate,
    endExclusive: LocalDate
): Flow<Data<List<Dose>>>
```

```kotlin
// DoseDao
@Transaction
@Query("""
    SELECT doses.*, medications.name as medicationName
    FROM doses
    INNER JOIN medications ON doses.medicationId = medications.id
    WHERE doses.medicationId = :medicationId
      AND timestamp >= :startTime AND timestamp <= :endTime
    ORDER BY timestamp ASC
""")
fun getDosesForMedicationInTimeRange(
    medicationId: Long,
    startTime: Long,
    endTime: Long
): Flow<List<DoseWithMedication>>
```

No new Room entity, no schema/version bump — this is a plain query
addition over the existing `doses`/`medications` tables.

## Week-bucketing

A dose's week is the Monday-aligned week containing its own timestamp
(same "week" definition already used everywhere else in the app). The
existing private `currentWeekStart()` in `ReportViewModel.kt` (which
computes *today's* week start) is generalized into a reusable, public
pure function in the same file:

```kotlin
fun weekStartOf(date: LocalDate): LocalDate {
    val daysSinceMonday = date.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    return date.minus(daysSinceMonday, DateTimeUnit.DAY)
}

private fun currentWeekStart(): LocalDate =
    weekStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
```

The new screen's aggregation:

1. Query `getDosesForMedicationInRange(medicationId, periodStart,
   periodEndExclusive)` for the currently displayed month or year.
2. Filter to `status == DoseStatus.TAKEN`, group by `weekStartOf(dose
   timestamp's local date)`, sum `amount` per group.
3. Enumerate every week-start from `weekStartOf(periodStart)` up to (but
   not including) `periodEndExclusive`, stepping by 7 days, and map each
   to its summed quantity (`0.0` if that week had no TAKEN doses in the
   query result) — this keeps weeks with nothing taken visible in the
   sequence as zero-height bars rather than silently disappearing.

## UI

New screen `MedicationReportScreen(medicationId, viewModel, onBack)`,
new `MedicationReportViewModel`:

```kotlin
enum class ReportRangeMode { MONTH, YEAR }

data class WeekQuantity(val weekStart: LocalDate, val totalTaken: Double)

data class MedicationReportState(
    val medicationName: String = "",
    val unit: String = "",
    val mode: ReportRangeMode = ReportRangeMode.MONTH,
    val periodStart: LocalDate = todaysMonthStart(), // first-of-month containing today
    val weeks: Data<List<WeekQuantity>> = Data.Loading
)

sealed interface MedicationReportEvent {
    data object ToggleMode : MedicationReportEvent
    data object PreviousPeriod : MedicationReportEvent
    data object NextPeriod : MedicationReportEvent
}
```

- Top bar: medication name (loaded via the existing
  `MedicationRepository.getMedicationById`/`getMedicationOnce`), back
  arrow.
- A segmented Month/Year toggle (Material3 `SegmentedButton`).
- A `< [period label] >` row below it, matching the existing weekly
  Report's arrow-row pattern exactly. Period label: `"July 2026"` in
  Month mode, `"2026"` in Year mode.
- `ToggleMode` re-anchors the new mode's period to *contain* whatever
  period is currently showing (e.g. toggling from "July 2026" to Year
  mode shows "2026", not necessarily the current real-world year) — so
  switching modes while browsing history doesn't jump back to today.
- The chart: a horizontally-scrollable `Row` (`Modifier.horizontalScroll`),
  one bar per `WeekQuantity`. Each bar is a fixed-width column: a
  `Box` whose height is `(totalTaken / maxTakenInPeriod) *
  <max bar height>`, floored to a small minimum visible height for a
  genuinely-zero week (so it stays present and tappable rather than
  invisible), rounded top corners, filled with
  `MaterialTheme.colorScheme.primary`, sitting on a shared baseline. A
  short week-start label (`"07/13"`) below each bar. Tapping a bar
  toggles a value label (e.g. `"1000.0 mg"`) shown above that bar only —
  per the app's existing chart (progress-meter) precedent of direct
  labels being selective, not printed on every mark.
- Scale is per-medication, per-currently-displayed-period, auto-derived
  from `maxTakenInPeriod` (the largest single week's total currently
  shown) — consistent with the earlier quantity-meter's "auto per
  medication" scaling decision, no fixed cross-medication constant.
- Loading/Error/Empty (`weeks` data empty or all-zero — still rendered,
  not specially collapsed, so an all-zero month is visibly "an empty set
  of bars" rather than a blank screen) states follow the existing
  `Data<T>` `when` pattern used throughout the app.

`ReportScreen.kt`'s `MedicationSummaryCard` gains an `onClick: () -> Unit`
parameter, wired from `ReportScreen`'s `items(result.data) { summary -> }`
loop to `onMedicationClick(summary.medicationId)`. `ReportScreen` itself
gains an `onMedicationClick: (Long) -> Unit` parameter.

`Destinations.kt` gains:

```kotlin
@Serializable
data class MedicationReport(val medicationId: Long) : Destination
```

`DoseTrackerAppMain.kt`: the `Destination.Report` branch's `ReportScreen(...)`
call gains `onMedicationClick = { id -> backstack.add(Destination.MedicationReport(id)) }`;
a new `is Destination.MedicationReport ->` branch renders
`MedicationReportScreen` (detail pane, matching the other detail-style
destinations like `ConfirmDose`/`MedicationDetail`).

## Testing

`MedicationReportViewModelTest` (new, same JUnit4/mockito-kotlin/
`StandardTestDispatcher` convention as `ReportViewModelTest`):
- Week-bucketing sums `amount` correctly across multiple TAKEN doses
  landing in the same week, and keeps a week with zero TAKEN doses in the
  sequence as a `0.0` entry rather than omitting it.
- `PreviousPeriod`/`NextPeriod` shift by exactly one calendar month (Month
  mode) or one calendar year (Year mode), including a December→January /
  January→December month rollover.
- `ToggleMode` re-anchors to the period containing the currently-displayed
  date rather than resetting to today.

`weekStartOf` is pure and already effectively covered by extending
`ReportViewModelTest`'s existing week-math assertions if useful, but
doesn't need a dedicated test beyond what `MedicationReportViewModelTest`
already exercises through the bucketing tests above.

No DAO/repository-level test changes needed beyond what the new query
requires structurally — `getDosesForMedicationInRange` mirrors
`getDosesInWeek`'s already-reviewed range-boundary logic exactly, applied
correctly from the start.
