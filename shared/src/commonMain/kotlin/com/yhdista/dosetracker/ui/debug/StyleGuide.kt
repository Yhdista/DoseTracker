package com.yhdista.dosetracker.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.MedicationUnit
import com.yhdista.dosetracker.ui.catalog.MedicationItem
import com.yhdista.dosetracker.ui.today.DoseItem
import kotlin.math.roundToInt
import kotlin.time.Clock
import com.yhdista.dosetracker.ui.navigation.Destination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleManualScreen(
    onNavigateToSection: (Destination) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grafický manuál", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ManualCategoryItem(
                title = "Typografie",
                description = "Přehled textových stylů a velikostí písma v aplikaci.",
                icon = Icons.Rounded.Edit,
                onClick = { onNavigateToSection(Destination.StyleTypography) }
            )
            ManualCategoryItem(
                title = "Ikony",
                description = "Knihovna nejpoužívanějších ikon v aplikaci DoseTracker.",
                icon = Icons.Rounded.Star,
                onClick = { onNavigateToSection(Destination.StyleIcons) }
            )
            ManualCategoryItem(
                title = "Barvy",
                description = "Barevná paleta motivu a kontrastní poměry.",
                icon = Icons.Rounded.Palette,
                onClick = { onNavigateToSection(Destination.StyleColors) }
            )
            ManualCategoryItem(
                title = "Tlačítka",
                description = "Varianty tlačítek, interakční stavy a FAB.",
                icon = Icons.Rounded.TouchApp,
                onClick = { onNavigateToSection(Destination.StyleButtons) }
            )
            ManualCategoryItem(
                title = "Texty",
                description = "Typické formáty zpráv, upozornění a tipů.",
                icon = Icons.Rounded.Info,
                onClick = { onNavigateToSection(Destination.StyleTexts) }
            )
            ManualCategoryItem(
                title = "Komponenty",
                description = "Ukázky hotových karet a prvků uživatelského rozhraní.",
                icon = Icons.AutoMirrored.Rounded.List,
                onClick = { onNavigateToSection(Destination.StyleComponents) }
            )
        }
    }
}

@Composable
fun ManualCategoryItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Přejít"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleTypographyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Typografie", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TypographyManual()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleIconsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ikony", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            IconsManual()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleColorsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barvy", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ColorsManual()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleButtonsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tlačítka", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ButtonsManual()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleTextsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Texty", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TextsManual()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleComponentsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Komponenty", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ComponentsManual()
        }
    }
}

