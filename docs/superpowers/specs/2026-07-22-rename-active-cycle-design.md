# Rename Active Cycle

## Context

`docs/superpowers/specs/2026-07-19-medication-cycles-design.md` shipped
cycle creation (`CreateCycleScreen`) but explicitly had no mid-cycle
editing tools. The Today dashboard's "Spravovat" button was later found to
be misleadingly labeled: it opens `CreateCycleScreen` to attach a
follow-up cycle, not to edit the currently active one (it has since been
renamed to "Přidat návazný cyklus"). There is still no way to fix a typo
in an active cycle's name after creation short of ending the cycle and
creating a new one.

`createCycle` (`MedicationRepositoryImpl.kt:358`) materializes one
`CycleWeekEntity` row per week upfront, so editing `totalWeeks` after
creation is not free: growing it is a safe append, shrinking it is
destructive (must delete week rows and anything tied to them). That's out
of scope here — see Non-Goals.

## Goal

- User can rename the active cycle from the Today dashboard header,
  without leaving the Today screen.

## Non-Goals

- No editing of `totalWeeks`, `type`, or `onCompleteAction` on an active
  cycle. Renaming only.
- No editing of non-active (`DRAFT`/`COMPLETED`) cycles — only the one
  cycle that can be `ACTIVE`.

## Design

**UI:** `CycleDashboardHeader` (`TodayScreen.kt`) gets a new
`TextButton("Přejmenovat")` alongside the existing row ("Historie cyklu",
"Přidat návazný cyklus", "Upravit týdny", "Ukončit cyklus"). Tapping it
opens an `AlertDialog` — same construction as the existing "Ukončit
cyklus?" confirm dialog in the same composable — containing one
`OutlinedTextField` pre-filled with `cycle.name`. The confirm button is
disabled while the field is blank (mirrors `CreateCycleScreen`'s
`isValid` gate); a cancel button dismisses without changes.

**Event/data flow:** new `TodayEvent.RenameActiveCycle(name: String)`,
handled in `TodayViewModel`:

```kotlin
private fun renameActiveCycle(name: String) {
    viewModelScope.launch {
        val cycle = repository.getActiveCycleOnce() ?: return@launch
        repository.updateCycle(cycle.copy(name = name))
    }
}
```

`TodayState.activeCycle` is sourced from `repository.getActiveCycle()`
(a `Flow`), so the dashboard header picks up the new name automatically
once the write lands — no manual state patch in the ViewModel.

**Validation:** the dialog's confirm button being disabled on blank input
is the only guard; a blank name can never reach `onEvent`, so
`renameActiveCycle` doesn't need its own blank check or error state.

**Logging:** the generic `onEvent: $event` log in `TodayViewModel`
already covers the event. Add one `AppLogger.d` click log on the new
button, matching the pattern already used for the other header buttons
(`Historie cyklu`, `Přidat návazný cyklus`, `Upravit týdny`, `Ukončit
cyklus`).

## Testing

One test in `TodayViewModelTest.kt`, mirroring the existing `EndActiveCycle
marks the active cycle COMPLETED without touching onCompleteAction
routing` test: seed an active cycle, dispatch
`TodayEvent.RenameActiveCycle("new name")`, assert
`repository.updateCycle` was called with `cycle.copy(name = "new name")`
and nothing else changed.
