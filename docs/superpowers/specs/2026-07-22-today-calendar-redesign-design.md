# Today → "Kalendář" Redesign (±180denní časová osa cyklů + 14denní agenda)

## Context

Today's "Today's Doses" screen (`ui/today/TodayScreen.kt` / `TodayViewModel.kt`) shows
exactly one date: a cycle dashboard header (if a cycle is `ACTIVE`) followed by a flat
list of *today's* doses, split into "V rámci cyklu {name}" / "Ostatní".
`TodayViewModel.uiState` only ever calls `repository.getDosesForDate(today)` — there is
no date-range awareness anywhere in this screen.

The user wants to rethink this page as something closer to a personal calendar: a
horizontal timeline spanning **−180 to +180 days** that visualizes cycles (with the
active cycle clearly emphasized), the existing cycle-status card, and then an agenda of
the **next 14 days** — today shown with full detail, the following days progressively
simplified, with a clear, deliberate visual separation between individual days and
between weeks.

## Goal

- Replace the single-day dose list with a three-part vertical composition:
  1. A compact, horizontally scrollable **cycle timeline** (±180 days).
  2. The existing **active-cycle status card** (kept, lightly enhanced).
  3. A **14-day agenda**: today expanded, the next 13 days condensed, grouped by week.
- Make the *active* cycle unmistakable on the timeline at a glance.
- Handle the fact that, architecturally, the "future" beyond the active cycle's
  `onCompleteAction`/`nextCycleId` chain is genuinely unplanned — the design must not
  pretend to know about cycles that don't exist yet.
- Propose whatever small backend/ViewModel changes are required — call them out
  explicitly rather than silently assuming new capabilities exist.

## Non-Goals

- No redesign of `HistoryScreen` (all-time dose log) or `ReportScreen`/`MedicationReportScreen`
  (weekly per-medication stats) — the new Today screen deep-links into these rather than
  duplicating them.
- No support for scheduling a cycle to start in the future — that remains an explicit
  non-goal inherited from the original cycles feature; this redesign visualizes the
  *consequence* of that constraint (an "unplanned" future band) rather than removing it.
- No new `endDate` persistence for cycles in this pass — see Open Question below.
- No implementation yet. This document is the design/spec step; a task-by-task
  `docs/superpowers/plans/2026-07-22-today-calendar-redesign.md` implementation plan is
  a deliberate follow-up once this direction is confirmed, matching how
  `2026-07-22-cycle-settings-screen-design.md` → `2026-07-22-cycle-settings-screen.md`
  was sequenced.

## Current state (reference)

- `TodayState(doses: Data<List<Dose>>, activeCycle: Data<Cycle?>, selectedDoseId: Long?)`
  — single-date only.
- `Cycle(id, name, type: NORMAL|STANDARD|POST, totalWeeks: Int?, startDate, status:
  DRAFT|ACTIVE|COMPLETED, onCompleteAction: TO_STANDARD|TO_POST|TO_NONE, nextCycleId)`.
  Exactly one `ACTIVE` cycle at a time; cycles always start "today" when activated (no
  future-dated activation). `STANDARD` cycles are unbounded (`totalWeeks == null`).
- `Dose(id, medicationId, scheduleId, cycleId, medicationName, timestamp, amount, unit,
  status: TAKEN|MISSED|SKIPPED|PENDING)`.
- `MedicationRepository` already exposes: `getDosesForDate(date)`, `getDosesInWeek(weekStart)`,
  `getDosesForMedicationInRange(medicationId, start, endExclusive)`, `getActiveCycle()`,
  `getCompletedCycles()`. It does **not** yet expose a medication-agnostic date-range dose
  query, but the underlying `DoseDao.getDosesInTimeRange(startTime, endTime)` already
  exists and is used elsewhere — adding a repository-level wrapper is a small, additive
  change (see Architecture changes below).
- House visual language: Material3, `Card`-based composition (16dp padding, 12dp
  `Arrangement.spacedBy` between list items), Czech UI strings, `Icons.Rounded.*`,
  `LinearProgressIndicator` already used for progress (`ReportScreen`'s
  `MedicationSummaryCard`), a "‹ label ›" prev/next paging idiom already established in
  `ReportScreen`'s week navigator. `Color.kt` still holds the Compose-template
  placeholder palette (`Purple80`/`PurpleGrey80`/`Pink80`) — genuinely unbranded, so a
  small, purposeful palette extension for cycle-type coding is reasonable here rather
  than a full rebrand.
