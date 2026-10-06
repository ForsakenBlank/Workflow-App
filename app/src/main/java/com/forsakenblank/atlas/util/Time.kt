package com.forsakenblank.atlas.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun startOfDay(millis: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "${s}s"
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

fun formatDay(millis: Long): String {
    val date = millis.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormat)
    }
}

data class Streaks(val current: Int, val best: Int)

// a day counts once it has any log, today being empty does not break the streak yet
fun streaks(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Streaks {
    if (days.isEmpty()) return Streaks(0, 0)
    var current = 0
    var day = if (today in days) today else today.minusDays(1)
    while (day in days) {
        current++
        day = day.minusDays(1)
    }
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (d in days.sorted()) {
        run = if (previous != null && d == previous.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        previous = d
    }
    return Streaks(current, best)
}
