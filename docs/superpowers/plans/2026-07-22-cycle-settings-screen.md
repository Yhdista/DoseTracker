# Cycle Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the five cycle actions (Přejmenovat, Historie cyklu, Přidat návazný cyklus, Upravit týdny, Ukončit cyklus) off the Today dashboard header into a dedicated "Nastavení cyklu" subscreen, reachable via a gear icon on the header card.

**Architecture:** New `Destination.CycleSettings` (no params — always resolves "the" active cycle via `repository.getActiveCycle()`, same source `TodayViewModel` uses). New `CycleSettingsViewModel` owns `rename()`/`endCycle()`, replacing the `TodayEvent.RenameActiveCycle`/`EndActiveCycle` handlers removed from `TodayViewModel`. New `CycleSettingsScreen` hosts the two `AlertDialog`s (rename, end-confirm) moved verbatim from `CycleDashboardHeader`, plus a `ListItem` per action. `CycleDashboardHeader` shrinks to cycle stats + one gear `IconButton`.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform UI in `commonMain`), JUnit4 + Mockito-Kotlin + `kotlinx-coroutines-test` for `androidHostTest`, Koin for DI, Navigation3 (`androidx.navigation3`) for routing.

## Global Constraints

- Rename only changes `name`; end-cycle only changes `status` — no editing of `totalWeeks`, `type`, or `onCompleteAction` on the active cycle (spec: `docs/superpowers/specs/2026-07-22-cycle-settings-screen-design.md`, Non-Goals).
- Only the single `ACTIVE` cycle is ever editable this way — no editing of `DRAFT`/`COMPLETED` cycles, no deep-linking to a cycle by ID.
- What each of the five actions does is unchanged — only where they're presented moves.
- Match the existing `AppLogger.d` click/confirm-logging pattern already used on every button in `CycleDashboardHeader` (`Click: <label> (cycleId=..., name=...)`, `Confirm: <label> (...)`).

---

### Task 1: `CycleSettingsViewModel`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsViewModel.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getActiveCycle(): Flow<Data<Cycle?>>`, `getActiveCycleOnce(): Cycle?`, `updateCycle(cycle: Cycle): Data<Unit>` (all already exist, used identically by the handlers being removed from `TodayViewModel` in Task 3).
- Produces: `CycleSettingsViewModel(repository: MedicationRepository)`, `val uiState: StateFlow<Data<Cycle?>>`, `fun rename(name: String)`, `fun endCycle()` — consumed by Task 2's `CycleSettingsScreen`.

- [ ] **Step 1: Write the failing tests**

Create `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsViewModelTest.kt`:

```kotlin
package com.yhdista.dosetracker.ui.cycle

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CycleSettingsViewModelTest {

    private val repository = mock<MedicationRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rename updates only the name, leaving other fields untouched`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 12,
            startDate = LocalDate(2026, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        val viewModel = CycleSettingsViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.rename("primo")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(cycle.copy(name = "primo"))

        job.cancel()
    }

    @Test
    fun `endCycle marks the active cycle COMPLETED without touching onCompleteAction routing`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2026, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        val viewModel = CycleSettingsViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.endCycle()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(cycle.copy(status = CycleStatus.COMPLETED))

        job.cancel()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.cycle.CycleSettingsViewModelTest" -q`
Expected: FAIL with a compile error — `CycleSettingsViewModel` is unresolved.

- [ ] **Step 3: Implement `CycleSettingsViewModel`**

