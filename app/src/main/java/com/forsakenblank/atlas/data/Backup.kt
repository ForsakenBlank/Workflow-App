package com.forsakenblank.atlas.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Backup(
    val version: Int,
    val exportedAt: Long,
    val items: List<Item>,
    val notes: List<NoteBody>,
    val trackers: List<Tracker>,
    val logs: List<LogEntry>,
    val tags: List<Tag>,
    val itemTags: List<ItemTag>,
    // added in version 2, older backups simply have none
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val slots: List<TimetableSlot> = emptyList(),
    val terms: List<Term> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

class BackupManager(private val context: Context, private val repository: AtlasRepository) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri) {
        val text = json.encodeToString(Backup.serializer(), repository.snapshot())
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: error("Could not open the backup file")
        }
    }

    // returns how many items were restored
    suspend fun importFrom(uri: Uri): Int {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open the backup file")
        }
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.version <= Backup.CURRENT_VERSION) { "This backup is from a newer version of Atlas" }
        repository.restoreSnapshot(backup)
        return backup.items.size
    }
}
