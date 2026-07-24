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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.yhdista.dosetracker.ui.cycle.CycleSettingsScreen
import com.yhdista.dosetracker.ui.cycle.CycleSettingsViewModel
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
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.settings_title
import com.yhdista.dosetracker.shared.resources.tab_debug
import com.yhdista.dosetracker.shared.resources.tab_history
import com.yhdista.dosetracker.shared.resources.tab_meds
import com.yhdista.dosetracker.shared.resources.tab_report
import com.yhdista.dosetracker.shared.resources.tab_today
import com.yhdista.dosetracker.ui.navigation.Destination
import org.jetbrains.compose.resources.stringResource
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
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun DoseTrackerAppMain(
    initialConfirmDoseId: Long? = null,
    isDebugBuild: Boolean = false,
) {
    RequestNotificationPermissionEffect()

    // One back stack per top-level tab: switching tabs preserves each tab's detail
    // stack and scroll state instead of clearing everything.
    val todayStack = rememberNavBackStack(Destination.Today)
    val medsStack = rememberNavBackStack(Destination.Medications)
    val historyStack = rememberNavBackStack(Destination.History)
    val reportStack = rememberNavBackStack(Destination.Report)
    val settingsStack = rememberNavBackStack(Destination.Settings)
    val debugStack = rememberNavBackStack(Destination.Debug)
    var currentTab by rememberSaveable { mutableStateOf(TAB_TODAY) }

    val tabs = buildList {
        add(TabSpec(TAB_TODAY, stringResource(Res.string.tab_today), Icons.Rounded.Today, todayStack))
        add(TabSpec(TAB_MEDS, stringResource(Res.string.tab_meds), Icons.Rounded.Medication, medsStack))
        add(TabSpec(TAB_HISTORY, stringResource(Res.string.tab_history), Icons.Rounded.History, historyStack))
        add(TabSpec(TAB_REPORT, stringResource(Res.string.tab_report), Icons.Rounded.BarChart, reportStack))
        add(TabSpec(TAB_SETTINGS, stringResource(Res.string.settings_title), Icons.Rounded.Settings, settingsStack))
        if (isDebugBuild) add(TabSpec(TAB_DEBUG, stringResource(Res.string.tab_debug), Icons.Rounded.BugReport, debugStack))
    }
    val backstack = (tabs.find { it.key == currentTab } ?: tabs.first()).stack
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    LaunchedEffect(backstack.last()) {
        com.yhdista.dosetracker.core.AppLogger.i("Navigation", "Screen Transition: ${backstack.last()::class.simpleName ?: "Unknown"} (Route: ${backstack.last()})")
    }

    // Track the handled id so a restored back stack doesn't get the entry re-added.
    var handledConfirmDoseId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(initialConfirmDoseId) {
        if (initialConfirmDoseId != null && initialConfirmDoseId != handledConfirmDoseId) {
            handledConfirmDoseId = initialConfirmDoseId
            currentTab = TAB_TODAY
            todayStack.add(Destination.ConfirmDose(initialConfirmDoseId))
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { tab ->
                item(
                    selected = currentTab == tab.key,
                    onClick = {
                        if (currentTab == tab.key) {
                            // Reselecting the active tab pops it back to its root.
                            while (tab.stack.size > 1) tab.stack.removeLastOrNull()
                        } else {
                            currentTab = tab.key
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) }
                )
            }
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
                            onNavigateToCycleSettings = {
                                backstack.add(Destination.CycleSettings)
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
                            viewModel = koinViewModel<AddDoseViewModel> { parametersOf(destination.medicationId) },
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
                            viewModel = koinViewModel<MedicationDetailViewModel> { parametersOf(destination.id) },
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
                            viewModel = koinViewModel<ConfirmDoseViewModel> { parametersOf(destination.doseId) },
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
                            viewModel = koinViewModel<MedicationReportViewModel> { parametersOf(destination.medicationId) },
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
                            viewModel = koinViewModel<CycleWeekEditorViewModel> { parametersOf(destination.cycleId, destination.weekIndex) },
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
                            viewModel = koinViewModel<CycleWeekListViewModel> { parametersOf(destination.cycleId) },
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

private const val TAB_TODAY = "today"
private const val TAB_MEDS = "meds"
private const val TAB_HISTORY = "history"
private const val TAB_REPORT = "report"
private const val TAB_SETTINGS = "settings"
private const val TAB_DEBUG = "debug"

private data class TabSpec(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val stack: androidx.navigation3.runtime.NavBackStack<NavKey>,
)