- `ui/debug/StyleGuide.kt` ("Grafický manuál") is the app's living component catalog —
  new reusable visual components introduced by this redesign belong there too.

## Proposed design

### 1. Cycle timeline (the new hero element)

A single horizontally-scrollable strip, ~96dp tall, pinned at the very top under the
`TopAppBar`. It is **not** a full month-grid calendar — it's a compact ribbon, closer to
a Gantt strip, because 360 days of grid cells would be unreadable on a ~360dp-wide phone.

- **Non-linear density.** The middle ±21 days around today render at full density (one
  day ≈ 8dp tick, so the "near" window is legible and individually tappable). Beyond
  that, days compress into week-blocks (7 days → one ~10dp segment), so the full 360-day
  span fits in roughly 21×8 + ((180−21)/7)×10 ≈ 168 + 227 ≈ 395dp per side — a strip
  that's pannable in a couple of swipes rather than dozens. This mirrors a common
  "focus + context" timeline pattern and keeps today's immediate neighborhood
  scannable while the far past/future stays present but compact.
- **Today anchor.** The strip opens (initial scroll position) with today pinned at
  roughly 30% from the left edge — slightly more future than past is visible on first
  glance, matching what the user actually wants to plan around.
  A vertical "TEĎ" (now) marker line spans the full height of the strip at that
  position, always redrawn there even after the user scrolls away and back (a small
  "return to today" chip fades in once they've scrolled the anchor out of view).
- **Cycle bands.** Each cycle renders as a rounded-rect band spanning its date range,
  color-coded by `CycleType` (proposed additions to `Color.kt`: `CycleNormal` (primary
  hue), `CycleStandard` (tertiary hue), `CyclePost` (secondary hue) — reusing
  Material3's existing three-hue system rather than inventing a fourth palette). The
  **active** cycle's band is drawn taller (full 96dp) with a solid fill and a subtle
  1.5dp `MaterialTheme.colorScheme.primary` outline glow; all other (completed) bands
  are drawn shorter (60dp, vertically centered) and at 60% alpha, so the eye is drawn to
  the active one immediately without other cycles disappearing entirely.
- **Future projection — honest, not invented.** Beyond the active cycle's own end
  (`startDate + totalWeeks*7`, only known for `NORMAL`/`POST`), the timeline draws:
  - a **solid continuation band** only as far as the `onCompleteAction`/`nextCycleId`
    chain actually reaches (e.g. active `NORMAL` → chained `POST` cycle drawn solid;
    `STANDARD` cycles draw as a solid band with a fading/dissolving right edge instead
    of a hard stop, since "unbounded" has no end to draw);
  - past that point, a **diagonal-hatched, low-alpha "neplánováno" band** with no cycle
    name — it deliberately looks *unfinished*, not like a placeholder cycle, so the user
    never mistakes "nothing planned yet" for an actual scheduled cycle. Tapping it does
    nothing but show a small tooltip: "Zatím nic naplánováno".
- **Past cycles.** `COMPLETED` cycles render from `getCompletedCycles()` using
  `startDate` as the left edge. Right edge: `NORMAL`/`POST` use the computed
  `startDate + totalWeeks*7`; `STANDARD` cycles (whose actual end date isn't recorded —
  see Open Question) render with the same soft-fade right edge as an unbounded band,
  which is visually honest given the data we actually have, rather than fabricating a
  precise end.
- **Interaction.** Horizontal drag/fling to pan. Tapping any cycle band navigates to
  `Destination.CycleSettings` (if it's the active cycle) or a read-only cycle detail
  (if completed — reuses the existing `CycleHistoryScreen` row data). Tapping a bare day
  tick within the ±21-day dense window scrolls the 14-day agenda below to that date if
  it's within range, otherwise does nothing (out-of-agenda-range days aren't meant to be
  "opened", just seen in context).

### 2. Active-cycle status card

Kept essentially as today's `CycleDashboardHeader` — it already carries the right
information (name, type label, start date, elapsed days, remaining days/end date or
"Běží neomezeně", gear → `CycleSettings`) — with one addition: a thin
`LinearProgressIndicator` (elapsed/total weeks) directly under the name row for
`NORMAL`/`POST` cycles, giving a second, more precise reading of progress than the
timeline band alone. `STANDARD` cycles and the no-active-cycle state are unchanged
(`NoCycleHeader`'s "+ Nový cyklus" stays as-is).

Placement: directly below the timeline strip, so the sequence reads top-to-bottom as
"the big picture → today's specific cycle → the next two weeks in detail".

### 3. 14-day agenda

A single `LazyColumn` (today + next 13 days), grouped into week sections with a
**sticky header** per week ("Tento týden" for the first 7-day block starting today,
"Příští týden" for the second) rendered as a full-width, tonal
`colorScheme.secondaryContainer` bar — a clearly different, heavier treatment than the
per-day separation described below, so week boundaries are unmistakably a bigger break
than day boundaries.

- **Today (index 0): exhaustive.** Rendered as today's screen already does — a full
  `DoseItem` `Card` per dose, split "V rámci cyklu {name}" / "Ostatní" exactly as now,
  each with its status-toggle `IconButton`. Framed inside an outer `Card` with a
  `colorScheme.primaryContainer`-tinted top strip and a "DNES" label chip, so even
  mid-scroll the user can't mistake which day they're looking at.
- **Days 1–13: simplified.** Each future day is a single-row `ListItem`, not a `Card`
  per dose: leading a fixed-width "date rail" (day-of-week abbreviation, e.g. "ST", over
  the day number, e.g. "23" — reusing the same two-line compact date treatment across
  every row for scanability), trailing a summary — dose count + up to 3 time chips
  (e.g. "3 dávky · 08:00 · 14:00 · 20:00", overflowing to "… a další 2" beyond 3) using
  `AssistChip`-style small tonal chips, tinted by whether any dose in that day already
  belongs to the active cycle (`primaryContainer`) vs. only standalone medications
  (`surfaceVariant`). A day with zero scheduled doses renders as a visually muted single
  line, "Žádné dávky", `onSurfaceVariant` color, no chips — present in the list (so the
  day's existence and its position in the week grid isn't lost) but intentionally
  unemphasized.
- **Day separation:** a 1dp `HorizontalDivider` at `outlineVariant` alpha between every
  row, plus the date-rail column itself acts as a persistent visual anchor per row (its
  fixed 40dp width, distinct from the flexible summary content, is what actually reads
  as "each of these is one day" at a glance — the divider alone would be too subtle).
- **Week separation:** the sticky tonal header described above — a full change of
  background color and an increase in vertical padding (20dp above/below vs. 8dp between
  day rows), so it reads as a structural break, not just another divider.
- **Today indicator within the list:** beyond the bigger "today" card treatment itself,
  the date rail for today additionally gets a filled `primary`-colored circle behind the
  day number (matching the existing `CheckCircle`/status-icon color language), so it's
  recognizable even in a quick glance at the left edge of the list.
- **Scroll behavior:** continuous vertical scroll (not paged) — 14 days is short enough
  that paging would add friction without a real benefit; the sticky week headers already
  give the paging-like "which section am I in" cue for free.

### Color & typography

No new type scale — reuse existing `MaterialTheme.typography` tokens exactly as
`StyleGuide.kt`'s typography manual documents them. Color additions are limited to three
named cycle-type colors (`CycleNormal`/`CycleStandard`/`CyclePost`, derived from the
existing primary/tertiary/secondary hues rather than arbitrary new hues, so light/dark
theme derivation stays automatic via Material3's color scheme generation) plus reuse of
existing container/outline/onSurfaceVariant tokens everywhere else described above.

## Edge cases

| Case | Handling |
|---|---|
| No active cycle at all | Timeline still renders (shows only `COMPLETED` cycles, if any, plus an all-hatched "neplánováno" band across the whole future half); status-card slot falls back to today's existing `NoCycleHeader` ("+ Nový cyklus"); 14-day agenda renders unchanged (doses aren't gated on having a cycle — standalone medications keep working exactly as now). |
| Active cycle ends partway through the 14-day window, nothing chained (`onCompleteAction = TO_NONE`) | The agenda's day rows after the cycle's end date simply show whatever standalone (non-cycle) doses exist, or "Žádné dávky" — no fabricated cycle continuation. The timeline's hatched band starts exactly at that end date. |
| `STANDARD` (unbounded) active cycle | Timeline band fades rather than stopping; status card shows "Běží neomezeně" (unchanged); every future day in the agenda that falls within it keeps normal (non-hatched) chip tinting, since it genuinely is "within" an active, ongoing cycle. |
| Long run of empty days (no doses scheduled) | Each renders as the muted "Žádné dávky" row — deliberately not collapsed/hidden, so the day grid stays a reliable 14-row calendar rather than a variable-length list that reflows unpredictably. |
| A single day with 8+ doses | Today's exhaustive view scrolls within its section as it does today (no cap). A *future* day like this still condenses to the count+chip summary ("8 dávek · 07:00 · 08:00 · 09:00 · … a další 5") — the whole point of the simplified treatment is that it doesn't grow with dose count. |
| First-time user, zero history | Timeline shows an all-hatched strip with a single small centered label overlay, "Zatím žádné cykly"; status card shows `NoCycleHeader`; agenda shows 14 "Žádné dávky" rows (or real standalone-medication doses if the user has any configured without a cycle) — no separate empty-state screen needed, the calendar shell itself communicates "nothing yet" honestly. |

## Architecture changes needed

- `MedicationRepository` gains one new method,
  `fun getDosesInRange(start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>>`
  (all medications, not scoped to one) — a thin wrapper over the existing
  `DoseDao.getDosesInTimeRange(startTime, endTime)`, mirroring how
  `getDosesForMedicationInRange` already wraps the medication-scoped variant. Used once
  by the new ViewModel to fetch the whole 14-day window in a single flow instead of 14
  separate `getDosesForDate` subscriptions.
- `MedicationRepository` gains `fun getCyclesOverlappingRange(start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Cycle>>>` OR — simpler, and sufficient given how few cycles typically exist — the ViewModel just combines the existing `getActiveCycle()` + `getCompletedCycles()` flows and does the date-window filtering/chain-walking (via `nextCycleId`) in Kotlin. Recommended: the latter, to avoid a new DAO query for what's realistically always a small in-memory list.
- New `TodayState` (or a renamed `CalendarState`) shape:
  `activeCycle: Data<Cycle?>`, `completedCycles: Data<List<Cycle>>`,
  `dosesInWindow: Data<List<Dose>>` (the 14-day range), `selectedDoseId: Long?` — the
  ViewModel derives per-day groupings and the timeline's band list from these three
  flows rather than fetching per-day.
- No Room schema changes required for the core redesign — the timeline's "past
  `STANDARD` cycle end date is unknown" limitation is accepted as a known gap (see Open
  Question) rather than solved by a migration in this pass.

## Grafický manuál additions

New entries under `ComponentsManual()` / a new manual category ("Časová osa" alongside
Typografie/Ikony/Barvy/…):

- **Cycle timeline band** — the ribbon segment component (active vs. completed vs.
  hatched-unplanned states) shown standalone with example data.
- **Agenda day row** — the condensed future-day `ListItem` (date rail + summary chips),
  shown in its three tinted states (cycle-linked / standalone-only / empty).
- **Week section header** — the tonal sticky header, shown as a static example.

## Open Question

`CycleEntity`/`Cycle` has no `endDate` field — completion is inferred
(`startDate + totalWeeks*7`) for `NORMAL`/`POST`, and simply unknown for `STANDARD`
cycles that were manually ended (only `status` flips to `COMPLETED`, no timestamp of
*when*). This redesign's past-cycle timeline rendering works around that with the
soft-fade treatment described above, but if precise historical timeline accuracy
becomes important later, a follow-up spec should add a nullable `endedAt`/`endDate`
column (Room's existing `fallbackToDestructiveMigration` convention makes this cheap
whenever it's actually needed — not proposed as part of this pass).

## Testing

Once an implementation plan follows this spec:

- ViewModel: date-window derivation (per-day grouping, week-boundary math, today-index)
  against fake repository data via `Turbine`, mirroring `TodayViewModelTest`'s existing
  style.
- Timeline band computation: pure-function unit tests for "given active cycle + chain +
  completed cycles, produce the list of bands with correct date ranges and
  solid/hatched flags" — kept separate from Compose so it's fast/deterministic to test.
- Manual verification via the `run` skill once implemented, checked against this doc's
  edge-case table.