@Composable
private fun TypographyManual() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TypographyItem(name = "Display Large", style = MaterialTheme.typography.displayLarge)
        TypographyItem(name = "Headline Medium", style = MaterialTheme.typography.headlineMedium)
        TypographyItem(name = "Title Large", style = MaterialTheme.typography.titleLarge)
        TypographyItem(name = "Title Medium", style = MaterialTheme.typography.titleMedium)
        TypographyItem(name = "Body Large", style = MaterialTheme.typography.bodyLarge)
        TypographyItem(name = "Body Medium", style = MaterialTheme.typography.bodyMedium)
        TypographyItem(name = "Label Large", style = MaterialTheme.typography.labelLarge)
        TypographyItem(name = "Label Medium", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TypographyItem(name: String, style: androidx.compose.ui.text.TextStyle) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = "Rychlý hnědý pes skáče přes líného psa.", style = style)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun IconsManual() {
    val iconsList = listOf(
        Pair(Icons.Rounded.Today, "Today"),
        Pair(Icons.Rounded.Medication, "Medications"),
        Pair(Icons.Rounded.History, "History"),
        Pair(Icons.Rounded.BarChart, "BarChart"),
        Pair(Icons.Rounded.Settings, "Settings"),
        Pair(Icons.Rounded.BugReport, "BugReport"),
        Pair(Icons.Rounded.Add, "Add"),
        Pair(Icons.Rounded.Delete, "Delete"),
        Pair(Icons.Rounded.Schedule, "Schedule"),
        Pair(Icons.Rounded.CheckCircle, "CheckCircle"),
        Pair(Icons.Rounded.RadioButtonUnchecked, "Unchecked"),
        Pair(Icons.AutoMirrored.Rounded.ArrowBack, "ArrowBack"),
        Pair(Icons.AutoMirrored.Rounded.ArrowForward, "ArrowForward"),
        Pair(Icons.Rounded.Info, "Info"),
        Pair(Icons.Rounded.Warning, "Warning")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        iconsList.chunked(3).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowIcons.forEach { (iconVector, name) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = name,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (rowIcons.size < 3) {
                    repeat(3 - rowIcons.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorsManual() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hlavní barvy (Primary / Secondary / Tertiary)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock(name = "Primary", color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
            ColorBlock(name = "Primary Container", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock(name = "Secondary", color = MaterialTheme.colorScheme.secondary, textColor = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.weight(1f))
            ColorBlock(name = "Secondary Container", color = MaterialTheme.colorScheme.secondaryContainer, textColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock(name = "Tertiary", color = MaterialTheme.colorScheme.tertiary, textColor = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.weight(1f))
            ColorBlock(name = "Tertiary Container", color = MaterialTheme.colorScheme.tertiaryContainer, textColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Neutrální barvy (Surface / Background / Outline)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock(name = "Background", color = MaterialTheme.colorScheme.background, textColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            ColorBlock(name = "Surface", color = MaterialTheme.colorScheme.surface, textColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock(name = "Surface Variant", color = MaterialTheme.colorScheme.surfaceVariant, textColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            ColorBlock(name = "Outline", color = MaterialTheme.colorScheme.outline, textColor = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Chybové barvy (Error)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock(name = "Error", color = MaterialTheme.colorScheme.error, textColor = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f))
            ColorBlock(name = "Error Container", color = MaterialTheme.colorScheme.errorContainer, textColor = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorBlock(
    name: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(56.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ButtonsManual() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Primární tlačítko (Button)")
        }
        
        ElevatedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Vyvýšené tlačítko (ElevatedButton)")
        }

        FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Tónové tlačítko (FilledTonalButton)")
        }

        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Obrysové tlačítko (OutlinedButton)")
        }

        TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Textové tlačítko (TextButton)")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.Rounded.Add, contentDescription = "FAB Add")
            }
            
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Delete, contentDescription = "Icon Delete")
            }
        }
        
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Zakázané tlačítko (Disabled)")
        }
    }
}

@Composable
private fun TextsManual() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Nadpis sekce (H1/H2 ekvivalent)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Toto je běžný odstavec textu (Body Large). Používá se pro delší texty, popisy a obecné informace v aplikaci.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Vedlejší doplňující text (Body Medium / Variant color). Používá se pro popisky pod nadpisy nebo pomocné informace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Chybové hlášení nebo varování (Error color). Používá se pro chybové stavy a důležitá varování.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Tip: Pravidelné užívání léků zvyšuje účinnost léčby.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Pozor: Nepřekračujte doporučenou denní dávku léků.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ComponentsManual() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("1. Položka léku v katalogu (MedicationItem)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            MedicationItem(
                medication = Medication(id = 1, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG),
                isActive = true,
                onClick = {},
                onLogAdHocDose = {}
            )
        }

        Text("2. Položka dávky v dnešním přehledu (DoseItem)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        DoseItem(
            dose = Dose(
                id = 1,
                medicationId = 1,
                medicationName = "Ibalgin 400",
                timestamp = Clock.System.now(),
                amount = 400.0,
                unit = "mg",
                status = DoseStatus.PENDING
            ),
            onClick = {},
            onToggleStatus = {}
        )
        DoseItem(
            dose = Dose(
                id = 2,
                medicationId = 1,
                medicationName = "Paralen 500",
                timestamp = Clock.System.now(),
                amount = 500.0,
                unit = "mg",
                status = DoseStatus.TAKEN
            ),
            onClick = {},
            onToggleStatus = {}
        )

        Text("3. Souhrnná karta léku (MedicationSummaryCard)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Paralen 500", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Užito: 5  Zameškáno: 1  Přeskočeno: 0")
                Text(
                    "Nadcházející: 2",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val percentage = (2500.0 / 3000.0 * 100).roundToInt()
                val progress = (2500.0 / 3000.0).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(4.dp))
                Text(
                    "2500.0 / 3000.0 mg ($percentage%)",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
