package com.forsakenblank.atlas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class ItemType { FOLDER, NOTE, TRACKER }

enum class TrackerKind { COUNTER, YES_NO, TIMER }

// everything in the explorer is an item, the type decides which extra table holds its data
@Serializable
@Entity(tableName = "items", indices = [Index("parentId"), Index("type")])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val parentId: Long? = null, // null means the item sits at the root
    val name: String,
    val color: Int? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val pinned: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val deletedAt: Long? = null, // set when the item is in the trash
)

@Serializable
@Entity(
    tableName = "note_bodies",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
)
data class NoteBody(
    @PrimaryKey val itemId: Long,
    val text: String = "",
)

@Serializable
@Entity(
    tableName = "trackers",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
)
data class Tracker(
    @PrimaryKey val itemId: Long,
    val kind: TrackerKind,
    val unit: String? = null,
    val dailyGoal: Int? = null, // taps for counters, minutes for timers
    val showOnHome: Boolean = true,
    val runningSince: Long? = null, // timer start, kept in the db so it survives the app closing
)

@Serializable
@Entity(
    tableName = "log_entries",
    indices = [Index("trackerId"), Index("timestamp")],
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["trackerId"], onDelete = ForeignKey.CASCADE)],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val timestamp: Long,
    val value: Double = 1.0,
    val durationSeconds: Long? = null,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int? = null,
)

@Serializable
@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ItemTag(
    val itemId: Long,
    val tagId: Long,
)
