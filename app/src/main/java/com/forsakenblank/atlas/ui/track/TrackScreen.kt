@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.forsakenblank.atlas.ui.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.Aggregate
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackViewModel(repo: AtlasRepository) : ViewModel() {
    val trackers = todaySummaries(repo).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun TrackScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { TrackViewModel(it.repository) }
    val trackers by vm.trackers.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val tap = rememberTrackerTap()
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = settings.shortcutSize.minWidth.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (trackers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.Insights,
                        title = "No trackers yet",
                        body = "A tracker counts, ticks off or times anything. Tap it once to log.",
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Tap to log, hold to see stats and history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(trackers, key = { it.id }) { summary ->
                TrackerButton(
                    summary = summary,
                    onTap = { tap(summary) },
                    onLongPress = { navigator.openTracker(summary.id) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New tracker") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        NewTrackerDialog(parentId = null, onDismiss = { creating = false }, onCreated = { creating = false })
    }
}

val TrackerKind.label: String
    get() = when (this) {
        TrackerKind.COUNTER -> "Counter"
        TrackerKind.YES_NO -> "Yes or no"
        TrackerKind.TIMER -> "Timer"
        TrackerKind.NUMBER -> "Number"
        TrackerKind.RATING -> "Rating"
    }

private val TrackerKind.help: String
    get() = when (this) {
        TrackerKind.COUNTER -> "Each tap adds one, like cold showers or glasses of water."
        TrackerKind.YES_NO -> "Tap to mark today done, like going to the gym."
        TrackerKind.TIMER -> "Tap to start, tap again to stop and save the time, like studying."
        TrackerKind.NUMBER -> "Tap to type an amount, like weight, steps or money spent."
        TrackerKind.RATING -> "Tap to give today one to five stars, like mood or sleep quality."
    }

val Aggregate.label: String
    get() = when (this) {
        Aggregate.SUM -> "Add them up"
        Aggregate.AVERAGE -> "Average"
        Aggregate.LAST -> "Latest only"
    }

@Composable
fun NewTrackerDialog(parentId: Long?, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val vm = atlasViewModel { NewTrackerViewModel(it.repository) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TrackerKind.COUNTER) }
    var goal by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<Int?>(null) }
    var showOnHome by remember { mutableStateOf(true) }
    var aggregate by remember { mutableStateOf(Aggregate.SUM) }
    val scope = rememberCoroutineScope()
    val hasGoal = kind == TrackerKind.COUNTER || kind == TrackerKind.TIMER || kind == TrackerKind.NUMBER
    val hasUnit = kind == TrackerKind.COUNTER || kind == TrackerKind.NUMBER

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tracker") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackerKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                Text(kind.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasGoal) {
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { v -> goal = v.filter(Char::isDigit).take(6) },
                        label = { Text(if (kind == TrackerKind.TIMER) "Daily goal in minutes (optional)" else "Daily goal (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (hasUnit) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(16) },
                        label = { Text(if (kind == TrackerKind.NUMBER) "Unit (optional, like kg or steps)" else "Unit (optional, like glasses)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == TrackerKind.NUMBER) {
                    Text("When you log more than once a day", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Aggregate.entries.forEach { a ->
                            FilterChip(selected = aggregate == a, onClick = { aggregate = a }, label = { Text(a.label) })
                        }
                    }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                ColorRow(selected = color, onSelect = { color = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show on Home", modifier = Modifier.weight(1f))
                    Switch(checked = showOnHome, onCheckedChange = { showOnHome = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val id = vm.create(
                            name = name.trim(),
                            kind = kind,
                            parentId = parentId,
                            color = color,
                            goal = goal.toIntOrNull()?.takeIf { it > 0 && hasGoal },
                            unit = unit.trim().takeIf { it.isNotEmpty() && hasUnit },
                            showOnHome = showOnHome,
                            aggregate = if (kind == TrackerKind.NUMBER) aggregate else null,
                        )
                        onCreated(id)
                    }
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

class NewTrackerViewModel(private val repo: AtlasRepository) : ViewModel() {
    suspend fun create(
        name: String,
        kind: TrackerKind,
        parentId: Long?,
        color: Int?,
        goal: Int?,
        unit: String?,
        showOnHome: Boolean,
        aggregate: Aggregate?,
    ): Long = repo.createTracker(name, kind, parentId, color, goal, unit, showOnHome, aggregate)
}
