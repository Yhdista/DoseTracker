# Per-Medication Quantity History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a medication's card on the weekly Report screen opens a per-medication screen with a horizontally-scrollable bar chart — one bar per calendar week, height = total quantity taken that week — browsable by calendar month (~4-6 bars) or calendar year (~52 bars) via a Month/Year toggle.

**Architecture:** A new repository/DAO method bounds `getDosesInTimeRange`'s existing shape to one medication. A new pure week-bucketing step (reusing a generalized `weekStartOf` extracted from the existing weekly `ReportViewModel`) turns a flat dose list into one `WeekQuantity` per week in the displayed period, including zero-value weeks for continuity. A new `MedicationReportViewModel`/`MedicationReportScreen` pair renders that as a hand-rolled Compose bar chart (no charting library).

**Tech Stack:** Kotlin Multiplatform (`:shared` commonMain), Compose Multiplatform, Material3 (`SingleChoiceSegmentedButtonRow`/`SegmentedButton`, confirmed present in the project's resolved `material3-android:1.4.0`).

## Global Constraints

- No new Gradle dependencies.
- Tests follow the codebase's existing convention exactly: JUnit4, `mockito-kotlin`, `kotlinx-coroutines-test` `StandardTestDispatcher`. Not JUnit5, not Turbine, not AssertK.
- UI copy hardcoded inline in Compose — no `strings.xml`.
- ViewModels that need an id passed from navigation use the manual pattern: a `set<X>Id(id)` method backed by `SavedStateHandle.getStateFlow`, called from a `LaunchedEffect(id)` in the screen composable (test with a real `SavedStateHandle()` instance, matching `TodayViewModelTest`'s convention — not a mock).
- A week's "own" days determine its bucket regardless of month/year boundaries — a week whose Monday falls in the prior period is *not* extended backward to pull in those days; only doses whose timestamp actually falls inside the queried `[periodStart, periodEndExclusive)` range are counted, so a boundary week's bar may under-represent that week's true total. This is a deliberate, documented simplification (see the spec's Non-Goals) — do not "fix" it by widening the query range.
- The working tree may contain unrelated in-progress changes to `ReminderSchedule`/`MedicationDetailScreen`/`MedicationDetailViewModel`/`DoseGenerator`/`AppDatabase` (a "period time" feature) — these are known, intentional, and out of scope. None of the files this plan touches overlap with them. Do not touch, revert, or "clean up" anything outside this plan's file list.
- Build verification command: `./gradlew :app:assembleDebug`.
- Test verification command: `./gradlew :shared:testAndroidHostTest --tests "<FQCN>"`.

---

### Task 1: Repository/DAO range query + `MedicationWeekSummary.medicationId`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModel.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModelTest.kt`

**Interfaces:**
- Produces: `MedicationRepository.getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>>`; `weekStartOf(date: LocalDate): LocalDate` (public top-level in `ReportViewModel.kt`); `MedicationWeekSummary.medicationId: Long` — all consumed by Task 2/3.

- [ ] **Step 1: Extend the existing summary test to assert `medicationId`**

Replace the whole first `@Test` in `ReportViewModelTest.kt` (`summarizes counts and quantities per medication for the current week`) with:

```kotlin
    @Test
    fun `summarizes counts and quantities per medication for the current week`() = runTest {
        val instant = Clock.System.now()
        val doses = listOf(
            Dose(id = 1, medicationId = 1, medicationName = "Aspirin", timestamp = instant, amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 2, medicationId = 1, medicationName = "Aspirin", timestamp = instant, amount = 500.0, unit = "mg", status = DoseStatus.MISSED),
            Dose(id = 3, medicationId = 1, medicationName = "Aspirin", timestamp = instant, amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 4, medicationId = 2, medicationName = "Ibuprofen", timestamp = instant, amount = 400.0, unit = "mg", status = DoseStatus.SKIPPED),
            Dose(id = 5, medicationId = 2, medicationName = "Ibuprofen", timestamp = instant, amount = 400.0, unit = "mg", status = DoseStatus.PENDING)
        )
        whenever(repository.getDosesInWeek(any())).thenReturn(flowOf(Data.Success(doses)))

        val viewModel = ReportViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val summaries = (viewModel.uiState.value.summaries as Data.Success).data
        val aspirin = summaries.first { it.medicationName == "Aspirin" }
        val ibuprofen = summaries.first { it.medicationName == "Ibuprofen" }

        assertEquals(1L, aspirin.medicationId)
        assertEquals(2, aspirin.taken)
        assertEquals(1, aspirin.missed)
        assertEquals(0, aspirin.skipped)
        assertEquals(1000.0, aspirin.totalAmountTaken, 0.0)
        assertEquals(1500.0, aspirin.totalAmountScheduled, 0.0)
        assertEquals("mg", aspirin.unit)

        assertEquals(2L, ibuprofen.medicationId)
        assertEquals(1, ibuprofen.skipped)
        assertEquals(1, ibuprofen.upcoming)
        assertEquals(0.0, ibuprofen.totalAmountTaken, 0.0)
        assertEquals(800.0, ibuprofen.totalAmountScheduled, 0.0)
        assertEquals("mg", ibuprofen.unit)

        job.cancel()
    }
```

The only change from the current test is the two new `assertEquals(1L/2L, ...medicationId)` lines — everything else is unchanged.

- [ ] **Step 2: Add a dedicated test for `weekStartOf`**

Add to `ReportViewModelTest.kt` (a new `@Test`, no `runTest` needed — it's a pure function):

```kotlin
    @Test
    fun `weekStartOf returns the Monday on or before the given date`() {
        assertEquals(LocalDate(2026, 7, 13), weekStartOf(LocalDate(2026, 7, 13))) // a Monday itself
        assertEquals(LocalDate(2026, 7, 13), weekStartOf(LocalDate(2026, 7, 19))) // the Sunday ending that week
        assertEquals(LocalDate(2026, 7, 20), weekStartOf(LocalDate(2026, 7, 20))) // the following Monday
    }
```

(Verified against the real calendar: 2026-07-13 is a Monday, 2026-07-19 is the Sunday six days later, 2026-07-20 is the next Monday — matching the app's own Report screen, which already shows "07/13 - 07/19" as the current week.)

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.ReportViewModelTest"`
Expected: FAIL — compile errors (`MedicationWeekSummary` has no `medicationId`; `weekStartOf` doesn't exist as a public top-level function yet)

- [ ] **Step 4: `DoseDao` gains a medication-scoped range query**

Add to `DoseDao.kt` (near `getDosesInTimeRange`):

```kotlin
    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE doses.medicationId = :medicationId
          AND timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp ASC
    """)
    fun getDosesForMedicationInTimeRange(medicationId: Long, startTime: Long, endTime: Long): Flow<List<DoseWithMedication>>
```

- [ ] **Step 5: `MedicationRepository`/`MedicationRepositoryImpl` gain the range method**

Add to the `MedicationRepository` interface (near `getDosesInWeek`):

```kotlin
    fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>>
```

Add to `MedicationRepositoryImpl` (near `getDosesInWeek`; needs no new imports — `DateTimeUnit`, `TimeZone`, `atStartOfDayIn` are already imported in this file):

```kotlin
    override fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startMillis = start.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMillis = endExclusive.atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesForMedicationInTimeRange(medicationId, startMillis, endMillis)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for medication in range", e)) }
    }
```

- [ ] **Step 6: `MedicationWeekSummary` gains `medicationId`; `weekStartOf` is extracted and made public**

In `ReportViewModel.kt`, replace the `MedicationWeekSummary` data class with:

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

Replace the `currentWeekStart` function with:

```kotlin
fun weekStartOf(date: LocalDate): LocalDate {
    val daysSinceMonday = date.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    return date.minus(daysSinceMonday, DateTimeUnit.DAY)
}

private fun currentWeekStart(): LocalDate =
    weekStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
```

Replace the `summarize` function's `.groupBy { it.medicationName }.map { (name, doses) -> ... }` block with:

```kotlin
                    .groupBy { it.medicationId }
                    .map { (medicationId, doses) ->
                        MedicationWeekSummary(
                            medicationId = medicationId,
                            medicationName = doses.first().medicationName,
                            taken = doses.count { it.status == DoseStatus.TAKEN },
                            missed = doses.count { it.status == DoseStatus.MISSED },
                            skipped = doses.count { it.status == DoseStatus.SKIPPED },
                            upcoming = doses.count { it.status == DoseStatus.PENDING },
                            totalAmountTaken = doses.filter { it.status == DoseStatus.TAKEN }
                                .sumOf { it.amount ?: 0.0 },
                            totalAmountScheduled = doses.sumOf { it.amount ?: 0.0 },
                            unit = doses.firstOrNull { it.unit != null }?.unit ?: ""
                        )
                    }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.ReportViewModelTest"`
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 8: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL — `MedicationWeekSummary` gaining a field doesn't break `ReportScreen.kt` (it only reads existing fields; it doesn't construct `MedicationWeekSummary` itself, `ReportViewModel.summarize()` does, and that's already updated in Step 6).

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModel.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModelTest.kt
git commit -m "feat: add medication-scoped dose date-range query, track medicationId on weekly summaries"
```

---

### Task 2: `MedicationReportViewModel` — week bucketing, month/year period navigation

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/MedicationReportViewModel.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/MedicationReportViewModelTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getMedicationById(id)`, `.getDosesForMedicationInRange(medicationId, start, endExclusive)` (Task 1); `weekStartOf(date)` (Task 1).
- Produces: `MedicationReportViewModel(repository, savedStateHandle)` with `uiState: StateFlow<MedicationReportState>`, `setMedicationId(id)`, `onEvent(MedicationReportEvent)` — consumed by `MedicationReportScreen` (Task 3). `ReportRangeMode` enum (`MONTH`, `YEAR`), `WeekQuantity(weekStart, totalTaken)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.SavedStateHandle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReportViewModelTest {

    private val repository = mock<MedicationRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private val zone = TimeZone.currentSystemDefault()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buckets TAKEN doses by week and fills weeks with no doses as zero`() = runTest {
        // July 2026's weeks (Monday-aligned): 06-29, 07-06, 07-13, 07-20, 07-27
        val doses = listOf(
            Dose(id = 1, medicationId = 10, timestamp = LocalDate(2026, 7, 1).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 2, medicationId = 10, timestamp = LocalDate(2026, 7, 14).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 3, medicationId = 10, timestamp = LocalDate(2026, 7, 15).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.TAKEN),
            Dose(id = 4, medicationId = 10, timestamp = LocalDate(2026, 7, 21).atTime(8, 0).toInstant(zone), amount = 500.0, unit = "mg", status = DoseStatus.MISSED)
        )
        whenever(repository.getMedicationById(10)).thenReturn(flowOf(Data.Success(Medication(id = 10, name = "Aspirin", dosage = 500.0, unit = "mg"))))
        whenever(repository.getDosesForMedicationInRange(eq(10L), any(), any())).thenReturn(flowOf(Data.Success(doses)))

        val viewModel = MedicationReportViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        viewModel.setMedicationId(10)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val weeks = (state.weeks as Data.Success).data

        assertEquals("Aspirin", state.medicationName)
        assertEquals("mg", state.unit)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate(2026, 6, 29), weeks[0].weekStart)
        assertEquals(500.0, weeks[0].totalTaken, 0.0)
        assertEquals(LocalDate(2026, 7, 6), weeks[1].weekStart)
        assertEquals(0.0, weeks[1].totalTaken, 0.0)
        assertEquals(LocalDate(2026, 7, 13), weeks[2].weekStart)
        assertEquals(1000.0, weeks[2].totalTaken, 0.0)
        assertEquals(LocalDate(2026, 7, 20), weeks[3].weekStart)
        assertEquals(0.0, weeks[3].totalTaken, 0.0) // MISSED dose doesn't count
        assertEquals(LocalDate(2026, 7, 27), weeks[4].weekStart)
        assertEquals(0.0, weeks[4].totalTaken, 0.0)

        job.cancel()
    }

    @Test
    fun `PreviousPeriod and NextPeriod shift by one month, ToggleMode switches to year anchored on the current period`() = runTest {
        whenever(repository.getMedicationById(any())).thenReturn(flowOf(Data.Success(Medication(id = 10, name = "Aspirin", dosage = 500.0, unit = "mg"))))
        whenever(repository.getDosesForMedicationInRange(any(), any(), any())).thenReturn(flowOf(Data.Success(emptyList())))

        val viewModel = MedicationReportViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        viewModel.setMedicationId(10)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReportRangeMode.MONTH, viewModel.uiState.value.mode)
        val initialPeriodStart = viewModel.uiState.value.periodStart

        viewModel.onEvent(MedicationReportEvent.NextPeriod)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialPeriodStart.plus(1, DateTimeUnit.MONTH), viewModel.uiState.value.periodStart)

        viewModel.onEvent(MedicationReportEvent.PreviousPeriod)
        viewModel.onEvent(MedicationReportEvent.PreviousPeriod)
        testDispatcher.scheduler.advanceUntilIdle()
        val monthBefore = initialPeriodStart.minus(1, DateTimeUnit.MONTH)
        assertEquals(monthBefore, viewModel.uiState.value.periodStart)

        viewModel.onEvent(MedicationReportEvent.ToggleMode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReportRangeMode.YEAR, viewModel.uiState.value.mode)
        assertEquals(LocalDate(monthBefore.year, 1, 1), viewModel.uiState.value.periodStart)

        job.cancel()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.MedicationReportViewModelTest"`
Expected: FAIL (compile error — `MedicationReportViewModel` does not exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

enum class ReportRangeMode { MONTH, YEAR }

data class WeekQuantity(val weekStart: LocalDate, val totalTaken: Double)

data class MedicationReportState(
    val medicationName: String = "",
    val unit: String = "",
    val mode: ReportRangeMode = ReportRangeMode.MONTH,
    val periodStart: LocalDate = monthStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault())),
    val weeks: Data<List<WeekQuantity>> = Data.Loading
)

sealed interface MedicationReportEvent {
    data object ToggleMode : MedicationReportEvent
    data object PreviousPeriod : MedicationReportEvent
    data object NextPeriod : MedicationReportEvent
}

private data class PeriodSelection(val mode: ReportRangeMode, val periodStart: LocalDate)

private fun monthStartOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

private fun yearStartOf(date: LocalDate): LocalDate = LocalDate(date.year, 1, 1)

private fun periodStartFor(mode: ReportRangeMode, anchor: LocalDate): LocalDate = when (mode) {
    ReportRangeMode.MONTH -> monthStartOf(anchor)
    ReportRangeMode.YEAR -> yearStartOf(anchor)
}

private fun periodEndExclusive(mode: ReportRangeMode, periodStart: LocalDate): LocalDate = when (mode) {
    ReportRangeMode.MONTH -> periodStart.plus(1, DateTimeUnit.MONTH)
    ReportRangeMode.YEAR -> periodStart.plus(1, DateTimeUnit.YEAR)
}

private fun weeksInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): List<LocalDate> {
    val firstWeek = weekStartOf(periodStart)
    return generateSequence(firstWeek) { it.plus(7, DateTimeUnit.DAY) }
        .takeWhile { it < periodEndExclusive }
        .toList()
}

