package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.layout.*
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
    onCycleCreated: (cycleId: Long, weekCount: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdCycleId) {
        if (state.createdCycleId != null) {
            onCycleCreated(state.createdCycleId!!, state.createdWeekCount)
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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
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
            Text("Typ cyklu", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    CycleType.NORMAL to "Normální",
                    CycleType.STANDARD to "Standardní",
                    CycleType.POST to "Post"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(type)) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
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
            if (state.hasActiveCycle == true) {
                Text("Po dokončení", style = MaterialTheme.typography.labelLarge)
                listOf(
                    CycleCompleteAction.TO_STANDARD to "Na standardní",
                    CycleCompleteAction.TO_POST to "Na post",
                    CycleCompleteAction.TO_NONE to "Nic"
                ).forEach { (action, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.onCompleteAction == action,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(action)) }
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Button(
                onClick = { viewModel.onEvent(CreateCycleEvent.Save) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Uložit")
            }
        }
    }
}
