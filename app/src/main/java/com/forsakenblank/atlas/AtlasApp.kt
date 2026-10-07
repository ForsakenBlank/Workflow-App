package com.forsakenblank.atlas

import android.app.Application
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.BackupManager
import com.forsakenblank.atlas.data.SettingsStore
import com.forsakenblank.atlas.notify.ReminderScheduler
import com.forsakenblank.atlas.notify.createReminderChannel
import com.forsakenblank.atlas.ui.focus.FocusTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AtlasApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database by lazy { AtlasDatabase.build(this) }
    val repository by lazy { AtlasRepository(database) }
    val settingsStore by lazy { SettingsStore(this) }
    val backups by lazy { BackupManager(this, repository) }

    // null until the settings file has been read once
    val settings: StateFlow<AppSettings?> by lazy {
        settingsStore.settings.stateIn(appScope, SharingStarted.Eagerly, null)
    }

    // lives here so the timer keeps going when you leave the focus screen
    val focusTimer by lazy { FocusTimer(this) }

    val reminders by lazy { ReminderScheduler(this) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        appScope.launch { settingsStore.update(transform) }
    }

    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        appScope.launch {
            val days = settingsStore.settings.first().trashDays
            repository.purgeOldTrash(days)
        }
        watchReminders()
    }

    // plans reminders again a moment after anything they depend on changes
    @OptIn(FlowPreview::class)
    private fun watchReminders() {
        appScope.launch {
            combine(
                settingsStore.settings,
                repository.eventsBetween(0, Long.MAX_VALUE),
                repository.tasks(),
                repository.countdowns(),
            ) { settings, events, tasks, countdowns -> listOf(settings, events, tasks, countdowns) }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { runCatching { reminders.reschedule() } }
        }
    }
}
