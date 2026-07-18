# Medication Assistant v2 — Multi-Reminder Scheduling, Notification Confirm/Adjust, Weekly Report

## Context

DoseTracker today lets a user add medications with a single free-text
`frequency` field and at most one `reminderTime`, receive a single daily
notification (which just opens the app on tap), and manually log doses via
`AddDoseScreen`. There is no mechanism that generates "pending" dose
occurrences from a schedule, no way to confirm or adjust a dose directly
from the notification, and no reporting of adherence over time.

The goal of this spec is to close that gap: let a medication have multiple
recurring reminders (several times a day, specific weekdays), let the user
confirm/skip/snooze a dose straight from the notification (or adjust its
amount/time in-app), and show a weekly report of how much of each
medication was actually taken.

## Current state (reference)

- `Medication` (`shared/.../domain/model/Medication.kt`): `id, name,
  dosage, unit, frequency: String, reminderTime: String?`. Only one
  reminder time per medication; `frequency` is a free-text display label,
  not structured data.
- `Dose` (`shared/.../domain/model/Dose.kt`): `id, medicationId,
  medicationName, timestamp, amount, unit, status
  (TAKEN/MISSED/SKIPPED/PENDING)`. Already supports a per-dose
  amount/unit override.
- `ReminderScheduler` (`app/.../reminder/ReminderScheduler.kt`): schedules
  one exact `AlarmManager` alarm per medication from `reminderTime`,
  reschedules itself for +1 day each time it fires. `RescheduleWorker` /
  `RescheduleReceiver` re-arm all medications' alarms on boot / app
  upgrade.
- `ReminderReceiver` (`app/.../reminder/ReminderReceiver.kt`): on alarm
  fire, calls `NotificationHelper.showNotification(...)` — a plain
  notification with no action buttons, tapping it just opens
  `MainActivity`. **No `Dose` row is created anywhere in this flow.**
- `AddDoseScreen`/`AddDoseViewModel`: manual, ad-hoc dose entry only —
  always inserts a new `Dose` with `status = TAKEN`. This is the only
  place a `Dose` row is ever created today.
- `TodayScreen`/`TodayViewModel`: lists whatever `Dose` rows already exist
  for today's date range (`getDosesForDate`); tapping a row toggles
  TAKEN/PENDING in place, tapping the card body navigates to
  `MedicationDetail` (currently a placeholder).
- `HistoryScreen`: flat list of all doses ever logged, newest first.
- No weekly/adherence report exists.
- `AppDatabase` is at schema `version = 1`, `exportSchema = false`, no
  `Migration` classes, no `fallbackToDestructiveMigration()` configured
  explicitly in `DatabaseBuilder.android.kt`.
- Manifest already declares `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`.

## Goals

1. A medication can have multiple recurring reminders: several times a
   day, each restricted to a chosen subset of weekdays.
2. Each scheduled occurrence materializes into a real `Dose` row
   (`PENDING`) ahead of time, so the notification, the Today screen, and
   the weekly report all operate on the same concrete data.
3. The reminder notification offers three direct actions — **Taken**,
   **Skip**, **Snooze** — plus tapping the notification body opens an
   in-app screen to adjust the amount/time before confirming.
4. A dose left unconfirmed for 2 hours past its scheduled time
   automatically becomes `MISSED`.
5. A new Report screen shows, per medication, how many doses were
   taken/missed/skipped in a given calendar week (Mon–Sun), with
   previous/next week navigation.

## Non-Goals

- Preserving existing installed data across the schema change — the app
  has no real user data yet, so `fallbackToDestructiveMigration()` is used
  instead of writing `Migration` classes.
- Configurable missed-dose timeout (hardcoded 2h constant for this spec).
- Push/notification delivery of the weekly report (in-app screen only).
- Medication stock/refill tracking, adherence streaks, CSV/PDF export —
  noted as ideas during brainstorming, explicitly out of scope here.
- Editing the missed-dose window, snooze duration, or notification action
  set per-medication — all are fixed app-wide constants for now.

## Data model

