package com.yhdista.dosetracker.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Tools", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Mock Data Generation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Use these tools to populate the database with mock doses to test reports, history, and the main dashboard.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { viewModel.generateDosesForToday() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate Doses for Today")
                    }

                    Button(
                        onClick = { viewModel.generateMockHistory() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate 7-Day Mock History")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Destructive Operations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Warning: Resetting the database will delete all medications, reminder schedules, and log history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Button(
                        onClick = { viewModel.resetDatabase() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Database")
                    }
                }
            }
        }
    }
}