Create `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsViewModel.kt`:

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CycleSettingsViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    val uiState: StateFlow<Data<Cycle?>> = repository.getActiveCycle()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Data.Loading
        )

    fun rename(name: String) {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleOnce() ?: return@launch
            repository.updateCycle(cycle.copy(name = name))
        }
    }

    fun endCycle() {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleOnce() ?: return@launch
            repository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.cycle.CycleSettingsViewModelTest" -q`
Expected: PASS, both tests green.

- [ ] **Step 5: Register in Koin**

In `shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt`, add the import next to the other `ui.cycle` imports:

```kotlin
import com.yhdista.dosetracker.ui.cycle.CycleSettingsViewModel
```

Add the binding next to `CycleHistoryViewModel`'s:

```kotlin
    viewModel { CycleHistoryViewModel(get()) }
    viewModel { CycleSettingsViewModel(get()) }
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsViewModel.kt shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsViewModelTest.kt
git commit -m "feat: add CycleSettingsViewModel"
```

---

### Task 2: `CycleSettingsScreen` + navigation wiring

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`

**Interfaces:**
- Consumes: `CycleSettingsViewModel` from Task 1 (`uiState`, `rename(name)`, `endCycle()`); `Destination.CreateCycle`, `Destination.CycleHistory`, `Destination.CycleWeekList(cycleId)` (already exist).
- Produces: `Destination.CycleSettings` (new nav key), `CycleSettingsScreen(viewModel, onBack, onNavigateToCycleHistory, onNavigateToCreateCycle, onNavigateToManageWeeks, onEnded)` — consumed by Task 4's updated `TodayScreen` wiring.

- [ ] **Step 1: Add the `CycleSettings` destination**

In `Destinations.kt`, add after `CycleHistory` (after line 41-42):

```kotlin
    @Serializable
    data object CycleSettings : Destination
```

- [ ] **Step 2: Write `CycleSettingsScreen`**

Create `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsScreen.kt`:

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleSettingsScreen(
    viewModel: CycleSettingsViewModel,
    onBack: () -> Unit,
    onNavigateToCycleHistory: () -> Unit,
    onNavigateToCreateCycle: () -> Unit,
    onNavigateToManageWeeks: (Long) -> Unit,
    onEnded: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení cyklu", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state) {
            is Data.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chyba: ${result.message}")
                }
            }
            is Data.Success -> {
                val cycle = result.data
                if (cycle == null) {
                    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    CycleSettingsList(
                        cycle = cycle,
                        modifier = Modifier.padding(padding),
                        onRename = viewModel::rename,
                        onOpenHistory = onNavigateToCycleHistory,
                        onAddFollowUp = onNavigateToCreateCycle,
                        onManageWeeks = { onNavigateToManageWeeks(cycle.id) },
                        onEndCycle = {
                            viewModel.endCycle()
                            onEnded()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleSettingsList(
    cycle: Cycle,
    modifier: Modifier = Modifier,
    onRename: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onAddFollowUp: () -> Unit,
    onManageWeeks: () -> Unit,
    onEndCycle: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(cycle.id) { mutableStateOf(cycle.name) }
    var showEndConfirm by remember { mutableStateOf(false) }

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
                        com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Confirm: Přejmenovat cyklus (cycleId=${cycle.id}, oldName='${cycle.name}', newName='$renameText')")
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

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("Ukončit cyklus?") },
            text = { Text("Cyklus \"${cycle.name}\" bude ukončen a nebude žádný aktivní cyklus.") },
            confirmButton = {
                TextButton(onClick = {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Confirm: Ukončit cyklus (cycleId=${cycle.id}, name='${cycle.name}')")
                    showEndConfirm = false
                    onEndCycle()
                }) { Text("Ukončit") }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("Zrušit") }
            }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("Přejmenovat") },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Přejmenovat (cycleId=${cycle.id}, name='${cycle.name}')")
                    renameText = cycle.name
                    showRenameDialog = true
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Historie cyklu") },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Historie cyklu (cycleId=${cycle.id}, name='${cycle.name}')")
                    onOpenHistory()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Přidat návazný cyklus") },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Přidat návazný cyklus (cycleId=${cycle.id}, name='${cycle.name}') -> opens CreateCycleScreen to attach a follow-up cycle")
                    onAddFollowUp()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Upravit týdny") },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Upravit týdny (cycleId=${cycle.id}, name='${cycle.name}')")
                    onManageWeeks()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Ukončit cyklus", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Ukončit cyklus, opening confirm dialog (cycleId=${cycle.id}, name='${cycle.name}')")
                    showEndConfirm = true
                }
            )
        }
    }
}
```

- [ ] **Step 3: Wire the `CycleSettings` NavEntry**

In `DoseTrackerAppMain.kt`, add the imports next to the other `ui.cycle` imports:

```kotlin
import com.yhdista.dosetracker.ui.cycle.CycleSettingsScreen
import com.yhdista.dosetracker.ui.cycle.CycleSettingsViewModel
```

Add a new `when` branch right after the `is Destination.CycleHistory -> { ... }` block (after line 322, before `is Destination.Settings ->`):

```kotlin
                is Destination.CycleSettings -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CycleSettingsScreen(
                            viewModel = koinViewModel<CycleSettingsViewModel>(),
                            onBack = { backstack.removeLastOrNull() },
                            onNavigateToCycleHistory = { backstack.add(Destination.CycleHistory) },
                            onNavigateToCreateCycle = { backstack.add(Destination.CreateCycle) },
                            onNavigateToManageWeeks = { cycleId -> backstack.add(Destination.CycleWeekList(cycleId)) },
                            onEnded = { backstack.removeLastOrNull() }
                        )
                    }
                }
```

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileAndroidMain -q`
Expected: no output, exit code 0.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleSettingsScreen.kt shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt
git commit -m "feat: add CycleSettingsScreen and wire CycleSettings destination"
```

---

### Task 3: Remove `RenameActiveCycle`/`EndActiveCycle` from `TodayViewModel`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Produces: `TodayEvent` shrinks to `ToggleDoseStatus` and `SelectDose` only — Task 4's `TodayScreen.kt` must stop referencing `TodayEvent.RenameActiveCycle` / `TodayEvent.EndActiveCycle` and the `onRename` / `onEndCycle` params that dispatched them.

- [ ] **Step 1: Remove the two obsolete tests**

In `TodayViewModelTest.kt`, delete the `EndActiveCycle marks the active cycle COMPLETED without touching onCompleteAction routing` test (lines 87-107) and the `RenameActiveCycle updates only the name, leaving other fields untouched` test (lines 109-129) — everything from `@Test` through the closing `}` of each, including the blank line before each `@Test`.

Also remove the now-unused import (nothing in the remaining two tests calls `verify`):

```kotlin
import org.mockito.kotlin.verify
```

The file should end with the `uiState includes the active cycle when one is running` test followed directly by the class's closing `}`.

- [ ] **Step 2: Run tests to verify they still pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.today.TodayViewModelTest" -q`
Expected: PASS, 2 tests green (the two `uiState` tests).

- [ ] **Step 3: Remove the event, handler branches, and handler methods**

In `TodayViewModel.kt`, shrink the `TodayEvent` sealed interface (lines 30-35):

```kotlin
sealed interface TodayEvent {
    data class ToggleDoseStatus(val dose: Dose) : TodayEvent
    data class SelectDose(val id: Long?) : TodayEvent
}
```

Shrink the `onEvent` `when` block (lines 79-87):

```kotlin
    fun onEvent(event: TodayEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("TodayViewModel", "onEvent: $event")
        when (event) {
            is TodayEvent.ToggleDoseStatus -> toggleDoseStatus(event.dose)
            is TodayEvent.SelectDose -> selectDose(event.id)
        }
    }
```

Delete the `endActiveCycle()` and `renameActiveCycle(name: String)` private methods entirely (lines 100-112).

Remove the now-unused import (nothing left in this file references `CycleStatus`):

```kotlin
import com.yhdista.dosetracker.domain.model.CycleStatus
```

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileAndroidMain -q`
Expected: Fails — `TodayScreen.kt` still references `TodayEvent.RenameActiveCycle`/`EndActiveCycle` and `CycleDashboardHeader`'s `onRename`/`onEndCycle` params. This is expected; Task 4 fixes it.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt
git commit -m "feat: remove RenameActiveCycle/EndActiveCycle from TodayViewModel"
```

---

### Task 4: Trim `CycleDashboardHeader` to a gear icon, wire `TodayScreen` to `CycleSettings`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`

**Interfaces:**
- Consumes: `Destination.CycleSettings` from Task 2.
- Produces: nothing consumed by later tasks — this is the last task. Fixes the compile break left by Task 3.

- [ ] **Step 1: Add the `Settings` icon import**

In `TodayScreen.kt`, add next to the other `icons.rounded` imports (after line 9):

```kotlin
import androidx.compose.material.icons.rounded.Settings
```

- [ ] **Step 2: Replace `CycleDashboardHeader`**

Replace the entire composable (from `@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)` at line 165 through its closing `}` at line 283) with:

```kotlin
@Composable
private fun CycleDashboardHeader(
    cycle: Cycle,
    onOpenSettings: () -> Unit
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val elapsedDays = cycle.startDate.daysUntil(today)
    val typeLabel = when (cycle.type) {
        CycleType.NORMAL -> "Cyklus"
        CycleType.STANDARD -> "Standardní cyklus"
        CycleType.POST -> "Post-cyklus"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cycle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    com.yhdista.dosetracker.core.AppLogger.d("TodayScreen", "Click: Nastavení cyklu (cycleId=${cycle.id}, name='${cycle.name}')")
                    onOpenSettings()
                }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Nastavení cyklu")
                }
            }
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Začátek: ${cycle.startDate}")
            Text("Běží $elapsedDays dní")
            val totalWeeks = cycle.totalWeeks
            if (totalWeeks != null) {
                val totalDays = totalWeeks * 7
                val remainingDays = (totalDays - elapsedDays).coerceAtLeast(0)
                val endDate = cycle.startDate.plus(totalDays, DateTimeUnit.DAY)
                Text("Zbývá $remainingDays dní (končí $endDate)")
            } else {
                Text("Běží neomezeně")
            }
        }
    }
}
```

- [ ] **Step 3: Update `TodayContent`'s call site and signature**

Replace the `TodayContent` signature (lines 60-67):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    onNavigateToConfirm: (Long) -> Unit,
    onCreateCycle: () -> Unit = {},
    onNavigateToCycleSettings: () -> Unit = {}
) {
```

Replace the `CycleDashboardHeader` call (lines 101-108):

```kotlin
                            CycleDashboardHeader(
                                cycle = activeCycle,
                                onOpenSettings = onNavigateToCycleSettings
                            )
```

- [ ] **Step 4: Update `TodayScreen`'s signature and call site**

Replace the public `TodayScreen` composable (lines 38-56):

```kotlin
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToConfirm: (Long) -> Unit,
    onNavigateToCreateCycle: () -> Unit,
    onNavigateToCycleSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToConfirm = onNavigateToConfirm,
        onCreateCycle = onNavigateToCreateCycle,
        onNavigateToCycleSettings = onNavigateToCycleSettings
    )
}
```

- [ ] **Step 5: Update the `Today` `NavEntry` in `DoseTrackerAppMain.kt`**

Replace the `TodayScreen(...)` call (lines 161-175):

```kotlin
                        TodayScreen(
                            viewModel = koinViewModel<TodayViewModel>(),
                            onNavigateToConfirm = { doseId ->
                                backstack.add(Destination.ConfirmDose(doseId))
                            },
                            onNavigateToCreateCycle = {
                                backstack.add(Destination.CreateCycle)
                            },
                            onNavigateToCycleSettings = {
                                backstack.add(Destination.CycleSettings)
                            }
                        )
