package com.forsakenblank.atlas.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class NoteRow(
    @Embedded val item: Item,
    val text: String?,
)

data class TrackerWithItem(
    @Embedded val item: Item,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val tracker: Tracker,
)

@Dao
interface ItemDao {
    @Query(
        """
        SELECT * FROM items
        WHERE parentId IS :parentId AND deletedAt IS NULL AND archived = 0
        ORDER BY type = 'FOLDER' DESC, sortOrder, name COLLATE NOCASE
        """
    )
    fun children(parentId: Long?): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE parentId = :parentId")
    suspend fun childrenOnce(parentId: Long): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE type = 'FOLDER' AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun folders(): Flow<List<Item>>

    // only the top of each deleted subtree, so a trashed folder shows once
    @Query(
        """
        SELECT * FROM items AS i WHERE i.deletedAt IS NOT NULL
        AND (i.parentId IS NULL OR NOT EXISTS (
            SELECT 1 FROM items AS p WHERE p.id = i.parentId AND p.deletedAt = i.deletedAt
        ))
        ORDER BY i.deletedAt DESC
        """
    )
    fun trash(): Flow<List<Item>>

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("UPDATE items SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<Long>, at: Long?)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteForever(ids: List<Long>)

    @Query("DELETE FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrash(before: Long)

    @Query("SELECT * FROM items")
    suspend fun all(): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Query("DELETE FROM items")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query(
        """
        SELECT items.*, note_bodies.text AS text FROM items
        LEFT JOIN note_bodies ON note_bodies.itemId = items.id
        WHERE items.type = 'NOTE' AND items.deletedAt IS NULL AND items.archived = 0
        ORDER BY items.pinned DESC, items.updated DESC
        """
    )
    fun notes(): Flow<List<NoteRow>>

    @Query(
        """
        SELECT items.*, note_bodies.text AS text FROM items
        INNER JOIN item_tags ON item_tags.itemId = items.id
        LEFT JOIN note_bodies ON note_bodies.itemId = items.id
        WHERE item_tags.tagId = :tagId AND items.type = 'NOTE'
        AND items.deletedAt IS NULL AND items.archived = 0
        ORDER BY items.pinned DESC, items.updated DESC
        """
    )
    fun notesWithTag(tagId: Long): Flow<List<NoteRow>>

    @Query("SELECT * FROM note_bodies WHERE itemId = :itemId")
    suspend fun body(itemId: Long): NoteBody?

    @Upsert
    suspend fun upsert(body: NoteBody)

    @Query("SELECT * FROM note_bodies")
    suspend fun all(): List<NoteBody>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bodies: List<NoteBody>)
}

@Dao
interface TrackerDao {
    @Transaction
    @Query(
        """
        SELECT * FROM items WHERE type = 'TRACKER' AND deletedAt IS NULL AND archived = 0
        ORDER BY sortOrder, name COLLATE NOCASE
        """
    )
    fun trackers(): Flow<List<TrackerWithItem>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeTracker(id: Long): Flow<TrackerWithItem?>

    @Query("SELECT * FROM trackers WHERE itemId = :id")
    suspend fun get(id: Long): Tracker?

    @Insert
    suspend fun insert(tracker: Tracker)

    @Update
    suspend fun update(tracker: Tracker)

    @Insert
    suspend fun insertLog(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId ORDER BY timestamp DESC")
    fun logs(trackerId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :from ORDER BY timestamp")
    fun logsSince(from: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId AND timestamp >= :from ORDER BY timestamp DESC")
    suspend fun logsSinceOnce(trackerId: Long, from: Long): List<LogEntry>

    @Query("SELECT * FROM trackers")
    suspend fun all(): List<Tracker>

    @Query("SELECT * FROM log_entries")
    suspend fun allLogs(): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trackers: List<Tracker>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLogs(logs: List<LogEntry>)
}

@Dao
interface TagDao {
    // tags that are only used by trashed items stay hidden
    @Query(
        """
        SELECT DISTINCT tags.* FROM tags
        INNER JOIN item_tags ON item_tags.tagId = tags.id
        INNER JOIN items ON items.id = item_tags.itemId
        WHERE items.deletedAt IS NULL
        ORDER BY tags.name COLLATE NOCASE
        """
    )
    fun usedTags(): Flow<List<Tag>>

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun idFor(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("DELETE FROM item_tags WHERE itemId = :itemId")
    suspend fun clearFor(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(links: List<ItemTag>)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM item_tags)")
    suspend fun dropUnused()

    @Query("SELECT * FROM tags")
    suspend fun all(): List<Tag>

    @Query("SELECT * FROM item_tags")
    suspend fun allLinks(): List<ItemTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<Tag>)
}
