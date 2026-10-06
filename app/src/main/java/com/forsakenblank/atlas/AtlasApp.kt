package com.forsakenblank.atlas

import android.app.Application
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.BackupManager
import com.forsakenblank.atlas.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AtlasApp : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    val database by lazy { AtlasDatabase.build(this) }
    val repository by lazy { AtlasRepository(database) }
    val settings by lazy { SettingsStore(this) }
    val backups by lazy { BackupManager(this, repository) }

    override fun onCreate() {
        super.onCreate()
        // trash keeps things for 30 days
        appScope.launch { repository.purgeOldTrash() }
    }
}
