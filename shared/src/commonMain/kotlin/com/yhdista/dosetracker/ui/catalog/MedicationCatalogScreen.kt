package com.yhdista.dosetracker.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.MedicationUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCatalogScreen(
    viewModel: MedicationCatalogViewModel,
    onMedicationClick: (Long) -> Unit,
    onManageRemindersClick: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Medication Catalog", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Medication")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onEvent(CatalogEvent.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                FilterChip(
                    selected = state.showOnlyActive,
                    onClick = { viewModel.onEvent(CatalogEvent.ToggleOnlyActive(!state.showOnlyActive)) },
                    label = { Text("Pouze užívané") }
                )
            }

            if (showAddDialog) {
                AddMedicationDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, dosage, unit ->
                        viewModel.onEvent(CatalogEvent.AddMedication(name, dosage, unit))
                        showAddDialog = false
                    }
                )
            }

            when (val result = state.medications) {
                is Data.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is Data.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${result.message}")
                    }
                }
                is Data.Success -> {
                    val medications = result.data
                    if (medications.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No medications found")
                        }
                    } else {
                        LazyColumn {
                            items(medications) { medication ->
                                MedicationItem(
                                    medication = medication,
                                    isActive = medication.id in state.activeMedicationIds,
                                    onClick = { onMedicationClick(medication.id) },
                                    onLogAdHocDose = { onManageRemindersClick(medication.id) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(MedicationUnit.MG) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jednotka",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MedicationUnit.entries.forEach { medUnit ->
                        FilterChip(
                            selected = selectedUnit == medUnit,
                            onClick = { selectedUnit = medUnit },
                            label = { Text(medUnit.symbol) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, dosage, selectedUnit.symbol) },
                enabled = name.isNotBlank() && dosage.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search medications...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun MedicationItem(
    medication: Medication,
    isActive: Boolean,
    onClick: () -> Unit,
    onLogAdHocDose: () -> Unit
) {
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(medication.name)
                if (isActive) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Užívaný",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = { Text("${medication.dosage} ${medication.unit}") },
        trailingContent = {
            IconButton(onClick = onLogAdHocDose) {
                Icon(Icons.Default.Add, contentDescription = "Log manual dose")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
