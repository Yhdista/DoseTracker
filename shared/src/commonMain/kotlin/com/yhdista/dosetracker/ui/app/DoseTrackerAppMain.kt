package com.yhdista.dosetracker.ui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.yhdista.dosetracker.ui.catalog.MedicationCatalogScreen
import com.yhdista.dosetracker.ui.catalog.MedicationCatalogViewModel
import com.yhdista.dosetracker.ui.confirm.ConfirmDoseScreen
import com.yhdista.dosetracker.ui.confirm.ConfirmDoseViewModel
import com.yhdista.dosetracker.ui.cycle.CreateCycleScreen
import com.yhdista.dosetracker.ui.cycle.CreateCycleViewModel
import com.yhdista.dosetracker.ui.cycle.CycleHistoryScreen
import com.yhdista.dosetracker.ui.cycle.CycleHistoryViewModel
import com.yhdista.dosetracker.ui.cycle.CycleWeekEditorScreen
import com.yhdista.dosetracker.ui.cycle.CycleWeekEditorViewModel
import com.yhdista.dosetracker.ui.cycle.CycleWeekListScreen
import com.yhdista.dosetracker.ui.cycle.CycleWeekListViewModel
import com.yhdista.dosetracker.ui.dose.AddDoseScreen
import com.yhdista.dosetracker.ui.dose.AddDoseViewModel
import com.yhdista.dosetracker.ui.history.HistoryScreen
import com.yhdista.dosetracker.ui.history.HistoryViewModel
import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailScreen
import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailViewModel
import com.yhdista.dosetracker.ui.navigation.Destination
import com.yhdista.dosetracker.ui.report.MedicationReportScreen
import com.yhdista.dosetracker.ui.report.MedicationReportViewModel
import com.yhdista.dosetracker.ui.report.ReportScreen
import com.yhdista.dosetracker.ui.report.ReportViewModel
import com.yhdista.dosetracker.ui.today.TodayScreen
import com.yhdista.dosetracker.ui.today.TodayViewModel
import com.yhdista.dosetracker.ui.settings.SettingsScreen
import com.yhdista.dosetracker.ui.settings.SettingsViewModel
import com.yhdista.dosetracker.ui.debug.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun DoseTrackerAppMain(initialConfirmDoseId: Long? = null) {
    RequestNotificationPermissionEffect()

    val backstack = rememberNavBackStack(Destination.Today)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    LaunchedEffect(initialConfirmDoseId) {
        initialConfirmDoseId?.let { backstack.add(Destination.ConfirmDose(it)) }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = backstack.last() is Destination.Today,
                onClick = {
                    if (backstack.last() !is Destination.Today) {
                        backstack.clear()
                        backstack.add(Destination.Today)
                    }
                },
                icon = { Icon(Icons.Rounded.Today, contentDescription = "Today") },
                label = { Text("Today") }
            )
            item(
                selected = backstack.last() is Destination.Medications,
                onClick = {
                    if (backstack.last() !is Destination.Medications) {
                        backstack.clear()
                        backstack.add(Destination.Medications)
                    }
                },
                icon = { Icon(Icons.Rounded.Medication, contentDescription = "Medications") },
                label = { Text("Meds") }
            )
            item(
                selected = backstack.last() is Destination.History,
                onClick = {
                    if (backstack.last() !is Destination.History) {
                        backstack.clear()
                        backstack.add(Destination.History)
                    }
                },
                icon = { Icon(Icons.Rounded.History, contentDescription = "History") },
                label = { Text("History") }
            )
            item(
                selected = backstack.last() is Destination.Report,
                onClick = {
                    if (backstack.last() !is Destination.Report) {
                        backstack.clear()
                        backstack.add(Destination.Report)
                    }
                },
                icon = { Icon(Icons.Rounded.BarChart, contentDescription = "Report") },
                label = { Text("Report") }
            )
            item(
                selected = backstack.last() is Destination.Settings,
                onClick = {
                    if (backstack.last() !is Destination.Settings) {
                        backstack.clear()
                        backstack.add(Destination.Settings)
                    }
                },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
            item(
                selected = backstack.last() is Destination.Debug,
                onClick = {
                    if (backstack.last() !is Destination.Debug) {
                        backstack.clear()
                        backstack.add(Destination.Debug)
                    }
                },
                icon = { Icon(Icons.Rounded.BugReport, contentDescription = "Debug") },
                label = { Text("Debug") }
            )
        }
    ) {
        NavDisplay(
            backStack = backstack,
            modifier = Modifier.fillMaxSize(),
            onBack = { backstack.removeLastOrNull() },
            sceneStrategies = listOf(listDetailStrategy),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            )
        ) { key ->
            val destination = key as Destination
            when (destination) {
                is Destination.Today -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        TodayScreen(
                            viewModel = koinViewModel<TodayViewModel>(),
                            onNavigateToConfirm = { doseId ->
                                backstack.add(Destination.ConfirmDose(doseId))
                            },
                            onNavigateToCreateCycle = {
                                backstack.add(Destination.CreateCycle)
                            },
                            onNavigateToCycleHistory = {
                                backstack.add(Destination.CycleHistory)
                            },
                            onNavigateToManageWeeks = { cycleId ->
                                backstack.add(Destination.CycleWeekList(cycleId))
                            }
                        )
                    }
                }
                is Destination.Medications -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        MedicationCatalogScreen(
                            viewModel = koinViewModel<MedicationCatalogViewModel>(),
                            onMedicationClick = { id ->
                                backstack.add(Destination.MedicationDetail(id))
                            },
                            onManageRemindersClick = { id ->
                                backstack.add(Destination.AddDose(id))
                            }
                        )
                    }
                }
                is Destination.History -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        HistoryScreen(
                            viewModel = koinViewModel<HistoryViewModel>()
                        )
                    }
                }
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
                is Destination.AddDose -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        AddDoseScreen(
                            medicationId = destination.medicationId,
                            viewModel = koinViewModel<AddDoseViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.MedicationDetail -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        MedicationDetailScreen(
                            medicationId = destination.id,
                            viewModel = koinViewModel<MedicationDetailViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.ConfirmDose -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        ConfirmDoseScreen(
                            doseId = destination.doseId,
                            viewModel = koinViewModel<ConfirmDoseViewModel>(),
                            onBack = { backstack.removeLastOrNull() },
                            onNavigateToMedicationDetail = { medicationId ->
                                backstack.removeLastOrNull()
                                backstack.add(Destination.MedicationDetail(medicationId))
                            }
                        )
                    }
                }
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
                is Destination.CreateCycle -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CreateCycleScreen(
                            viewModel = koinViewModel<CreateCycleViewModel>(),
                            onBack = { backstack.removeLastOrNull() },
                            onCreated = { cycleId, weekCount ->
                                backstack.removeLastOrNull()
                                if (weekCount > 0) {
                                    backstack.add(Destination.CycleWeekList(cycleId))
                                }
                            }
                        )
                    }
                }
                is Destination.CycleWeekEditor -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CycleWeekEditorScreen(
                            cycleId = destination.cycleId,
                            weekIndex = destination.weekIndex,
                            viewModel = koinViewModel<CycleWeekEditorViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.CycleWeekList -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CycleWeekListScreen(
                            cycleId = destination.cycleId,
                            viewModel = koinViewModel<CycleWeekListViewModel>(),
                            onBack = { backstack.removeLastOrNull() },
                            onWeekClick = { weekIndex ->
                                backstack.add(Destination.CycleWeekEditor(destination.cycleId, weekIndex))
                            }
                        )
                    }
                }
                is Destination.CycleHistory -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CycleHistoryScreen(
                            viewModel = koinViewModel<CycleHistoryViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.Settings -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        SettingsScreen(
                            viewModel = koinViewModel<SettingsViewModel>()
                        )
                    }
                }
                is Destination.Debug -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        DebugScreen(
                            viewModel = koinViewModel<DebugViewModel>(),
                            onNavigateToStyleManual = { backstack.add(Destination.StyleManual) }
                        )
                    }
                }
                is Destination.StyleManual -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleManualScreen(
                            onNavigateToSection = { section -> backstack.add(section) },
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.StyleTypography -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleTypographyScreen(
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.StyleIcons -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleIconsScreen(
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.StyleColors -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleColorsScreen(
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.StyleButtons -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleButtonsScreen(
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.StyleTexts -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleTextsScreen(
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.StyleComponents -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        StyleComponentsScreen(
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
            }
        }
    }
}
