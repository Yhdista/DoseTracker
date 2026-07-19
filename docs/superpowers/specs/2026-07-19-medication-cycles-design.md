# Medication Cycles (Cyklus / Standardni cyklus / Post-cyklus)

## Context

An earlier spec (`docs/superpowers/specs/2026-07-19-medication-quantity-history-design.md`)
explicitly deferred a "cycles" concept ("a bulking cycle taking more
medications vs. a standard cycle") as future work, noting it wouldn't
require schema changes to the quantity-history feature. This spec builds
that deferred concept.

Today, medications are configured individually: each `Medication` has zero
or more `ReminderSchedule`s (when/how much, via `WEEKDAYS`/`INTERVAL` +
`EXACT`/`PERIOD` time — configured in `MedicationDetailScreen`'s
`ScheduleDialog`). `DoseGenerator` materializes `DoseEntity` rows ahead of
time from all enabled schedules. The "Today" screen shows one flat list of
today's doses (`TodayViewModel`/`TodayContent`), with no notion of a
higher-level, time-boxed protocol grouping several medications together.

The user wants to define a **Cyklus** (cycle): a named, multi-week protocol
where the set of medications and their schedule can differ from week to
week (e.g. a titration ramp). The Today screen becomes a dashboard showing
the active cycle's progress, and doses belonging to the cycle are visually
separated from unrelated, standalone medication doses.

## Goal

- User creates a **Cyklus**: name, number of weeks, and per-week
  medication schedules (reusing the existing schedule UI/mechanism).
- Today screen shows a dashboard header when a cycle is active: name,
  start date, elapsed time, time remaining.
- Today's dose list splits into "part of the active cycle" vs. "other"
  (standalone medications not in any cycle).
- When a timed cycle finishes, the app automatically transitions to a
  follow-up state, per a small state machine (see Cycle lifecycle).

## Non-Goals

- No support for multiple concurrently active cycles — exactly one cycle
  can be `ACTIVE` at a time.
- No mid-cycle editing tools beyond what's needed to set it up (no
  "copy previous week" convenience, no drag-to-reorder weeks) — YAGNI for
  v1, each week's medications/schedule are configured independently.
- No real Room migration — this feature uses the existing
  `fallbackToDestructiveMigration(dropAllTables = true)` convention.
  Bumping `AppDatabase.version` will wipe local data on upgrade, same as
  every prior schema change in this project. (Explicitly requested by the
  user to skip writing a migration for this feature.)
- No scheduling a cycle to start in the future — creating/activating a
  cycle always starts it `today`.
- No editing an already-*completed* (archived) cycle's history.

## Current state (reference)

- `AppDatabase` (`data/local/AppDatabase.kt`): `@Database(entities =
  [MedicationEntity, DoseEntity, ReminderScheduleEntity, PeriodTimeEntity],
  version = 4)`.
- `ReminderScheduleEntity` (`data/local/entity/ReminderScheduleEntity.kt`):
  `id, medicationId (FK cascade), minutesOfDay, daysOfWeek, enabled,
  scheduleType, intervalDays, startDate, timeType, dayPeriod`.
- `DoseEntity` (`data/local/entity/DoseEntity.kt`): `id, medicationId (FK
  cascade), scheduleId (no FK — deliberate, so deleting a schedule doesn't
  cascade-delete dose history), timestamp, amount, unit, status`.
- `DoseGenerator` (`reminder/DoseGenerator.kt`): `runForToday()`/
  `runForDate(date)` iterate all enabled `ReminderSchedule`s, check
  `matchesDate()` (weekday bitmask or interval/startDate math), and
  create/update `Dose` rows + alarms.
- `TodayViewModel`/`TodayContent` (`ui/today/`): `TodayState(doses:
  Data<List<Dose>>, selectedDoseId: Long?)`, one flat `LazyColumn` of
  `DoseItem`s sourced from `MedicationRepository.getDosesForDate(date)`.
- `MedicationDetailScreen`'s `ScheduleDialog`: the existing UI for
  configuring one `ReminderSchedule` (time type EXACT/PERIOD, frequency
  WEEKDAYS/INTERVAL) — this is reused for cycle week schedules.
- `MedicationRepository`/`MedicationRepositoryImpl`: single fat repository
  interface over all four DAOs; `di/DataModule.kt` wires `AppDatabase` +
  DAOs + repository + `DoseGenerator`; `di/ViewModelModule.kt` wires one
  `viewModel {}` per screen.
- No existing "cycle"/"course"/"regimen" concept anywhere in the codebase
  — greenfield.
