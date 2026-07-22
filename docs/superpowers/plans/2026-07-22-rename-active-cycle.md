# Rename Active Cycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user rename the currently active cycle from the Today dashboard header, without leaving the Today screen.

**Architecture:** New `TodayEvent.RenameActiveCycle(name)` handled by `TodayViewModel`, which loads the active cycle and calls `repository.updateCycle(cycle.copy(name = name))`. `TodayState.activeCycle` is sourced from a `Flow`, so the dashboard header updates automatically once the write lands. UI: a new "Přejmenovat" button in `CycleDashboardHeader` opens an `AlertDialog` with a single pre-filled `OutlinedTextField`, reusing the existing "Ukončit cyklus?" dialog's construction as a template.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform UI in `commonMain`), JUnit4 + Mockito-Kotlin + `kotlinx-coroutines-test` for `androidHostTest`.

## Global Constraints

- Rename only — no editing of `totalWeeks`, `type`, or `onCompleteAction` on the active cycle (spec: `docs/superpowers/specs/2026-07-22-rename-active-cycle-design.md`, Non-Goals).
- Only the single `ACTIVE` cycle is editable this way — no editing of `DRAFT`/`COMPLETED` cycles.
- Blank names must never reach the ViewModel — the dialog's confirm button is the only guard, no error state in the dialog.
- Match existing `AppLogger.d` click-logging pattern already present on the other `CycleDashboardHeader` buttons.

---

### Task 1: `TodayEvent.RenameActiveCycle` + `TodayViewModel` handler

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getActiveCycleOnce(): Cycle?` and `MedicationRepository.updateCycle(cycle: Cycle): Data<Unit>` (both already exist, used identically by `endActiveCycle()` at `TodayViewModel.kt:98-103`).
- Produces: `TodayEvent.RenameActiveCycle(val name: String) : TodayEvent`, routed through `TodayViewModel.onEvent` — consumed by Task 2's UI.

- [ ] **Step 1: Write the failing test**

Add to `TodayViewModelTest.kt`, after the existing `EndActiveCycle marks the active cycle COMPLETED...` test (before the closing `}` of the class):

```kotlin
    @Test
    fun `RenameActiveCycle updates only the name, leaving other fields untouched`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 12,
            startDate = LocalDate(2026, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(emptyList())))
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        val viewModel = TodayViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TodayEvent.RenameActiveCycle("primo"))
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(cycle.copy(name = "primo"))

        job.cancel()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.today.TodayViewModelTest" -q`
Expected: FAIL with a compile error — `TodayEvent.RenameActiveCycle` is unresolved.

- [ ] **Step 3: Add the event and handler**

In `TodayViewModel.kt`, add the new event to the `sealed interface TodayEvent` block (line 30-34):

```kotlin
sealed interface TodayEvent {
    data class ToggleDoseStatus(val dose: Dose) : TodayEvent
    data class SelectDose(val id: Long?) : TodayEvent
    object EndActiveCycle : TodayEvent
    data class RenameActiveCycle(val name: String) : TodayEvent
}
```

Add the branch in `onEvent` (line 78-85):

```kotlin
    fun onEvent(event: TodayEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("TodayViewModel", "onEvent: $event")
        when (event) {
            is TodayEvent.ToggleDoseStatus -> toggleDoseStatus(event.dose)
            is TodayEvent.SelectDose -> selectDose(event.id)
            is TodayEvent.EndActiveCycle -> endActiveCycle()
            is TodayEvent.RenameActiveCycle -> renameActiveCycle(event.name)
        }
    }
```

Add the handler next to `endActiveCycle()` (after line 103):