`Medication` loses `frequency` and `reminderTime`:

```kotlin
data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: String,
)
```

New `ReminderSchedule`, one medication has many:

```kotlin
data class ReminderSchedule(
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int,   // 0..1439, local time of day
    val daysOfWeek: Int,     // bitmask, bit 0 = Monday .. bit 6 = Sunday
    val enabled: Boolean = true,
)
```

`Dose` gains a nullable back-reference to the schedule that generated it
(`null` for ad-hoc entries made via `AddDoseScreen`):

```kotlin
data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long? = null,
    val medicationName: String = "",
    val timestamp: Instant,
    val amount: Double? = null,
    val unit: String? = null,
    val status: DoseStatus = DoseStatus.PENDING,
)
```

Room: new `ReminderScheduleEntity` table (FK to `medications`, cascade
delete), `DoseEntity` gets a `scheduleId: Long?` column with a unique
index on `(scheduleId, timestamp)` to make dose generation idempotent.
`AppDatabase` bumps to `version = 2`; `DatabaseBuilder.android.kt` adds
`.fallbackToDestructiveMigration(dropAllTables = true)`. The `seedCallback`
in `AppDatabase` is rewritten to seed `ReminderSchedule` rows instead of
the old `reminderTime`/`frequency` columns.

## Scheduling engine

- `DoseReminderScheduler` interface changes from operating on a
  `Medication` to operating on a `ReminderSchedule` occurrence: schedule
  one exact `AlarmManager` alarm carrying a `doseId` (not a
  `medicationId`), plus a second "timeout" alarm at `scheduledTime + 2h`
  for the same `doseId`.
- New `DoseGenerator` (in `:shared`, framework-agnostic): given "today",
  finds every enabled `ReminderSchedule` whose `daysOfWeek` includes
  today, and for each one not already represented by a `Dose` row
  (`scheduleId`, today), inserts `Dose(status = PENDING, timestamp =
  today at minutesOfDay, amount = null, unit = medication.unit)`. For
  every row it inserts, it schedules that occurrence's reminder alarm and
  its timeout alarm via `DoseReminderScheduler`.
