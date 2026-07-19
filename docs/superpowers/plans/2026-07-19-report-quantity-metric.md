# Weekly Report Quantity Metric Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a total-quantity-taken-vs-scheduled progress meter per medication to the existing weekly Report screen, alongside the existing Taken/Missed/Skipped/Upcoming counts.

**Architecture:** Pure aggregation addition to `ReportViewModel.summarize()` — sum each dose's already-populated `amount` field, split by `status == TAKEN` vs. all statuses, per medication. No repository/DAO changes (same `Dose` list `getDosesInWeek` already returns). UI adds a `LinearProgressIndicator` + label to the existing per-medication card.

**Tech Stack:** Kotlin Multiplatform (`:shared` commonMain), Compose Multiplatform, Material3 `LinearProgressIndicator`.

## Global Constraints

- No new Gradle dependencies.
- Tests follow the codebase's existing convention exactly: JUnit4 (`org.junit.Test`/`Before`/`After`), `mockito-kotlin`, `kotlinx-coroutines-test` `StandardTestDispatcher`. Not JUnit5, not Turbine, not AssertK.
- UI copy is hardcoded inline in Compose — no `strings.xml`.
- The percentage's denominator (`totalAmountScheduled`) is the sum of `amount` across **all** doses in the week regardless of status (Taken+Missed+Skipped+Upcoming) — not just Taken+Missed+Skipped. Every `Dose` row already carries a real `amount` at creation time (`DoseGenerator` sets it from `medication.dosage`; ad-hoc doses set it from `AddDoseScreen`), so this is a plain sum, no null-filtering needed beyond an `?: 0.0` fallback.
- The percentage may exceed 100% (taking an extra ad-hoc dose) — that's correct, not a bug. Show the true percentage in the label; cap only the progress bar's visual fill at 100%.
- Build verification command: `./gradlew :app:assembleDebug`.
- Test verification command: `./gradlew :shared:testAndroidHostTest --tests "<FQCN>"`.

---

### Task 1: Quantity aggregation + progress meter UI

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModel.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModelTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportScreen.kt`

**Interfaces:**
- Produces: `MedicationWeekSummary` gains `totalAmountTaken: Double`, `totalAmountScheduled: Double`, `unit: String` — consumed by `ReportScreen.kt`'s `MedicationSummaryCard`.

- [ ] **Step 1: Extend the existing aggregation test with quantity assertions**

Replace the whole `summarizes counts per medication for the current week` test in `ReportViewModelTest.kt` (the file's first `@Test`) with:

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

        assertEquals(2, aspirin.taken)
        assertEquals(1, aspirin.missed)
        assertEquals(0, aspirin.skipped)
        assertEquals(1000.0, aspirin.totalAmountTaken, 0.0)
        assertEquals(1500.0, aspirin.totalAmountScheduled, 0.0)
        assertEquals("mg", aspirin.unit)

        assertEquals(1, ibuprofen.skipped)
        assertEquals(1, ibuprofen.upcoming)
        assertEquals(0.0, ibuprofen.totalAmountTaken, 0.0)
        assertEquals(800.0, ibuprofen.totalAmountScheduled, 0.0)
        assertEquals("mg", ibuprofen.unit)

        job.cancel()
    }
```

This is a pure extension of the existing test (same fixture shape, now with `amount`/`unit` set on each dose and new assertions) — the second test (`PreviousWeek and NextWeek shift the queried week by 7 days`) is untouched.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.ReportViewModelTest"`
Expected: FAIL — compile error (`MedicationWeekSummary` has no `totalAmountTaken`/`totalAmountScheduled`/`unit`) or, once that's stubbed, assertion failures on the new fields.

- [ ] **Step 3: Extend `MedicationWeekSummary` and `summarize()`**

In `ReportViewModel.kt`, replace the `MedicationWeekSummary` data class (currently 7 lines starting `data class MedicationWeekSummary(`) with:

```kotlin
data class MedicationWeekSummary(
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

Replace the `summarize` function's `map { (name, doses) -> ... }` block (inside the `is Data.Success ->` branch) with:

```kotlin
                    .map { (name, doses) ->
                        MedicationWeekSummary(
                            medicationName = name,
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

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.ReportViewModelTest"`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 5: Add the progress meter to `MedicationSummaryCard`**

In `ReportScreen.kt`, add `import kotlin.math.roundToInt` to the file's imports, and replace the `MedicationSummaryCard` function with:

```kotlin
@Composable
private fun MedicationSummaryCard(summary: MedicationWeekSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Taken: ${summary.taken}  Missed: ${summary.missed}  Skipped: ${summary.skipped}")
            if (summary.upcoming > 0) {
                Text(
                    "Upcoming: ${summary.upcoming}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.totalAmountScheduled > 0) {
                val percentage = (summary.totalAmountTaken / summary.totalAmountScheduled * 100).roundToInt()
                val progress = (summary.totalAmountTaken / summary.totalAmountScheduled).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${summary.totalAmountTaken} / ${summary.totalAmountScheduled} ${summary.unit} ($percentage%)",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
```

`Spacer`/`height` come from the file's existing `import androidx.compose.foundation.layout.*`; `LinearProgressIndicator`/`Card`/`Text`/`MaterialTheme` come from the existing `import androidx.compose.material3.*`. No other new imports beyond `kotlin.math.roundToInt`.

- [ ] **Step 6: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Manual verification**

Launch the app, log at least one TAKEN dose for a medication with an active schedule this week (via `ConfirmDoseScreen` or the Today screen), open the Report tab, confirm the medication's card shows the new quantity line (`"X / Y <unit> (Z%)"`) and a progress bar filled proportionally to the percentage. Confirm a medication with zero doses scheduled this week (if any) simply omits the meter (no crash, no empty bar). Confirm previous/next week navigation updates the meter along with the existing counts.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModel.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModelTest.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportScreen.kt
git commit -m "feat: show total quantity taken vs scheduled per medication in weekly report"
```