```

- [ ] **Step 6: Remove now-unused imports in `TodayScreen.kt`**

`remember` and `mutableStateOf` were only used by the dialog state removed from `CycleDashboardHeader` — remove:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
```

(`getValue`/`setValue` stay — `by viewModel.uiState.collectAsStateWithLifecycle()` still needs them.)

- [ ] **Step 7: Compile**

Run: `./gradlew :shared:compileAndroidMain -q`
Expected: no output, exit code 0.

- [ ] **Step 8: Run the full `androidHostTest` suite**

Run: `./gradlew :shared:testAndroidHostTest -q`
Expected: all tests pass, including `TodayViewModelTest` (2 tests) and `CycleSettingsViewModelTest` (2 tests).

- [ ] **Step 9: Manual verification**

Run the app (or use the `run` skill). On the Today dashboard with an active cycle, confirm the header shows only cycle stats plus a gear icon (no more five-button row). Tap the gear icon — `CycleSettingsScreen` opens with all five actions listed. Verify each:
- "Přejmenovat" → dialog → change name → "Uložit" → back out to Today, header shows the new name.
- "Historie cyklu" → opens cycle history.
- "Přidat návazný cyklus" → opens `CreateCycleScreen`.
- "Upravit týdny" → opens the week list for this cycle.
- "Ukončit cyklus" → confirm dialog → "Ukončit" → screen pops straight back to Today, header now shows `NoCycleHeader`'s "+ Nový cyklus" state.

Check `adb logcat` shows the `Click:`/`Confirm:` lines from `CycleSettingsScreen` and `TodayScreen`'s `Click: Nastavení cyklu`.

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt
git commit -m "feat: replace Today cycle action row with a settings gear icon"
```
