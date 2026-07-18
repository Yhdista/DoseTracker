# DoseTracker KMP Migration — Phase 1: Extract `:shared` module

## Context

DoseTracker is currently a single-module Android app (`:app`, package
`com.yhdista.dosetracker`) built with Jetpack Compose, Room, and Hilt. The
goal is to eventually ship DoseTracker on iOS and Desktop (JVM) as well,
sharing not just business logic but the Compose UI itself via Compose
Multiplatform.

This is a large change, so it will be done in phases, following the same
incremental philosophy the sibling project VLACHOVI_CZ used for its own
`:shared` KMP module (see that module's own note about merging 5
over-granular submodules back into one — avoid premature module
granularity).

**This spec covers Phase 1 only**: extracting a `:shared` Kotlin
Multiplatform module with `androidTarget()` as its only target. The app's
behavior, build output, and installed package do not change — this phase
is a structural refactor, not a feature change. iOS and Desktop targets are
explicitly deferred to later phases (see Non-Goals).

## Current state (reference)

Source layout in `app/src/main/java/com/yhdista/dosetracker/`:
- `domain/model/` — `Dose`, `Medication` (plain Kotlin data classes)
- `domain/repository/` — `MedicationRepository` interface
- `data/local/` — Room `AppDatabase`, DAOs, Entities, `Converters`
- `data/mapper/` — plain Kotlin mapper functions
- `data/repository/MedicationRepositoryImpl` — depends on the DAOs, the
  mappers, and directly on `reminder/ReminderScheduler`
- `di/` — `DatabaseModule`, `RepositoryModule` (Hilt `@Module`s)
- `reminder/` — `NotificationHelper`, `ReminderReceiver`,
  `ReminderScheduler` (WorkManager-based), `RescheduleReceiver`,
  `RescheduleWorker` — all Android framework APIs (WorkManager,
  `NotificationManager`, `BroadcastReceiver`)
- `ui/` — Compose screens + ViewModels for catalog/dose/history/today,
  navigation, theme
- `MainActivity`, `DoseTrackerApp`

Confirmed by grep: `androidx.camera.*`, `accompanist-permissions` (used
only for the `POST_NOTIFICATIONS` runtime permission, not camera),
`retrofit`/`okhttp`/`moshi` are all present in `app/build.gradle.kts` but
**unused** in source today. No networking code exists yet. This
significantly reduces Phase 1 scope — there is no Retrofit→Ktor migration
to do until networking is actually added.

Existing tests: `ExampleUnitTest`, `ExampleInstrumentedTest`,
`TodayViewModelTest`.

The `distribute` Gradle task (Firebase App Distribution) added earlier
lives entirely in `:app` and assembles/uploads the app APK — it is
unaffected by this module split.

## Goals

1. Split the codebase into `:shared` (platform-agnostic) and `:app`
   (Android-only shell), with `:shared` building as a KMP module
   (`androidTarget()` only for now).
2. Replace Hilt with Koin for everything that moves into `:shared`, since
   Hilt cannot run on iOS/Desktop and shared code must stay DI-framework
   compatible with future targets.
3. Introduce a common `DoseReminderScheduler` interface so
   `MedicationRepositoryImpl` (which moves to `:shared`) no longer depends
   directly on the Android-only WorkManager implementation.
4. Keep the app fully working and installable on Android exactly as
   before — same `applicationId` (`com.yhdista.dosetracker`, no `.android`
   suffix), same behavior, same Firebase App Distribution setup.

## Non-Goals (deferred to later phases)

- Adding actual iOS or Desktop targets/entry points (Phase 2+).
- iOS/Desktop `actual` implementations of `AppDatabase` construction and
  `DoseReminderScheduler`.
- Any networking layer (Ktor or otherwise) — none exists today.
- Any camera/photo feature (CameraX deps are present but unused).

## Module architecture

