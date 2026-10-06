package com.forsakenblank.atlas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun itemTypeToString(type: ItemType): String = type.name

    @TypeConverter
    fun stringToItemType(value: String): ItemType = ItemType.valueOf(value)

    @TypeConverter
    fun trackerKindToString(kind: TrackerKind): String = kind.name

    @TypeConverter
    fun stringToTrackerKind(value: String): TrackerKind = TrackerKind.valueOf(value)
}

@Database(
    entities = [Item::class, NoteBody::class, Tracker::class, LogEntry::class, Tag::class, ItemTag::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AtlasDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun notes(): NoteDao
    abstract fun trackers(): TrackerDao
    abstract fun tags(): TagDao

    companion object {
        fun build(context: Context): AtlasDatabase =
            Room.databaseBuilder(context, AtlasDatabase::class.java, "atlas.db")
                .addCallback(StarterContent)
                .build()
    }
}

// first launch gets the three starter trackers from the plan and a welcome note
private object StarterContent : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val item = "INSERT INTO items (id, type, parentId, name, color, sortOrder, pinned, created, updated, archived) " +
            "VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, 0)"
        db.execSQL(item, arrayOf(1, "FOLDER", null, "Inbox", null, 0, now, now))
        db.execSQL(item, arrayOf(2, "FOLDER", null, "Habits", 0xFF4DB6AC.toInt(), 1, now, now))
        db.execSQL(item, arrayOf(3, "NOTE", 1, "Welcome to Atlas", null, 0, now, now))
        db.execSQL(item, arrayOf(4, "TRACKER", 2, "Cold shower", 0xFF64B5F6.toInt(), 0, now, now))
        db.execSQL(item, arrayOf(5, "TRACKER", 2, "Gym", 0xFFE57373.toInt(), 1, now, now))
        db.execSQL(item, arrayOf(6, "TRACKER", 2, "Study", 0xFFBA68C8.toInt(), 2, now, now))

        val tracker = "INSERT INTO trackers (itemId, kind, unit, dailyGoal, showOnHome) VALUES (?, ?, ?, ?, 1)"
        db.execSQL(tracker, arrayOf(4, "COUNTER", null, 1))
        db.execSQL(tracker, arrayOf(5, "YES_NO", null, null))
        db.execSQL(tracker, arrayOf(6, "TIMER", null, 60))

        db.execSQL("INSERT INTO note_bodies (itemId, text) VALUES (?, ?)", arrayOf(3, welcomeText))
        db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'atlas')")
        db.execSQL("INSERT INTO item_tags (itemId, tagId) VALUES (3, 1)")
    }

    private val welcomeText = """
        Everything you make in Atlas lives in the Explorer, so notes, folders and trackers can sit side by side.

        Home has one tap shortcuts for your trackers. Tap Cold shower to log one, tap Gym to mark today done, and tap Study to start a timer (tap again to stop and save it). Hold any shortcut to see its streaks, chart and history.

        Add #tags anywhere in a note to group it, like this one is tagged #atlas.

        Settings (the gear at the top) has the theme and backups.
    """.trimIndent()
}
