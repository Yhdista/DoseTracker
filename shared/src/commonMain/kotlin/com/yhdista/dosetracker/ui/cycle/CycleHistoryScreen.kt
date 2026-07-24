package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.ui.common.DataContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleHistoryScreen(
    viewModel: CycleHistoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historie cyklu", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        DataContent(state, Modifier.padding(padding)) { cycles ->
            if (cycles.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Žádný dokončený cyklus")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cycles, key = { it.id }) { cycle ->
                        ListItem(
                            headlineContent = { Text(cycle.name) },
                            supportingContent = { Text("${cycleTypeLabel(cycle.type)} - začátek ${cycle.startDate}") }
                        )
                    }
                }
            }
        }
    }
}

private fun cycleTypeLabel(type: CycleType): String = when (type) {
    CycleType.NORMAL -> "Cyklus"
    CycleType.STANDARD -> "Standardní cyklus"
    CycleType.POST -> "Post-cyklus"
}