- **`:shared`** — new Gradle module, KMP (`kotlin.multiplatform` +
  `android.library` plugins), `androidTarget()` as the only target for
  now. Namespace: `com.yhdista.dosetracker.shared` (mirrors the `.shared`
  suffix convention already used by VLACHOVI_CZ's own shared module).
  Contains:
  - `domain/model`, `domain/repository` (moved as-is)
  - `data/mapper` (moved as-is)
  - `data/local/*` — Room `AppDatabase`, DAOs, Entities, `Converters`.
    Room 2.8.4 (already the pinned version) supports KMP via
    `RoomDatabaseConstructor` and the bundled SQLite driver. Only the
    Android `actual` for building the database instance is implemented in
    this phase.
  - `data/repository/MedicationRepositoryImpl` — moved, but its
    dependency on `ReminderScheduler` becomes a dependency on the new
    common `DoseReminderScheduler` interface instead.
  - `ui/*` — Compose screens, ViewModels, navigation, theme, converted to
    Compose Multiplatform (`org.jetbrains.compose` plugin). ViewModels
    drop `@HiltViewModel` in favor of plain classes provided via Koin.
  - Koin modules (replacing `di/DatabaseModule` and `di/RepositoryModule`).
- **`:app`** — existing module, becomes a thin Android shell. Package
  stays `com.yhdista.dosetracker`. Contains:
  - `MainActivity`
  - `DoseTrackerApp` (Application class), now calling `startKoin { ... }`
    instead of using `@HiltAndroidApp`
  - `reminder/*` — unchanged, stays here (Android framework APIs:
    WorkManager, `NotificationManager`, `BroadcastReceiver`). The existing
    `ReminderScheduler` becomes the Android `actual` implementation of
    `DoseReminderScheduler`.
  - AndroidManifest, resources
  - The `distribute` task and Firebase App Distribution config (already
    in place, unaffected)

## DI migration: Hilt → Koin

- `di/DatabaseModule.kt`, `di/RepositoryModule.kt` → Koin `module { }`
  definitions living in `:shared`, using `single`/`factory` instead of
  `@Provides`.
- All `hiltViewModel()` call sites in Compose screens → `koinViewModel()`.
- `DoseTrackerApp`: `@HiltAndroidApp` + Hilt's generated Application base
  → `startKoin { androidContext(this@DoseTrackerApp); modules(...) }`.
- Once the rewrite is verified, remove Hilt plugin/deps from
  `app/build.gradle.kts` (`hilt-android`, `hilt-compiler`,
  `androidx-hilt-work`, the Hilt Gradle plugin alias) and from
  `libs.versions.toml` if nothing else references them.

## `DoseReminderScheduler` abstraction

- New interface `DoseReminderScheduler` in a new `reminder` package under
  `:shared`. Its method signatures mirror exactly what
  `MedicationRepositoryImpl` currently calls on today's `ReminderScheduler`
  (schedule/cancel for a given dose) — no new methods, no behavior change.
- `:app`'s existing `ReminderScheduler` implements this interface
  (Android `actual`, using WorkManager as it does today).
- Koin wires the Android implementation to the interface at app startup.
- iOS/Desktop implementations are out of scope for this phase (Non-Goals).

## Gradle changes

- `settings.gradle.kts`: add `include(":shared")`.
- `shared/build.gradle.kts`: `kotlin.multiplatform` + `android.library`
  plugins, `androidTarget()`, `org.jetbrains.compose` for Compose
  Multiplatform UI, Koin (`koin-core`, `koin-compose`), Room (with KSP
  configured per source set for the Android target).
- `app/build.gradle.kts`: add `implementation(project(":shared"))`; remove
  dependencies that move to `:shared` (Room, Hilt, Moshi — Moshi only if
  confirmed unused after the move); the `distribute` task and
  `firebaseAppDistribution` block stay untouched.
- `gradle/libs.versions.toml`: add `kotlin-multiplatform` plugin alias,
  `compose-multiplatform` (`org.jetbrains.compose`), Koin library/version
  entries.

## Verification plan

1. `./gradlew :app:assembleDebug` builds successfully.
2. Existing tests pass: `ExampleUnitTest`, `ExampleInstrumentedTest`,
   `TodayViewModelTest` (moved/adjusted for the new module layout as
   needed — `TodayViewModelTest` likely moves to `:shared` since
   `TodayViewModel` does).
3. Manual smoke test on a device/emulator: launch the app, add a dose,
   check the history and catalog screens, confirm a reminder notification
   still fires as before.

## Firebase / App Distribution note

If a Firebase Android app was already registered under
`com.yhdista.dosetracker.android`, it must be deleted and re-added under
the correct package name (`com.yhdista.dosetracker`) before
`google-services.json` is downloaded — otherwise the package name won't
match `applicationId` and the `google-services` plugin will fail. This is
independent of the KMP migration itself but was the reason the `.android`
suffix question came up.
