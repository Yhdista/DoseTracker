package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.clickable
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
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.shared.resources.cycle_week_number
import com.yhdista.dosetracker.shared.resources.cycleweeklist_title
import com.yhdista.dosetracker.shared.resources.error_prefix
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleWeekListScreen(
    cycleId: Long,
    viewModel: CycleWeekListViewModel,
    onBack: () -> Unit,
    onWeekClick: (weekIndex: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.cycleweeklist_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state.weeks) {
            is Data.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.error_prefix, result.message))
                }
            }
            is Data.Success -> {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(result.data, key = { it.id }) { week ->
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.cycle_week_number, week.weekIndex + 1)) },
                            modifier = Modifier.clickable { onWeekClick(week.weekIndex) }
                        )
                    }
                }
            }
        }
    }
}
