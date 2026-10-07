package com.forsakenblank.atlas.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.util.REMINDER_DAYS
import com.forsakenblank.atlas.util.Reminder
import com.forsakenblank.atlas.util.planReminders
import com.forsakenblank.atlas.util.startMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// keeps one alarm per planned reminder, plus one just after midnight to plan the next day
class ReminderScheduler(private val app: AtlasApp) {

    private val alarms by lazy { app.getSystemService(AlarmManager::class.java) }
    private val prefs by lazy { app.getSharedPreferences("reminders", Context.MODE_PRIVATE) }
    private val lock = Mutex()

    suspend fun reschedule() {
        lock.withLock { withContext(Dispatchers.IO) { plan() } }
    }

    private suspend fun plan() {
        val settings = app.settingsStore.settings.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // two days past the window, for reminders set a day before the event
        val events = app.repository.eventsBetween(today.startMillis(), today.plusDays(REMINDER_DAYS + 2L).startMillis()).first()
        val tasks = app.repository.tasks().first()
        val countdowns = app.repository.countdowns().first()
        val planned = planReminders(System.currentTimeMillis(), zone, settings, events, tasks, countdowns)

        val keys = planned.map { it.key }.toSet()
        prefs.getStringSet(SCHEDULED, emptySet()).orEmpty().filter { it !in keys }.forEach { cancel(it) }
        planned.forEach { schedule(it) }
        prefs.edit().putStringSet(SCHEDULED, keys).apply()

        if (settings.remindersOn) {
            val nextDay = today.plusDays(1).atStartOfDay(zone).plusMinutes(5).toInstant().toEpochMilli()
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, nextDay, refreshIntent())
        } else {
            alarms.cancel(refreshIntent())
        }
    }

    // the data part makes each reminder its own pending intent, the extras ride along
    private fun intentFor(key: String): Intent =
        Intent(app, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .setData(Uri.parse("atlas://reminder/$key"))

    private fun schedule(reminder: Reminder) {
        val intent = intentFor(reminder.key)
            .putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
            .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            .putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
        val pending = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
    }

    private fun cancel(key: String) {
        val pending = PendingIntent.getBroadcast(app, 0, intentFor(key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        app,
        0,
        Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val SCHEDULED = "scheduled"
    }
}