- Unrelated in-progress work on `develop` (uncommitted): a "Grafický
  manuál" style-guide screen (`ui/debug/StyleGuide.kt`, new `Style*`
  destinations, `DebugScreen.kt` entry point). Additive, different files —
  no collision with this feature, except that the style guide renders
  `ui.today.DoseItem` as a live example, so it will pick up any visual
  changes to `DoseItem` automatically.

## Data model

Two new Room entities (`data/local/entity/`):

```kotlin
enum class CycleType { NORMAL, STANDARD, POST }
enum class CycleStatus { ACTIVE, COMPLETED }
enum class CycleCompleteAction { TO_STANDARD, TO_POST, TO_NONE }

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CycleType,
    val totalWeeks: Int?,              // null for STANDARD (unbounded)
    val startDate: String,             // ISO LocalDate
    val status: CycleStatus,
    val onCompleteAction: CycleCompleteAction,
    val nextCycleId: Long?,            // pre-attached POST cycle, if any
)

@Entity(
    tableName = "cycle_weeks",
    foreignKeys = [ForeignKey(
        entity = CycleEntity::class, parentColumns = ["id"],
        childColumns = ["cycleId"], onDelete = ForeignKey.CASCADE,
    )],
)
data class CycleWeekEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val weekIndex: Int,                 // 0-based; STANDARD always has exactly one row (weekIndex = 0)
)
```

Changes to existing entities:

- `ReminderScheduleEntity` gains `cycleWeekId: Long?` (FK cascade to
  `CycleWeekEntity`, nullable — `null` means a standalone schedule,
  unaffected by any cycle).
- `DoseEntity` gains `cycleId: Long?` — **denormalized, no FK**, written
  once at dose-generation time. This mirrors the existing `scheduleId`
  design: cycle association on a dose must survive the cycle (or its
  schedule) being deleted later, so history/grouping stays intact.

Domain models mirror this: `Cycle`, `CycleWeek` added under `domain/model/`;
`Dose.cycleId: Long?` added; mappers (`toDomain()`/`toEntity()`) updated
accordingly.

Exactly one `STANDARD`-type `CycleEntity` exists at a time — it's created
once (first time the user configures it) and edited in place afterward;
the create/edit flow never produces a second `STANDARD` row. This keeps
the `TO_STANDARD` lookup in the lifecycle section below unambiguous (there
is always at most one candidate to find and reactivate).

`AppDatabase.entities` gains `CycleEntity::class, CycleWeekEntity::class`,
version bumps (destructive migration, per Non-Goals). New `CycleDao` with
standard CRUD + `getActiveCycle(): Flow<CycleEntity?>` +
`getCompletedCycles(): Flow<List<CycleEntity>>`. `MedicationRepository`
gains cycle methods (kept in the one fat repository, matching existing
convention) — `getActiveCycle()`, `createCycle(...)`, `getCycleHistory()`,
etc. — backed by `CycleDao`.

## Cycle lifecycle (state machine)

**States:** at most one `CycleEntity` has `status = ACTIVE` at any time;
everything else is `COMPLETED` or not yet activated.

**Completion detection:** runs as part of the existing
`DoseGenerator.runForToday()` entry point (already invoked on every
schedule change and on a daily/app-open basis). For the active cycle,
compute:

```
weekIndex = floor((today - cycle.startDate) / 7)
```

- `STANDARD`: `weekIndex` is never used to end the cycle — it never
  completes on its own (`totalWeeks == null`). Dose generation always
  reads the single `weekIndex = 0` week.
- `NORMAL` / `POST`: if `weekIndex >= totalWeeks`, the cycle completes:
  `status = COMPLETED`, then apply `onCompleteAction`:
  - `TO_STANDARD` → find the `STANDARD`-type cycle (the user-maintained
    baseline template), set `status = ACTIVE`, `startDate = today`.
  - `TO_POST` → activate `nextCycleId` (must be a `POST`-type cycle),
    `status = ACTIVE`, `startDate = today`.
  - `TO_NONE` → no active cycle.
  - `POST` cycles are always created with `onCompleteAction = TO_NONE`
    (per the confirmed design: a post-cycle always ends into "no active
    cycle", never chains further).

**No missed-transition problem:** because `weekIndex` and completion are
recomputed from `startDate` every time `runForToday()` runs, the app
doesn't need to catch a transition at the exact day it happens — reopening
the app after several idle days recomputes the correct state immediately.

**Dose generation:** `DoseGenerator.matchesDate()` gains one more
condition — a schedule with `cycleWeekId != null` only matches if its
`CycleWeekEntity.weekIndex` equals the active cycle's current
`weekIndex` (from above) **and** that cycle is `ACTIVE`. Standalone
schedules (`cycleWeekId == null`) are unaffected and keep behaving exactly
as today. Every dose row written by the generator for a cycle-linked
schedule gets `cycleId` set to that schedule's cycle (via
`cycleWeekId -> CycleWeekEntity.cycleId`).

