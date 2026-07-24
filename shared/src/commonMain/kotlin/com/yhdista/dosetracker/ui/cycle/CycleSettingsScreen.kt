package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.shared.resources.cancel
import com.yhdista.dosetracker.shared.resources.catalog_name
import com.yhdista.dosetracker.shared.resources.cycle_history_title
import com.yhdista.dosetracker.shared.resources.cyclesettings_add_follow_up
import com.yhdista.dosetracker.shared.resources.cyclesettings_end
import com.yhdista.dosetracker.shared.resources.cyclesettings_end_cycle
import com.yhdista.dosetracker.shared.resources.cyclesettings_end_cycle_confirm_text
import com.yhdista.dosetracker.shared.resources.cyclesettings_end_cycle_confirm_title
import com.yhdista.dosetracker.shared.resources.cyclesettings_manage_weeks
import com.yhdista.dosetracker.shared.resources.cyclesettings_rename
import com.yhdista.dosetracker.shared.resources.cyclesettings_rename_cycle
import com.yhdista.dosetracker.shared.resources.save
import com.yhdista.dosetracker.shared.resources.today_cycle_settings
import com.yhdista.dosetracker.shared.resources.today_no_active_cycle
import com.yhdista.dosetracker.ui.common.DataContent
import com.yhdista.dosetracker.ui.common.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource

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

    ObserveAsEvents(viewModel.uiEvents) { event ->
        when (event) {
            CycleSettingsUiEvent.CycleEnded -> onEnded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.today_cycle_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        DataContent(state, Modifier.padding(padding)) { cycle ->
            if (cycle == null) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.today_no_active_cycle))
                }
            } else {
                CycleSettingsList(
                    cycle = cycle,
                    modifier = Modifier.padding(padding),
                    onRename = viewModel::rename,
                    onOpenHistory = onNavigateToCycleHistory,
                    onAddFollowUp = onNavigateToCreateCycle,
                    onManageWeeks = { onNavigateToManageWeeks(cycle.id) },
                    onEndCycle = { viewModel.endCycle() }
                )
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
            title = { Text(stringResource(Res.string.cyclesettings_rename_cycle)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(Res.string.catalog_name)) },
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
                ) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text(stringResource(Res.string.cyclesettings_end_cycle_confirm_title)) },
            text = { Text(stringResource(Res.string.cyclesettings_end_cycle_confirm_text, cycle.name)) },
            confirmButton = {
                TextButton(onClick = {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Confirm: Ukončit cyklus (cycleId=${cycle.id}, name='${cycle.name}')")
                    showEndConfirm = false
                    onEndCycle()
                }) { Text(stringResource(Res.string.cyclesettings_end)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.cyclesettings_rename)) },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Přejmenovat (cycleId=${cycle.id}, name='${cycle.name}')")
                    renameText = cycle.name
                    showRenameDialog = true
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.cycle_history_title)) },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Historie cyklu (cycleId=${cycle.id}, name='${cycle.name}')")
                    onOpenHistory()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.cyclesettings_add_follow_up)) },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Přidat návazný cyklus (cycleId=${cycle.id}, name='${cycle.name}') -> opens CreateCycleScreen to attach a follow-up cycle")
                    onAddFollowUp()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.cyclesettings_manage_weeks)) },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Upravit týdny (cycleId=${cycle.id}, name='${cycle.name}')")
                    onManageWeeks()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.cyclesettings_end_cycle), color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    com.yhdista.dosetracker.core.AppLogger.d("CycleSettingsScreen", "Click: Ukončit cyklus, opening confirm dialog (cycleId=${cycle.id}, name='${cycle.name}')")
                    showEndConfirm = true
                }
            )
        }
    }
}
