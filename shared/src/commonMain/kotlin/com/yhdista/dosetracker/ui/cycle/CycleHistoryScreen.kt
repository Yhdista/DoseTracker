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
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.shared.resources.cycle_history_title
import com.yhdista.dosetracker.shared.resources.cycle_type_normal
import com.yhdista.dosetracker.shared.resources.cycle_type_post
import com.yhdista.dosetracker.shared.resources.cycle_type_standard
import com.yhdista.dosetracker.shared.resources.cyclehistory_empty
import com.yhdista.dosetracker.shared.resources.cyclehistory_type_started
import com.yhdista.dosetracker.ui.common.DataContent
import org.jetbrains.compose.resources.stringResource

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
                title = { Text(stringResource(Res.string.cycle_history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        DataContent(state, Modifier.padding(padding)) { cycles ->
            if (cycles.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.cyclehistory_empty))
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
                            supportingContent = {
                                Text(
                                    stringResource(
                                        Res.string.cyclehistory_type_started,
                                        cycleTypeLabel(cycle.type),
                                        cycle.startDate.toString()
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun cycleTypeLabel(type: CycleType): String = when (type) {
    CycleType.NORMAL -> stringResource(Res.string.cycle_type_normal)
    CycleType.STANDARD -> stringResource(Res.string.cycle_type_standard)
    CycleType.POST -> stringResource(Res.string.cycle_type_post)
}
