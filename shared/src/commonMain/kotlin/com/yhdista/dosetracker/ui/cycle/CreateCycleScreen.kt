package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.ui.common.ObserveAsEvents
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.shared.resources.catalog_name
import com.yhdista.dosetracker.shared.resources.createcycle_on_complete
import com.yhdista.dosetracker.shared.resources.createcycle_title
import com.yhdista.dosetracker.shared.resources.createcycle_total_weeks
import com.yhdista.dosetracker.shared.resources.createcycle_type
import com.yhdista.dosetracker.shared.resources.cycle_type_normal
import com.yhdista.dosetracker.shared.resources.cycle_type_post
import com.yhdista.dosetracker.shared.resources.cycle_type_standard
import com.yhdista.dosetracker.shared.resources.save
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCycleScreen(
    viewModel: CreateCycleViewModel,
    onBack: () -> Unit,
    onCreated: (cycleId: Long, weekCount: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.uiEvents) { event ->
        when (event) {
            is CreateCycleUiEvent.Created -> onCreated(event.cycleId, event.weekCount)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.createcycle_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (state.hasActiveCycle == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onEvent(CreateCycleEvent.NameChanged(it)) },
                    label = { Text(stringResource(Res.string.catalog_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Type selector
                Text(stringResource(Res.string.createcycle_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hasActiveCycle == false) {
                        FilterChip(
                            selected = state.type == CycleType.NORMAL,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.NORMAL)) },
                            label = { Text(stringResource(Res.string.cycle_type_normal)) }
                        )
                    }
                    FilterChip(
                        selected = state.type == CycleType.STANDARD,
                        onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.STANDARD)) },
                        label = { Text(stringResource(Res.string.cycle_type_standard)) }
                    )
                    if (state.hasActiveCycle == true) {
                        FilterChip(
                            selected = state.type == CycleType.POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.POST)) },
                            label = { Text(stringResource(Res.string.cycle_type_post)) }
                        )
                    }
                }

                // Total weeks field (hidden for STANDARD type)
                if (state.type != CycleType.STANDARD) {
                    OutlinedTextField(
                        value = state.totalWeeks.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let {
                                viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(it))
                            }
                        },
                        label = { Text(stringResource(Res.string.createcycle_total_weeks)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // On complete action selector
                if (state.type != CycleType.STANDARD && state.hasActiveCycle == false) {
                    Text(stringResource(Res.string.createcycle_on_complete), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_STANDARD,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_STANDARD)) },
                            label = { Text(stringResource(Res.string.cycle_type_standard)) }
                        )
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_POST)) },
                            label = { Text(stringResource(Res.string.cycle_type_post)) }
                        )
                    }
                }

                val isValid = state.name.isNotBlank() && (state.type == CycleType.STANDARD || state.totalWeeks > 0)
                Button(
                    onClick = { viewModel.onEvent(CreateCycleEvent.Save) },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
}
