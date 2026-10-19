package lk.ridewise.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.ridewise.domain.model.RideAnalysis
import lk.ridewise.ui.components.*

@Composable fun HistoryScreen(history: List<RideAnalysis>, busy: Boolean, onOpen: (String) -> Unit, onClear: () -> Unit, onDemo: () -> Unit) {
    var clear by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Your ride notebook.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text("${history.size} saved analyses · stored only on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (history.isEmpty()) item {
            Notice("No saved analyses yet. Try a demo, review the numbers and save your first analysis.")
            Spacer(Modifier.height(16.dp))
            Button(onDemo, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("Try Demo Ride") }
        } else {
            item { TextButton({ clear = true }, enabled = !busy) { Text("Clear all history") } }
            items(history, key = { it.id }) { analysis -> AnalysisCard(analysis) { onOpen(analysis.id) } }
        }
        item { Text("Tap an analysis to view its original cost assumptions or delete it. Screenshots and full recognized screen text are never saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear all ride history?") },
        text = { Text("All ${history.size} saved analyses will be permanently deleted. Your vehicle settings will remain.") },
        confirmButton = { TextButton({ clear = false; onClear() }) { Text("Delete all analyses") } },
        dismissButton = { TextButton({ clear = false }) { Text("Cancel") } })
}