## Today dashboard + dose grouping

`TodayState` gains `activeCycle: Cycle?` (via a new
`MedicationRepository.getActiveCycle()` flow, combined into
`TodayViewModel.uiState` alongside the existing doses flow).

`TodayContent` renders, above the dose list, a dashboard header —
only when `activeCycle != null`:

- Cycle name + type label (Cyklus / Standardní cyklus / Post-cyklus)
- Start date
- Elapsed ("běží X dní" / "týden Y")
- Remaining: `NORMAL`/`POST` show "zbývá X dní / končí `DATE`";
  `STANDARD` shows an unbounded/"běží neomezeně" indicator instead (no
  countdown, since `totalWeeks == null`)
- A link to `Destination.CycleHistory`

The dose `LazyColumn` splits into two sections keyed off `Dose.cycleId`:

1. **"V rámci cyklu [název]"** — doses where `cycleId == activeCycle.id`
2. **"Ostatní"** — doses where `cycleId == null` (or belonging to a
   different/past cycle)

When `activeCycle == null`, the list renders as one flat section, same as
today (no visual change from current behavior), and the dashboard header
is replaced by a "+ Nový cyklus" entry point into `Destination.CreateCycle`.

## Cycle creation / editing UI + navigation

New destinations (`Destinations.kt`, following the existing
`MedicationDetail(id)` pattern):

- `Destination.CreateCycle` — new cycle setup form
- `Destination.CycleWeekEditor(cycleId: Long, weekIndex: Int)` — per-week
  medication/schedule editor
- `Destination.CycleHistory` — list of `COMPLETED` cycles

Both wired into `DoseTrackerAppMain.kt`'s `NavDisplay` as `detailPane()`
entries, each with its own `koinViewModel<...>()`, matching every existing
screen.

**Entry points:** Today dashboard, as described above (create when no
active cycle; history link when one is active).

**One-active-cycle constraint on creation:**

- If there is **no** active cycle: creating any cycle (any `type`)
  activates it immediately (`status = ACTIVE`, `startDate = today`).
- If there **is** an active cycle: the create flow only allows two things
  — (a) edit/create the `STANDARD` template (saved but not activated; it's
  picked up later by the `TO_STANDARD` transition), or (b) create a `POST`
  cycle and attach it as the active cycle's `nextCycleId`
  (`onCompleteAction` set to `TO_POST`) — it activates only once the
  current cycle completes.

**`CreateCycle` form fields:**

1. Name, type (`NORMAL`/`STANDARD`/`POST` — pre-filled to `POST` when
   entering via the "attach post-cycle to active cycle" path)
2. Number of weeks — shown only for `NORMAL`/`POST` (hidden for
   `STANDARD`)
3. For `NORMAL`/`POST`: on-completion behavior — `TO_STANDARD` (default)
   or `TO_POST` (pick an existing unattached `POST` cycle, or create one
   inline)
4. On save, navigates to a list of the cycle's weeks (or straight to the
   single week for `STANDARD`) — tapping a week opens `CycleWeekEditor`

**`CycleWeekEditor`:** list of medications currently assigned to this
week + an "add medication" action — picks an existing `Medication` and
configures its schedule via the **same** `ScheduleDialog` component
already used in `MedicationDetailScreen` (EXACT/PERIOD time,
WEEKDAYS/INTERVAL frequency), persisted as a `ReminderScheduleEntity` with
`cycleWeekId` set instead of `null`.

## Testing

Following existing conventions (`android-testing`: JUnit5, Turbine, fake
repositories, `UnconfinedTestDispatcher`):

- **`DoseGenerator`**: unit tests for the `matchesDate()` extension — a
  `cycleWeekId`-linked schedule generates only during its own cycle week
  and nothing outside it; standalone schedules are unaffected.
- **Cycle lifecycle**: unit tests for completion detection and each
  transition (`TO_STANDARD`, `TO_POST`, `TO_NONE`), including the
  "app was closed across a week/cycle boundary" case (recomputed purely
  from `startDate`, no missed-transition state).
- **`TodayViewModel`**: `activeCycle` + dose-list grouping (cycle vs.
  other) verified against fake repository data via `Turbine` on
  `uiState`.
- **Cycle creation / week editor ViewModels**: standard MVI-style tests
  mirroring `MedicationDetailViewModel` (add/update/delete a
  cycle-scoped schedule → correct repository calls +
  `DoseGenerator.runForToday()` invoked).
