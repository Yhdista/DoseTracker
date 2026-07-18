package com.yhdista.dosetracker.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCatalogScreen(
    viewModel: MedicationCatalogViewModel,
    onMedicationClick: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Medication Catalog") })
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
                query = state.searchQuery,
                onQueryChange = { viewModel.onEvent(CatalogEvent.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (showAddDialog) {
                AddMedicationDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, dosage, unit, frequency ->
                        viewModel.onEvent(CatalogEvent.AddMedication(name, dosage, unit, frequency))
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
                                    onClick = { onMedicationClick(medication.id) }
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
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("mg") }
    var frequency by remember { mutableStateOf("Daily") }

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
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g., mg, ml)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Frequency (e.g., Daily)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, dosage, unit, frequency) },
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
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(medication.name) },
        supportingContent = { Text("${medication.dosage} ${medication.unit} - ${medication.frequency}") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