- `DoseGenerator` runs from three triggers, all reusing/extending the
  existing `RescheduleWorker`/`RescheduleReceiver` machinery:
  1. A daily WorkManager job at local midnight.
  2. `RescheduleReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
     `QUICKBOOT_POWERON`) — already exists, extended to call
     `DoseGenerator` for today in addition to re-arming alarms.
  3. Whenever a `ReminderSchedule` is created/edited/deleted (from the
     new medication detail screen) — backfills/cancels today's occurrence
     immediately so a same-day edit takes effect without waiting for
     midnight.
- `ReminderReceiver` simplifies: on alarm fire it now receives a
  `doseId`, loads that `Dose` (already `PENDING` in the DB), and shows the
  actionable notification (Section below). No DB writes happen here
  beyond what the action buttons do.

## Notification actions

- `NotificationHelper.showNotification` gains three
  `NotificationCompat.Action`s, each a `PendingIntent` to a new
  `DoseActionReceiver` (`exported = false`) carrying `doseId` and an
  action extra:
  - **Taken** → `updateDose(dose.copy(status = TAKEN))`, cancel the
    notification, cancel that dose's timeout alarm.
  - **Skip** → `updateDose(dose.copy(status = SKIPPED))`, same
    cancellations.
  - **Snooze** → re-schedule a new one-off alarm for `now + 15min` on the
    same `doseId`, dismiss the current notification, leave `status =
    PENDING` and the original timeout alarm untouched (so repeated
    snoozing past the 2h window still resolves to `MISSED`).
- Tapping the notification body (not an action button) opens
  `ConfirmDoseScreen(doseId)` — a new screen, pre-filled with the
  medication's default dosage and the scheduled time, both editable. Its
  "Save" button calls `updateDose` (not insert, the row already exists)
  with `status = TAKEN` and the edited amount/time.
- `TodayScreen`'s tap-on-card behavior changes: tapping a `PENDING` dose
  opens `ConfirmDoseScreen(dose.id)` instead of `MedicationDetail`. The
  existing quick-toggle icon button keeps doing the fast TAKEN/PENDING
  flip. Tapping into a medication's schedule (add/edit reminders) moves to
  a dedicated entry point on `MedicationCatalogScreen`'s medication row.

## Auto-missed handling

Implemented as the "timeout" alarm described above: fires at
`scheduledTime + 2h`, and if the target `Dose` is still `PENDING`, updates
it to `MISSED` and cancels/dismisses its notification if still showing.
This is a fixed 2-hour constant for this spec (see Non-Goals).

## Weekly report

- New `ReportScreen` + `ReportViewModel`, new `Destination.Report`, added
  as a fourth item in `DoseTrackerAppMain`'s navigation suite.
- State holds `weekStart: LocalDate` (Monday of the displayed ISO week),
  defaulting to the current week; `<`/`>` controls move it by ±7 days.
- Data comes from the existing `DoseDao.getDosesInTimeRange` query for
  `[weekStart, weekStart + 7days)`, joined with medication names (already
  returned as `DoseWithMedication`).
- `ReportViewModel` aggregates in Kotlin (data volume is small — no new
  SQL aggregation needed): per medication, counts of `TAKEN`/`MISSED`/
  `SKIPPED` among doses whose scheduled time has already passed, plus a
  separate "upcoming" count for `PENDING` doses still in the future
  (excluded from any percentage).

## UI/navigation changes summary

- `MedicationDetailPlaceholder` is replaced by a real medication detail
  screen: medication info plus a list of its `ReminderSchedule`s with
  add/edit/delete, each schedule editable as a time-of-day + weekday
  multi-select.
- `AddMedicationDialog` (in `MedicationCatalogScreen`) drops the
  "Frequency" text field; reminder schedules are configured afterward on
  the detail screen, not at creation time.
- `AddDoseScreen`/`AddDoseViewModel` are unchanged — still the path for
  logging an ad-hoc dose with no schedule behind it.
- `Destinations.kt` gains `ConfirmDose(doseId: Long)` and `Report`.

## Phasing

1. **Data model + generation + scheduling engine.** `ReminderSchedule`
   entity/DAO, `Dose.scheduleId`, `DoseGenerator`, `DoseReminderScheduler`
   reworked to schedule per-occurrence (not per-medication), `Reminder`/
   `Reschedule` receivers and workers wired to the generator. Medication
   detail screen gets basic schedule CRUD (no notification actions yet —
   `ReminderReceiver` still shows a plain notification, just now backed by
   a real `Dose` row). This phase is the foundation everything else
   depends on.
2. **Notification actions + confirm/adjust + auto-missed.**
   `DoseActionReceiver`, the three notification actions, `ConfirmDoseScreen`,
   the timeout alarm and `MISSED` transition, `TodayScreen`'s tap behavior
   change.
3. **Weekly report.** `ReportScreen`, `ReportViewModel`, nav entry.

## Testing

- `DoseGenerator`: unit tests (JUnit5, per `android-testing` conventions)
  covering weekday-bitmask matching, idempotency (running twice for the
  same day doesn't duplicate rows), and backfill-on-edit.
- `ReportViewModel`: unit tests for aggregation counts and week-boundary
  math (Monday start, week rollover), following the existing
  `TodayViewModelTest` pattern (fake repository, `UnconfinedTestDispatcher`,
  Turbine).
- `DoseActionReceiver` / alarm scheduling: manual verification on-device
  (exact-alarm and notification-action behavior isn't practically unit
  testable) — schedule a reminder a few minutes out, confirm Taken/Skip/
  Snooze each behave correctly, confirm the 2h timeout flips an untouched
  dose to `MISSED`.
- Manual smoke test of the full loop: add a medication with two daily
  reminder times on weekdays only, confirm today's doses appear in
  `TodayScreen`, confirm one from the notification, adjust one via
  `ConfirmDoseScreen`, check both land correctly in `ReportScreen` for the
  current week.