private fun bucketDosesByWeek(
    doses: List<Dose>,
    periodStart: LocalDate,
    periodEndExclusive: LocalDate
): List<WeekQuantity> {
    val zone = TimeZone.currentSystemDefault()
    val takenByWeek = doses
        .filter { it.status == DoseStatus.TAKEN }
        .groupBy { weekStartOf(it.timestamp.toLocalDateTime(zone).date) }
        .mapValues { (_, weekDoses) -> weekDoses.sumOf { it.amount ?: 0.0 } }

    return weeksInPeriod(periodStart, periodEndExclusive).map { weekStart ->
        WeekQuantity(weekStart, takenByWeek[weekStart] ?: 0.0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReportViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)
    private val _selection = MutableStateFlow(
        PeriodSelection(ReportRangeMode.MONTH, monthStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault())))
    )

    val uiState: StateFlow<MedicationReportState> = combine(
        medicationIdFlow.filterNotNull(),
        _selection
    ) { medicationId, selection -> medicationId to selection }
        .flatMapLatest { (medicationId, selection) ->
            val endExclusive = periodEndExclusive(selection.mode, selection.periodStart)
            combine(
                repository.getMedicationById(medicationId),
                repository.getDosesForMedicationInRange(medicationId, selection.periodStart, endExclusive)
            ) { medicationResult, dosesResult ->
                val medication = (medicationResult as? Data.Success)?.data
                val weeks = when (dosesResult) {
                    is Data.Success -> Data.Success(bucketDosesByWeek(dosesResult.data, selection.periodStart, endExclusive))
                    is Data.Error -> Data.Error(dosesResult.message)
                    is Data.Loading -> Data.Loading
                }
                MedicationReportState(
                    medicationName = medication?.name ?: "",
                    unit = medication?.unit ?: "",
                    mode = selection.mode,
                    periodStart = selection.periodStart,
                    weeks = weeks
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicationReportState()
        )

    fun setMedicationId(id: Long) {
        if (savedStateHandle.get<Long>("medicationId") == null) {
            savedStateHandle["medicationId"] = id
        }
    }

    fun onEvent(event: MedicationReportEvent) {
        when (event) {
            is MedicationReportEvent.ToggleMode -> _selection.update { current ->
                val newMode = if (current.mode == ReportRangeMode.MONTH) ReportRangeMode.YEAR else ReportRangeMode.MONTH
                PeriodSelection(newMode, periodStartFor(newMode, current.periodStart))
            }
            is MedicationReportEvent.PreviousPeriod -> _selection.update { current ->
                current.copy(
                    periodStart = when (current.mode) {
                        ReportRangeMode.MONTH -> current.periodStart.minus(1, DateTimeUnit.MONTH)
                        ReportRangeMode.YEAR -> current.periodStart.minus(1, DateTimeUnit.YEAR)
                    }
                )
            }
            is MedicationReportEvent.NextPeriod -> _selection.update { current ->
                current.copy(
                    periodStart = when (current.mode) {
                        ReportRangeMode.MONTH -> current.periodStart.plus(1, DateTimeUnit.MONTH)
                        ReportRangeMode.YEAR -> current.periodStart.plus(1, DateTimeUnit.YEAR)
                    }
                )
            }
        }
    }
}
```

Note: `weekStartOf` here is the public top-level function added to `ReportViewModel.kt` in Task 1 — same package (`com.yhdista.dosetracker.ui.report`), so no import is needed to call it from this file.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.MedicationReportViewModelTest"`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 5: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL — `MedicationReportViewModel.kt` is a new, self-contained file with no consumers yet; it isn't referenced by DI or navigation until Task 3, so nothing else can break by its presence.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/MedicationReportViewModel.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/MedicationReportViewModelTest.kt
git commit -m "feat: add MedicationReportViewModel with weekly bucketing and month/year navigation"
```

---

### Task 3: `MedicationReportScreen` + navigation + DI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/MedicationReportScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt`

**Interfaces:**
- Consumes: `MedicationReportViewModel.uiState`, `.setMedicationId()`, `.onEvent()` (Task 2); `MedicationWeekSummary.medicationId` (Task 1).

- [ ] **Step 1: `MedicationSummaryCard` becomes clickable**

In `ReportScreen.kt`, add `import androidx.compose.foundation.clickable` to the imports. Change the `ReportScreen` function signature to add a new parameter:

```kotlin
@Composable
fun ReportScreen(viewModel: ReportViewModel, onMedicationClick: (Long) -> Unit) {
```

In its `items(result.data) { summary -> MedicationSummaryCard(summary) }` call, change to:

```kotlin
                            items(result.data) { summary ->
                                MedicationSummaryCard(summary, onClick = { onMedicationClick(summary.medicationId) })
                            }
```

Change the `MedicationSummaryCard` function signature and its `Card(...)` call:

```kotlin
@Composable
private fun MedicationSummaryCard(summary: MedicationWeekSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
```

(The rest of `MedicationSummaryCard`'s body is unchanged.)

- [ ] **Step 2: `MedicationReportScreen`**

```kotlin
package com.yhdista.dosetracker.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import kotlinx.datetime.LocalDate
import kotlinx.datetime.MonthNames
import kotlinx.datetime.format
import kotlinx.datetime.format.char

private const val MAX_BAR_HEIGHT_DP = 160
private const val MIN_BAR_HEIGHT_DP = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationReportScreen(
    medicationId: Long,
    viewModel: MedicationReportViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedWeek by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(medicationId) {
        viewModel.setMedicationId(medicationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.medicationName.ifEmpty { "Medication" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                SegmentedButton(
                    selected = state.mode == ReportRangeMode.MONTH,
                    onClick = {
                        if (state.mode != ReportRangeMode.MONTH) viewModel.onEvent(MedicationReportEvent.ToggleMode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Month") }
                SegmentedButton(
                    selected = state.mode == ReportRangeMode.YEAR,
                    onClick = {
                        if (state.mode != ReportRangeMode.YEAR) viewModel.onEvent(MedicationReportEvent.ToggleMode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Year") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEvent(MedicationReportEvent.PreviousPeriod) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous period")
                }
                Text(
                    text = periodLabel(state.mode, state.periodStart),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { viewModel.onEvent(MedicationReportEvent.NextPeriod) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next period")
                }
            }

            when (val result = state.weeks) {
                is Data.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is Data.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${result.message}")
                    }
                }
                is Data.Success -> {
                    val weeks = result.data
                    val maxTaken = weeks.maxOfOrNull { it.totalTaken } ?: 0.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeks.forEach { week ->
                            WeekBar(
                                week = week,
                                maxTaken = maxTaken,
                                unit = state.unit,
                                isSelected = selectedWeek == week.weekStart,
                                onClick = {
                                    selectedWeek = if (selectedWeek == week.weekStart) null else week.weekStart
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBar(
    week: WeekQuantity,
    maxTaken: Double,
    unit: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val heightDp = if (maxTaken > 0) {
        (MIN_BAR_HEIGHT_DP + (week.totalTaken / maxTaken) * (MAX_BAR_HEIGHT_DP - MIN_BAR_HEIGHT_DP)).dp
    } else {
        MIN_BAR_HEIGHT_DP.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp)
    ) {
        if (isSelected) {
            Text(
                text = "${week.totalTaken} $unit",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(MAX_BAR_HEIGHT_DP.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(heightDp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onClick)
            )
        }
        Text(
            text = week.weekStart.format(weekTickFormat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun periodLabel(mode: ReportRangeMode, periodStart: LocalDate): String {
    return when (mode) {
        ReportRangeMode.MONTH -> periodStart.format(monthLabelFormat)
        ReportRangeMode.YEAR -> periodStart.year.toString()
    }
}

private val monthLabelFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_FULL); char(' '); year()
}

private val weekTickFormat = LocalDate.Format {
    monthNumber(); char('/'); day()
}
```

- [ ] **Step 3: New destination**

Modify `Destinations.kt` — add:

```kotlin
    @Serializable
    data class MedicationReport(val medicationId: Long) : Destination
```

- [ ] **Step 4: Wire it into `DoseTrackerAppMain`**

Modify `DoseTrackerAppMain.kt`:

- Add `import com.yhdista.dosetracker.ui.report.MedicationReportScreen` and `import com.yhdista.dosetracker.ui.report.MedicationReportViewModel`.
- Change the `Destination.Report` branch's `ReportScreen(...)` call to:

```kotlin
                is Destination.Report -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        ReportScreen(
                            viewModel = koinViewModel<ReportViewModel>(),
                            onMedicationClick = { id -> backstack.add(Destination.MedicationReport(id)) }
                        )
                    }
                }
```

- Add a new branch, alongside the other detail-pane destinations (e.g. after `Destination.ConfirmDose`):

```kotlin
                is Destination.MedicationReport -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        MedicationReportScreen(
                            medicationId = destination.medicationId,
                            viewModel = koinViewModel<MedicationReportViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
```

- [ ] **Step 5: DI**

Add `import com.yhdista.dosetracker.ui.report.MedicationReportViewModel` and `viewModel { MedicationReportViewModel(get(), get()) }` to `ViewModelModule.kt`.

- [ ] **Step 6: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Manual verification**

Launch the app, log TAKEN doses for one medication across at least two different weeks (directly via SQLite/adb if faster than waiting on real reminders — insert `doses` rows with distinct `timestamp`s a week or more apart, `status = 'TAKEN'`, a real `amount`/`unit`). Open the Report tab, tap that medication's card, confirm `MedicationReportScreen` opens showing the medication's name, a Month/Year toggle, a `< July 2026 >`-style period row, and a horizontally-scrollable bar chart with the expected number of week bars for the current month, bars roughly proportional to the seeded quantities, and zero-height (but still present) bars for weeks with nothing taken. Tap a bar, confirm its value label appears above it and toggles off on a second tap. Use the previous/next arrows to move a month back and forward, confirm the period label and bar set both update. Switch to Year mode, confirm it shows ~52 bars scrollable horizontally and the label switches to just the year; toggle back to Month, confirm it returns to a month containing whatever period was last shown (not necessarily today's month if you'd navigated away first).

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/MedicationReportScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt
git commit -m "feat: add MedicationReportScreen with month/year weekly bar chart, wire navigation from weekly report"
```
