package com.forsakenblank.atlas.data

import androidx.room.withTransaction
import com.forsakenblank.atlas.util.startOfDay
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class AtlasRepository(private val db: AtlasDatabase) {

    private val items = db.items()
    private val notes = db.notes()
    private val trackers = db.trackers()
    private val tags = db.tags()

    // explorer

    fun children(parentId: Long?): Flow<List<Item>> = items.children(parentId)

    fun folders(): Flow<List<Item>> = items.folders()

    fun observeItem(id: Long): Flow<Item?> = items.observe(id)

    suspend fun item(id: Long): Item? = items.get(id)

    suspend fun createFolder(name: String, parentId: Long?, color: Int? = null): Long =
        items.insert(Item(type = ItemType.FOLDER, parentId = parentId, name = name, color = color))

    suspend fun rename(item: Item, name: String) {
        items.update(item.copy(name = name, updated = System.currentTimeMillis()))
    }

    suspend fun setColor(item: Item, color: Int?) {
        items.update(item.copy(color = color, updated = System.currentTimeMillis()))
    }

    suspend fun setPinned(item: Item, pinned: Boolean) {
        items.update(item.copy(pinned = pinned))
    }

    suspend fun move(item: Item, newParentId: Long?) {
        // stop a folder being moved inside itself
        if (newParentId != null && (newParentId == item.id || newParentId in descendantsOf(item.id))) return
        items.update(item.copy(parentId = newParentId, updated = System.currentTimeMillis()))
    }

    // walks up from a folder to the root so the explorer can show breadcrumbs
    suspend fun pathTo(folderId: Long?): List<Item> {
        val path = mutableListOf<Item>()
        var current = folderId?.let { items.get(it) }
        while (current != null) {
            path.add(0, current)
            current = current.parentId?.let { items.get(it) }
        }
        return path
    }

    private suspend fun descendantsOf(id: Long): List<Long> {
        val found = mutableListOf<Long>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            for (child in items.childrenOnce(queue.removeFirst())) {
                found += child.id
                queue += child.id
            }
        }
        return found
    }

    // trash

    fun trash(): Flow<List<Item>> = items.trash()

    suspend fun moveToTrash(item: Item) {
        items.setDeleted(listOf(item.id) + descendantsOf(item.id), System.currentTimeMillis())
    }

    suspend fun restore(item: Item) {
        val deletedAt = item.deletedAt ?: return
        db.withTransaction {
            val ids = (listOf(item.id) + descendantsOf(item.id)).filter { items.get(it)?.deletedAt == deletedAt }
            items.setDeleted(ids, null)
            // if the old parent is still in the trash, bring the item back to the root
            val parent = item.parentId?.let { items.get(it) }
            if (item.parentId != null && (parent == null || parent.deletedAt != null)) {
                items.get(item.id)?.let { items.update(it.copy(parentId = null)) }
            }
        }
    }

    suspend fun deleteForever(item: Item) {
        items.deleteForever(listOf(item.id) + descendantsOf(item.id))
        tags.dropUnused()
    }

    suspend fun purgeOldTrash(days: Int = 30) {
        items.purgeTrash(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
        tags.dropUnused()
    }

    // notes

    fun notes(): Flow<List<NoteRow>> = notes.notes()

    fun notesWithTag(tagId: Long): Flow<List<NoteRow>> = notes.notesWithTag(tagId)

    fun usedTags(): Flow<List<Tag>> = tags.usedTags()

    suspend fun createNote(parentId: Long?, title: String = ""): Long = db.withTransaction {
        val id = items.insert(Item(type = ItemType.NOTE, parentId = parentId, name = title))
        notes.upsert(NoteBody(id, ""))
        id
    }

    suspend fun noteText(id: Long): String = notes.body(id)?.text.orEmpty()

    suspend fun saveNote(id: Long, title: String, text: String) {
        db.withTransaction {
            val item = items.get(id) ?: return@withTransaction
            items.update(item.copy(name = title, updated = System.currentTimeMillis()))
            notes.upsert(NoteBody(id, text))
            setTags(id, findTags(text))
        }
    }

    private suspend fun setTags(itemId: Long, names: Set<String>) {
        tags.clearFor(itemId)
        val links = names.map { name ->
            val tagId = tags.idFor(name) ?: tags.insert(Tag(name = name))
            ItemTag(itemId, tagId)
        }
        if (links.isNotEmpty()) tags.link(links)
        tags.dropUnused()
    }

    // trackers

    fun trackers(): Flow<List<TrackerWithItem>> = trackers.trackers()

    fun observeTracker(id: Long): Flow<TrackerWithItem?> = trackers.observeTracker(id)

    fun logs(trackerId: Long): Flow<List<LogEntry>> = trackers.logs(trackerId)

    fun logsSince(from: Long): Flow<List<LogEntry>> = trackers.logsSince(from)

    suspend fun createTracker(
        name: String,
        kind: TrackerKind,
        parentId: Long?,
        color: Int?,
        dailyGoal: Int?,
        unit: String?,
        showOnHome: Boolean,
    ): Long = db.withTransaction {
        val id = items.insert(Item(type = ItemType.TRACKER, parentId = parentId, name = name, color = color))
        trackers.insert(Tracker(id, kind, unit, dailyGoal, showOnHome))
        id
    }

    suspend fun updateTracker(tracker: Tracker) = trackers.update(tracker)

    // one tap from home or the track tab, returns the new log id so it can be undone
    suspend fun tap(trackerId: Long): Long? {
        val tracker = trackers.get(trackerId) ?: return null
        val now = System.currentTimeMillis()
        return when (tracker.kind) {
            TrackerKind.COUNTER -> trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = now))
            TrackerKind.YES_NO -> {
                val today = trackers.logsSinceOnce(trackerId, startOfDay(now))
                if (today.isEmpty()) {
                    trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = now))
                } else {
                    today.forEach { trackers.deleteLog(it.id) }
                    null
                }
            }
            TrackerKind.TIMER -> {
                val since = tracker.runningSince
                if (since == null) {
                    trackers.update(tracker.copy(runningSince = now))
                    null
                } else {
                    trackers.update(tracker.copy(runningSince = null))
                    val seconds = (now - since) / 1000
                    if (seconds < 1) null
                    else trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = since, value = seconds.toDouble(), durationSeconds = seconds))
                }
            }
        }
    }

    suspend fun cancelTimer(trackerId: Long) {
        trackers.get(trackerId)?.let { trackers.update(it.copy(runningSince = null)) }
    }

    suspend fun deleteLog(id: Long) = trackers.deleteLog(id)

    // backup

    suspend fun snapshot(): Backup = Backup(
        version = Backup.CURRENT_VERSION,
        exportedAt = System.currentTimeMillis(),
        items = items.all(),
        notes = notes.all(),
        trackers = trackers.all(),
        logs = trackers.allLogs(),
        tags = tags.all(),
        itemTags = tags.allLinks(),
    )

    suspend fun restoreSnapshot(backup: Backup) {
        db.withTransaction {
            // items cascade to notes, trackers, logs and tag links
            items.clear()
            tags.dropUnused()
            items.insertAll(backup.items)
            notes.insertAll(backup.notes)
            trackers.insertAll(backup.trackers)
            trackers.insertAllLogs(backup.logs)
            tags.insertAll(backup.tags)
            tags.link(backup.itemTags)
        }
    }

    companion object {
        private val tagPattern = Regex("""(?<![\w#])#([\p{L}\p{N}_-]+)""")

        fun findTags(text: String): Set<String> =
            tagPattern.findAll(text).map { it.groupValues[1].lowercase() }.toSet()
    }
}
