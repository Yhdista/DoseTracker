package com.yhdista.dosetracker.ui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Today
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
import com.yhdista.dosetracker.ui.dose.AddDoseScreen
import com.yhdista.dosetracker.ui.dose.AddDoseViewModel
import com.yhdista.dosetracker.ui.history.HistoryScreen
import com.yhdista.dosetracker.ui.history.HistoryViewModel
import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailScreen
import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailViewModel
import com.yhdista.dosetracker.ui.navigation.Destination
import com.yhdista.dosetracker.ui.report.ReportScreen
import com.yhdista.dosetracker.ui.report.ReportViewModel
import com.yhdista.dosetracker.ui.today.TodayScreen
import com.yhdista.dosetracker.ui.today.TodayViewModel
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
                                backstack.add(Destination.AddDose(id))
                            },
                            onManageRemindersClick = { id ->
                                backstack.add(Destination.MedicationDetail(id))
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
                        ReportScreen(viewModel = koinViewModel<ReportViewModel>())
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
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
            }
        }
    }
}
