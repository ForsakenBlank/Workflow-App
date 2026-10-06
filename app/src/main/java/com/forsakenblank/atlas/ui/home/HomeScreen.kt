package com.forsakenblank.atlas.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.NoteRow
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.track.TrackerButton
import com.forsakenblank.atlas.ui.track.TrackerSummary
import com.forsakenblank.atlas.ui.track.tapMessage
import com.forsakenblank.atlas.ui.track.todaySummaries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HomeViewModel(private val repo: AtlasRepository) : ViewModel() {

    val shortcuts = todaySummaries(repo)
        .map { list -> list.filter { it.tracker.showOnHome } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedNotes = repo.notes()
        .map { notes -> notes.filter { it.item.pinned }.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun tap(summary: TrackerSummary): Long? = repo.tap(summary.id)

    fun undo(logId: Long) {
        viewModelScope.launch { repo.deleteLog(logId) }
    }
}

@Composable
fun HomeScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { HomeViewModel(it.repository) }
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val pinned by vm.pinnedNotes.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullWidth { TodayCard(shortcuts) }

        fullWidth { SectionTitle("Shortcuts") }
        if (shortcuts.isEmpty()) {
            fullWidth {
                EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = "No shortcuts yet",
                    body = "Make a tracker in the Track tab and it shows up here for one tap logging.",
                )
            }
        }
        items(shortcuts, key = { it.id }) { summary ->
            TrackerButton(
                summary = summary,
                onTap = {
                    val message = summary.tapMessage()
                    scope.launch {
                        val logId = vm.tap(summary)
                        if (logId != null && message != null) {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
                            if (result == SnackbarResult.ActionPerformed) vm.undo(logId)
                        }
                    }
                },
                onLongPress = { navigator.openTracker(summary.id) },
            )
        }

        if (pinned.isNotEmpty()) {
            fullWidth { SectionTitle("Pinned notes") }
            items(pinned, key = { "note-${it.item.id}" }) { note ->
                PinnedNote(note) { navigator.openNote(note.item.id) }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun TodayCard(shortcuts: List<TrackerSummary>) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val left = shortcuts.count { !it.goalMet }
    val summary = when {
        shortcuts.isEmpty() -> "Nothing to track yet."
        left == 0 -> "Everything is done for today. Nice."
        left == 1 -> "1 tracker left today."
        else -> "$left trackers left today."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(greeting, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun PinnedNote(note: NoteRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(note.item.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                note.text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