```kotlin
    private fun renameActiveCycle(name: String) {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleOnce() ?: return@launch
            repository.updateCycle(cycle.copy(name = name))
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.today.TodayViewModelTest" -q`
Expected: PASS, all `TodayViewModelTest` tests green (4 tests total).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt
git commit -m "feat: add RenameActiveCycle event to TodayViewModel"
```

---

### Task 2: "Přejmenovat" button + dialog in `CycleDashboardHeader`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `TodayEvent.RenameActiveCycle(name: String)` from Task 1, dispatched via the `onEvent: (TodayEvent) -> Unit` parameter already threaded through `TodayContent` → `CycleDashboardHeader` is called with `cycle = activeCycle` at `TodayScreen.kt:101-107`; `onEvent` is available in the enclosing `TodayContent` scope, wire it into `CycleDashboardHeader` as a new `onRename: (String) -> Unit` parameter.
- Produces: nothing consumed by later tasks — this is the last task.

- [ ] **Step 1: Add `onRename` parameter and wire it at the call site**

In `TodayScreen.kt`, update the `CycleDashboardHeader` call inside `TodayContent` (around line 101-107):

```kotlin
                            CycleDashboardHeader(
                                cycle = activeCycle,
                                onOpenHistory = onOpenCycleHistory,
                                onManageCycle = onCreateCycle,
                                onManageWeeks = { onManageWeeks(activeCycle.id) },
                                onEndCycle = { onEvent(TodayEvent.EndActiveCycle) },
                                onRename = { newName -> onEvent(TodayEvent.RenameActiveCycle(newName)) }
                            )
```

Update the `CycleDashboardHeader` signature (around line 166-172):

```kotlin
private fun CycleDashboardHeader(
    cycle: Cycle,
    onOpenHistory: () -> Unit,
    onManageCycle: () -> Unit,
    onManageWeeks: () -> Unit,
    onEndCycle: () -> Unit,
    onRename: (String) -> Unit
) {
```

- [ ] **Step 2: Add dialog state and the rename dialog**

Inside `CycleDashboardHeader`, next to the existing `var showEndConfirm by remember { mutableStateOf(false) }` (around line 173), add:

```kotlin
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(cycle.id) { mutableStateOf(cycle.name) }
```

Immediately after the existing `if (showEndConfirm) { AlertDialog(...) }` block (after line 190), add:

```kotlin
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Přejmenovat cyklus") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Název") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        com.yhdista.dosetracker.core.AppLogger.d("TodayScreen", "Confirm: Přejmenovat cyklus (cycleId=${cycle.id}, oldName='${cycle.name}', newName='$renameText')")
                        showRenameDialog = false
                        onRename(renameText)
                    }
                ) { Text("Uložit") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Zrušit") }
            }
        )
    }
```

- [ ] **Step 3: Add the "Přejmenovat" button**

In the `FlowRow` (around line 216-241), add a new `TextButton` as the first entry, before "Historie cyklu":

```kotlin
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    com.yhdista.dosetracker.core.AppLogger.d("TodayScreen", "Click: Přejmenovat (cycleId=${cycle.id}, name='${cycle.name}')")
                    renameText = cycle.name
                    showRenameDialog = true
                }) {
                    Text("Přejmenovat")
                }
                TextButton(onClick = {
                    com.yhdista.dosetracker.core.AppLogger.d("TodayScreen", "Click: Historie cyklu (cycleId=${cycle.id}, name='${cycle.name}')")
                    onOpenHistory()
                }) {
                    Text("Historie cyklu")
                }
```

(leave the remaining three buttons — "Přidat návazný cyklus", "Upravit týdny", "Ukončit cyklus" — unchanged, just after this new one).

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileAndroidMain -q`
Expected: no output, exit code 0.

- [ ] **Step 5: Manual verification**

Run the app (or use the `run` skill), start a cycle with a name, tap "Přejmenovat" on the Today dashboard, change the name, tap "Uložit". Confirm the header updates to the new name and `adb logcat` shows both the `Click: Přejmenovat` and `Confirm: Přejmenovat cyklus` lines followed by `onEvent: RenameActiveCycle(name=...)`.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt
git commit -m "feat: add cycle rename dialog to Today dashboard"
```
