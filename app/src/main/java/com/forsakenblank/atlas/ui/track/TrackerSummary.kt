package com.forsakenblank.atlas.ui.track

import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.Tracker
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.startOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

data class TrackerSummary(
    val item: Item,
    val tracker: Tracker,
    val todayCount: Int,
    val todaySeconds: Long,
) {
    val id: Long get() = item.id
    val kind: TrackerKind get() = tracker.kind
    val running: Boolean get() = tracker.runningSince != null
    val doneToday: Boolean get() = todayCount > 0

    // null when there is no goal to measure against
    val progress: Float?
        get() = when (kind) {
            TrackerKind.YES_NO -> if (doneToday) 1f else 0f
            TrackerKind.COUNTER -> tracker.dailyGoal?.takeIf { it > 0 }?.let { todayCount / it.toFloat() }
            TrackerKind.TIMER -> tracker.dailyGoal?.takeIf { it > 0 }?.let { todaySeconds / (it * 60f) }
        }?.coerceIn(0f, 1f)

    val goalMet: Boolean get() = (progress ?: if (doneToday) 1f else 0f) >= 1f

    fun todayLabel(): String = when (kind) {
        TrackerKind.YES_NO -> if (doneToday) "Done today" else "Not yet today"
        TrackerKind.COUNTER -> {
            val unit = tracker.unit?.let { " $it" }.orEmpty()
            tracker.dailyGoal?.let { "$todayCount of $it$unit" } ?: "$todayCount$unit today"
        }
        TrackerKind.TIMER -> {
            val time = if (todaySeconds == 0L) "0m" else formatDuration(todaySeconds)
            tracker.dailyGoal?.let { "$time of ${it}m" } ?: "$time today"
        }
    }
}

fun summarise(trackers: List<TrackerWithItem>, todayLogs: List<LogEntry>): List<TrackerSummary> {
    val byTracker = todayLogs.groupBy { it.trackerId }
    return trackers.map { t ->
        val logs = byTracker[t.item.id].orEmpty()
        TrackerSummary(
            item = t.item,
            tracker = t.tracker,
            todayCount = logs.size,
            todaySeconds = logs.sumOf { it.durationSeconds ?: 0L },
        )
    }
}

// emits the start of today and again after midnight, so "today" never goes stale
private val today: Flow<Long> = flow {
    while (true) {
        emit(startOfDay())
        delay(60_000)
    }
}.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
fun todaySummaries(repo: AtlasRepository): Flow<List<TrackerSummary>> =
    today.flatMapLatest { start -> combine(repo.trackers(), repo.logsSince(start)) { t, logs -> summarise(t, logs) } }

// what the snackbar says after a tap, worked out before the tap happens
fun TrackerSummary.tapMessage(): String? = when (kind) {
    TrackerKind.COUNTER -> "Logged ${item.name}"
    TrackerKind.YES_NO -> if (doneToday) null else "${item.name} done for today"
    TrackerKind.TIMER -> if (running) "Saved ${item.name} time" else null
}
