# Cycle Settings Screen

## Context

`docs/superpowers/specs/2026-07-22-rename-active-cycle-design.md` added a
"Přejmenovat" button to the Today dashboard's `CycleDashboardHeader`,
bringing the button row to five: "Přejmenovat", "Historie cyklu", "Přidat
návazný cyklus", "Upravit týdny", "Ukončit cyklus" (`TodayScreen.kt:248-280`).
Five text buttons in a `FlowRow` wraps awkwardly and buries the cycle's
actual stats (name, dates, progress) under a growing action list.

## Goal

- Move all five cycle actions off the Today dashboard header into a
  dedicated "Nastavení cyklu" subscreen, reachable via a single gear icon
  on the header card.
- Today dashboard header keeps showing cycle stats (name, type, start
  date, elapsed/remaining days) — only the action row moves out.

## Non-Goals

- No change to what any of the five actions do (rename, view history, add
  follow-up cycle, edit weeks, end cycle) — only where they live.
- No editing of `totalWeeks`, `type`, or `onCompleteAction` on the active
  cycle (unchanged from the rename spec's Non-Goals).
- No editing of non-active (`DRAFT`/`COMPLETED`) cycles — the settings
  screen only ever operates on the current `ACTIVE` cycle, same as today.
- No deep-linking to a specific cycle's settings by ID — the screen always
  reflects whichever cycle is currently active.

## Design

### Navigation

New `Destination.CycleSettings` (`data object`, no params) added to the
`Destination` sealed interface in
`shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`.
No cycle ID is threaded through nav — the destination is only ever reached
from the Today dashboard's active-cycle header, and the screen resolves
"the" cycle the same way `TodayViewModel` does today: via
`repository.getActiveCycle()`.

Wired into `DoseTrackerAppMain`'s `NavDisplay` as a `detailPane` `NavEntry`,
following the same shape as `Destination.CycleHistory`:

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

The existing `Destination.Today` `NavEntry` also changes: `onNavigateToCycleHistory`
and `onNavigateToManageWeeks` arguments are dropped from the `TodayScreen(...)`
call (params no longer exist), and a new one is added:

```kotlin
onNavigateToCycleSettings = { backstack.add(Destination.CycleSettings) }
```

### `CycleDashboardHeader` changes (`TodayScreen.kt`)

Remove: the `FlowRow` with its five `TextButton`s, both `AlertDialog`s
("Přejmenovat cyklus", "Ukončit cyklus?"), and the `showRenameDialog` /
`showEndConfirm` / `renameText` state that back them.

Add: a gear `IconButton` (`Icons.Rounded.Settings`) placed next to the
cycle name at the top of the card, wired to a new `onOpenSettings: () ->
Unit` parameter. The card's stats (name, type, start date, elapsed/
remaining days) are untouched.

`CycleDashboardHeader`'s signature shrinks to:

```kotlin
private fun CycleDashboardHeader(
    cycle: Cycle,
    onOpenSettings: () -> Unit
)
```

`onOpenHistory`, `onManageCycle`, `onManageWeeks`, `onEndCycle`, `onRename`
are all removed — nothing inside `CycleDashboardHeader` needs them anymore.

`TodayContent` / `TodayScreen` changes: `onOpenCycleHistory` and
`onManageWeeks` parameters are removed (nothing calls them once the button
row is gone). A new `onNavigateToCycleSettings: () -> Unit` parameter is
added and threaded to `CycleDashboardHeader`'s `onOpenSettings`.
`onCreateCycle` / `onNavigateToCreateCycle` stays — `NoCycleHeader`'s "+
Nový cyklus" button (shown when there's no active cycle) still needs it.

### `TodayViewModel` changes

Remove `TodayEvent.RenameActiveCycle` and `TodayEvent.EndActiveCycle`, their
branches in `onEvent`, and the `renameActiveCycle()` / `endActiveCycle()`
handlers. This logic moves to `CycleSettingsViewModel` below. Remove the
two corresponding tests from `TodayViewModelTest.kt`.

### `CycleSettingsScreen` + `CycleSettingsViewModel` (new, `ui/cycle` package)

`CycleSettingsViewModel` observes `repository.getActiveCycle(): Flow<Data<Cycle?>>`
directly (same source `TodayViewModel` uses today) and exposes it as
`uiState: StateFlow<Data<Cycle?>>` via `stateIn`. No `SavedStateHandle` /
cycle-ID plumbing needed, unlike `CycleWeekListViewModel` — there is only
ever one editable cycle, so there's nothing to key on.

Two suspend actions, mirroring the handlers removed from `TodayViewModel`:

```kotlin
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
```

`CycleSettingsScreen` layout: `Scaffold` with a `TopAppBar` ("Nastavení
cyklu", back arrow navigation icon) and a `LazyColumn` of `ListItem`s, one
per action, in the same order as the old button row:

1. "Přejmenovat" → opens the rename `AlertDialog` (moved here verbatim
   from `CycleDashboardHeader`: pre-filled `OutlinedTextField`, confirm
   disabled while blank) → `viewModel.rename(name)`
2. "Historie cyklu" → `onNavigateToCycleHistory()`
3. "Přidat návazný cyklus" → `onNavigateToCreateCycle()`
4. "Upravit týdny" → `onNavigateToManageWeeks(cycle.id)`
5. "Ukončit cyklus" (styled as destructive — e.g. error-colored text) →
   opens the end-confirm `AlertDialog` (moved here verbatim) → on confirm:
   dismiss dialog, call `viewModel.endCycle()` (fire-and-forget, same as
   today) and `onEnded()` together in the same click handler — no waiting
   for the repository write to complete before popping back, matching how
   the rest of this codebase treats these mutations (eventual consistency
   via the `Flow`, not an awaited round-trip).

While `uiState` is `Data.Loading` or the cycle is `null` (e.g. the active
cycle got ended from another entry point while this screen was open),
show a `CircularProgressIndicator`, consistent with `CycleWeekListScreen`'s
handling of its `Data` states.

**Logging:** every `ListItem` click and dialog confirm gets one
`AppLogger.d` line, matching the existing pattern (`Click: Přejmenovat
(cycleId=..., name=...)`, `Confirm: ...`) carried over from
`CycleDashboardHeader`. Add one `Click: Nastavení cyklu` log on the new
gear icon in `CycleDashboardHeader`.

## Testing

New `CycleSettingsViewModelTest.kt`, mirroring the two tests removed from
`TodayViewModelTest.kt`:

- seed an active cycle, call `viewModel.rename("primo")`, assert
  `repository.updateCycle` was called with `cycle.copy(name = "primo")`
- seed an active cycle, call `viewModel.endCycle()`, assert
  `repository.updateCycle` was called with `cycle.copy(status =
  CycleStatus.COMPLETED)` and nothing else changed

Manual verification: tap the gear icon on the Today dashboard, confirm
`CycleSettingsScreen` opens with all five actions; run through rename and
end-cycle, confirm both behave identically to before (rename reflects
immediately if you navigate back, end-cycle pops back to Today with
`NoCycleHeader` showing).
