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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCycleScreen(
    viewModel: CreateCycleViewModel,
    onBack: () -> Unit,
    onCreated: (cycleId: Long, weekCount: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdCycleId) {
        state.createdCycleId?.let {
            com.yhdista.dosetracker.core.AppLogger.d(
                "CreateCycleScreen",
                "Navigating away after create: cycleId=$it, weekCount=${state.createdWeekCount}"
            )
            onCreated(it, state.createdWeekCount)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nový cyklus", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
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
                    label = { Text("Název") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Type selector
                Text("Typ cyklu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hasActiveCycle == false) {
                        FilterChip(
                            selected = state.type == CycleType.NORMAL,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.NORMAL)) },
                            label = { Text("Cyklus") }
                        )
                    }
                    FilterChip(
                        selected = state.type == CycleType.STANDARD,
                        onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.STANDARD)) },
                        label = { Text("Standardní cyklus") }
                    )
                    if (state.hasActiveCycle == true) {
                        FilterChip(
                            selected = state.type == CycleType.POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.POST)) },
                            label = { Text("Post-cyklus") }
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
                        label = { Text("Počet týdnů") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // On complete action selector
                if (state.type != CycleType.STANDARD && state.hasActiveCycle == false) {
                    Text("Po skončení přejde do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_STANDARD,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_STANDARD)) },
                            label = { Text("Standardní cyklus") }
                        )
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_POST)) },
                            label = { Text("Post-cyklus") }
                        )
                    }
                }

                val isValid = state.name.isNotBlank() && (state.type == CycleType.STANDARD || state.totalWeeks > 0)
                Button(
                    onClick = { viewModel.onEvent(CreateCycleEvent.Save) },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Uložit")
                }
            }
        }
    }
}
