package com.forsakenblank.atlas.ui.track

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.util.formatDay
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.formatTime
import com.forsakenblank.atlas.util.streaks
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TrackerDetailViewModel(private val id: Long, private val repo: AtlasRepository) : ViewModel() {

    val tracker = repo.observeTracker(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs = repo.logs(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteLog(entry: LogEntry) {
        viewModelScope.launch { repo.deleteLog(entry.id) }
    }

    fun cancelTimer() {
        viewModelScope.launch { repo.cancelTimer(id) }
    }

    fun save(name: String, color: Int?, goal: Int?, showOnHome: Boolean) {
        val current = tracker.value ?: return
        viewModelScope.launch {
            repo.rename(current.item.copy(color = color), name)
            repo.updateTracker(current.tracker.copy(dailyGoal = goal, showOnHome = showOnHome))
        }
    }

    fun moveToTrash(done: () -> Unit) {
        val current = tracker.value ?: return
        viewModelScope.launch {
            repo.moveToTrash(current.item)
            done()
        }
    }
}

// one number per day for the chart, counts for counters and minutes for timers
private fun dailyValue(kind: TrackerKind, logs: List<LogEntry>): Float = when (kind) {
    TrackerKind.TIMER -> logs.sumOf { it.durationSeconds ?: 0L } / 60f
    else -> logs.size.toFloat()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(id: Long, navigator: AtlasNavigator) {
    val vm = atlasViewModel { TrackerDetailViewModel(id, it.repository) }
    val tracker by vm.tracker.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tracker?.item?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                editing = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move to trash") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                vm.moveToTrash { navigator.back() }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val t = tracker ?: return@Scaffold
        val accent = t.item.color.toItemColor(MaterialTheme.colorScheme.secondary)
        val byDay = remember(logs) { logs.groupBy { it.timestamp.toLocalDate() } }
        val streak = remember(byDay) { streaks(byDay.keys) }
        val today = LocalDate.now()
        val last14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
        val values = last14.map { dailyValue(t.tracker.kind, byDay[it].orEmpty()) }
        val weekTotal = values.takeLast(7).sum()
        val unitLabel = if (t.tracker.kind == TrackerKind.TIMER) "min" else t.tracker.unit.orEmpty()

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (t.tracker.runningSince != null) {
                item {
                    OutlinedButton(onClick = vm::cancelTimer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Text("  Discard the running timer")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Current streak", "${streak.current} d", Modifier.weight(1f))
                    StatCard("Best streak", "${streak.best} d", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Last 7 days", "${weekTotal.toInt()} $unitLabel".trim(), Modifier.weight(1f))
                    StatCard("All time", if (t.tracker.kind == TrackerKind.TIMER) formatDuration(logs.sumOf { it.durationSeconds ?: 0L }) else "${logs.size}", Modifier.weight(1f))
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Last 14 days", style = MaterialTheme.typography.titleSmall)
                        BarChart(values, accent, goal = t.tracker.dailyGoal?.toFloat())
                        Row {
                            Text(last14.first().dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Today", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "Nothing logged yet. Tap the tracker on Home or in Track to log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(logs.take(200), key = { it.id }) { entry ->
                val seconds = entry.durationSeconds
                ListItem(
                    headlineContent = { Text("${formatDay(entry.timestamp)}, ${formatTime(entry.timestamp)}") },
                    supportingContent = if (seconds != null) {
                        { Text(formatDuration(seconds)) }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteLog(entry) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete entry")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    val t = tracker
    if (editing && t != null) {
        EditTrackerDialog(
            tracker = t,
            onDismiss = { editing = false },
            onSave = { name, color, goal, home ->
                vm.save(name, color, goal, home)
                editing = false
            },
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BarChart(values: List<Float>, color: Color, goal: Float?) {
    val track = color.copy(alpha = 0.15f)
    val goalColor = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val max = maxOf(values.maxOrNull() ?: 0f, goal ?: 0f, 1f)
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barWidth) / 2
            drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(6f, 6f))
            val h = size.height * (v / max)
            if (h > 0f) {
                drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(6f, 6f))
            }
        }
        if (goal != null && goal > 0f) {
            val y = size.height - size.height * (goal / max)
            drawLine(goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
    }
}

@Composable
private fun EditTrackerDialog(
    tracker: TrackerWithItem,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Int?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(tracker.item.name) }
    var color by remember { mutableStateOf(tracker.item.color) }
    var goal by remember { mutableStateOf(tracker.tracker.dailyGoal?.toString().orEmpty()) }
    var showOnHome by remember { mutableStateOf(tracker.tracker.showOnHome) }
    val hasGoal = tracker.tracker.kind != TrackerKind.YES_NO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tracker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                if (hasGoal) {
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { v -> goal = v.filter(Char::isDigit).take(4) },
                        label = { Text(if (tracker.tracker.kind == TrackerKind.TIMER) "Daily goal in minutes" else "Daily goal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                ColorRow(selected = color, onSelect = { color = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show on Home", modifier = Modifier.weight(1f))
                    Switch(checked = showOnHome, onCheckedChange = { showOnHome = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), color, goal.toIntOrNull()?.takeIf { it > 0 && hasGoal }, showOnHome) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
