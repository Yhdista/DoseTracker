# Weekly Report — Total Quantity Taken Per Medication

## Context

The weekly Report screen (shipped in "Medication Assistant v2",
`docs/superpowers/specs/2026-07-19-medication-assistant-v2-design.md`) currently
shows, per medication, how many dose *occurrences* were Taken/Missed/Skipped/
Upcoming in the selected week. It says nothing about *how much* — a medication
taken 3 times at 500mg looks the same in the counts as one taken 3 times at
50mg. The user wants to also see total active-substance quantity (mg, ml, etc.)
taken per medication that week, shown as a bar/meter, not just raw counts.

## Current state (reference)

- `MedicationWeekSummary` (`shared/.../ui/report/ReportViewModel.kt`):
  `medicationName, taken, missed, skipped, upcoming` — counts only, no
  quantity.
- `ReportViewModel.summarize()` groups the week's `Dose` list by
  `medicationName` and counts by `DoseStatus`.
- Every `Dose` row already carries `amount: Double?` and `unit: String?`.
  Schedule-generated doses (`DoseGenerator`) always populate both
  (`amount = medication.dosage`, `unit = medication.unit`) at creation time,
  regardless of eventual status — so a `MISSED`/`SKIPPED`/`PENDING` dose still
  has a real `amount`, not `null`. Ad-hoc doses logged via `AddDoseScreen`
  also set `amount`/`unit` (defaulted from the medication, user-editable).
- `ReportScreen.kt`'s `MedicationSummaryCard` renders the medication name and
  a single line of counts (`"Taken: X  Missed: Y  Skipped: Z"`), plus an
  optional "Upcoming: N" line.

## Goal

Each medication's card on the Report screen also shows, for the selected
week: total quantity taken, total quantity scheduled, and what percentage of
the scheduled quantity was actually taken — as a thin progress bar plus a
direct label (e.g. `"620 / 1000 mg (62%)"`). This sits alongside the existing
counts, not in place of them.

## Non-Goals

- No new charting library, no multi-series/categorical chart — this is one
  meter per medication card, not a chart with a legend.
- No fixed cross-medication scale (e.g. a hardcoded "1000mg per bar segment")
  — each medication's bar is scaled to its own week (percentage of its own
  scheduled total), which naturally handles medications with very different
  dose magnitudes (mg vs ml vs IU) without a shared axis.
- No per-day breakdown within the week (that's the "7 bars per medication"
  alternative considered and explicitly not chosen) — one aggregate bar per
  medication per week, matching the existing one-card-per-medication layout.
- No historical/multi-week trend view.

## Data model

`MedicationWeekSummary` gains three fields:

```kotlin
data class MedicationWeekSummary(
    val medicationName: String,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val upcoming: Int,
    val totalAmountTaken: Double,
    val totalAmountScheduled: Double,
    val unit: String,
)
```

- `totalAmountTaken` — sum of `amount` across doses with `status == TAKEN`
  (defaults missing `amount` to `0.0`; in practice every dose has one, see
  Current State above).
- `totalAmountScheduled` — sum of `amount` across **all** doses in the week
  regardless of status (Taken + Missed + Skipped + Upcoming). This includes
  ad-hoc, non-schedule-linked doses too — an ad-hoc `TAKEN` dose contributes
  to both sides identically to a schedule-generated one, so it doesn't skew
  the percentage.
- `unit` — the medication's unit, read off the first dose in the group that
  has a non-null `unit` (all doses for a medication carry the same unit in
  practice, since it's always sourced from `Medication.unit`).
- Percentage is *not* stored on the model — it's derived at render time as
  `totalAmountTaken / totalAmountScheduled` when the denominator is `> 0`,
  else treated as "no data" (no scheduled quantity that week — e.g. a
  medication with schedules created after the week already passed, or one
  logged purely ad-hoc). This mirrors `totalAmountScheduled == 0` as the
  "can't compute a percentage" signal rather than inventing a separate
  nullable field.
- The percentage can exceed 100% (taking an unscheduled extra ad-hoc dose
  pushes `totalAmountTaken` above `totalAmountScheduled`) — this is a real,
  correct signal (over-adherence), not an error. The numeric label shows the
  true percentage uncapped; the bar's visual fill is capped at 100% width
  (see UI section) since a progress bar can't visually exceed its track.

## Aggregation logic

`ReportViewModel.summarize()`'s per-medication `map` block adds:

```kotlin
totalAmountTaken = doses.filter { it.status == DoseStatus.TAKEN }
    .sumOf { it.amount ?: 0.0 },
totalAmountScheduled = doses.sumOf { it.amount ?: 0.0 },
unit = doses.firstOrNull { it.unit != null }?.unit ?: "",
```

No repository or DAO changes — this is pure aggregation over the same
`Dose` list `getDosesInWeek` already returns.

## UI

`MedicationSummaryCard` adds, below the existing counts line:

- A thin `LinearProgressIndicator` (Material3), `progress = (totalAmountTaken
  / totalAmountScheduled).toFloat().coerceIn(0f, 1f)` when
  `totalAmountScheduled > 0`; omitted entirely when `totalAmountScheduled ==
  0` (nothing meaningful to show — no scheduled quantity that week).
  Track/fill colors reuse the app's existing Material3 `colorScheme.primary`
  (fill) / `colorScheme.surfaceVariant` (track) — the same pair already used
  elsewhere in the app to mean "completed" vs. "not yet" (e.g.
  `TodayScreen.kt`'s taken-state card background and check-icon tint). No new
  colors, no custom palette — this is a single-value meter, not a
  multi-series chart, so categorical color assignment doesn't apply.
- A direct label above or beside the bar:
  `"${formatted totalAmountTaken} / ${formatted totalAmountScheduled} ${unit}
  (${percentage}%)"`, e.g. `"620 / 1000 mg (62%)"`. The percentage is the
  true, uncapped value (can read over 100%). Amounts are formatted without
  unnecessary trailing zeros (matching how amounts already render elsewhere
  in the app, e.g. `HistoryScreen.kt`'s `item.dose.amount?.toString()`).

## Testing

`ReportViewModelTest`'s existing aggregation test extends to also assert
`totalAmountTaken`/`totalAmountScheduled`/`unit` for the fixture doses it
already constructs (mixed statuses with distinct `amount`/`unit` values per
medication), plus a new case covering `totalAmountScheduled == 0` (a
medication with only, say, `MISSED`... no — every dose has an amount, so this
case is specifically "no doses at all for a medication in the queried week,"
which already can't produce a `MedicationWeekSummary` row since the grouping
only emits rows for medications with at least one dose that week — so the
`totalAmountScheduled == 0` UI branch is actually unreachable in practice
given the aggregation's own grouping, and doesn't need a dedicated
ViewModel-level test; it remains defensive UI code for a case the data layer
already prevents). No repository/DAO test changes needed (no new queries).
