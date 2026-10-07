package com.forsakenblank.atlas

import android.app.Application
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.BackupManager
import com.forsakenblank.atlas.data.SettingsStore
import com.forsakenblank.atlas.ui.focus.FocusTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        appScope.launch { settingsStore.update(transform) }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val days = settingsStore.settings.first().trashDays
            repository.purgeOldTrash(days)
        }
    }
}
