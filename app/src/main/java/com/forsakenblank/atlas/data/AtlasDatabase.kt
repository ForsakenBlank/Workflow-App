package com.forsakenblank.atlas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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

    @TypeConverter
    fun aggregateToString(aggregate: Aggregate?): String? = aggregate?.name

    @TypeConverter
    fun stringToAggregate(value: String?): Aggregate? = value?.let { runCatching { Aggregate.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun repeatToString(repeat: Repeat): String = repeat.name

    @TypeConverter
    fun stringToRepeat(value: String): Repeat = runCatching { Repeat.valueOf(value) }.getOrDefault(Repeat.NONE)

    @TypeConverter
    fun countdownKindToString(kind: CountdownKind): String = kind.name

    @TypeConverter
    fun stringToCountdownKind(value: String): CountdownKind = runCatching { CountdownKind.valueOf(value) }.getOrDefault(CountdownKind.OTHER)
}

@Database(
    entities = [
        Item::class, NoteBody::class, Tracker::class, LogEntry::class, Tag::class, ItemTag::class,
        Event::class, Task::class, Subject::class, TimetableSlot::class, Term::class,
        SheetBody::class, Countdown::class, MoneyAccount::class, MoneyEntry::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AtlasDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun notes(): NoteDao
    abstract fun trackers(): TrackerDao
    abstract fun tags(): TagDao
    abstract fun events(): EventDao
    abstract fun tasks(): TaskDao
    abstract fun timetable(): TimetableDao
    abstract fun sheets(): SheetDao
    abstract fun countdowns(): CountdownDao
    abstract fun money(): MoneyDao

    companion object {
        fun build(context: Context): AtlasDatabase =
            Room.databaseBuilder(context, AtlasDatabase::class.java, "atlas.db")
                .addMigrations(*MIGRATIONS)
                .addCallback(StarterContent)
                .build()

        // version 2 adds the calendar, tasks, timetable and number tracker settings
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trackers` ADD COLUMN `aggregate` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, `endsAt` INTEGER NOT NULL, " +
                        "`allDay` INTEGER NOT NULL, `color` INTEGER, `location` TEXT, `notes` TEXT, " +
                        "`repeatRule` TEXT NOT NULL, `repeatUntil` INTEGER, `created` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_startsAt` ON `events` (`startsAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `notes` TEXT, `due` INTEGER, `priority` INTEGER NOT NULL, " +
                        "`done` INTEGER NOT NULL, `doneAt` INTEGER, `repeatRule` TEXT NOT NULL, " +
                        "`subjectId` INTEGER, `created` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_due` ON `tasks` (`due`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_subjectId` ON `tasks` (`subjectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subjects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER, `teacher` TEXT, `room` TEXT, `notes` TEXT, `folderId` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timetable_slots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, " +
                        "`endMinute` INTEGER NOT NULL, `week` INTEGER NOT NULL, `kind` TEXT, `room` TEXT, " +
                        "FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_timetable_slots_subjectId` ON `timetable_slots` (`subjectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `terms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `startDay` INTEGER NOT NULL, `endDay` INTEGER NOT NULL)"
                )
            }
        }

        // version 3 adds sheets, countdowns and reminder settings on events and tasks
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sheets` (`itemId` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`itemId`), " +
                        "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `countdowns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `date` INTEGER NOT NULL, `kind` TEXT NOT NULL, `yearly` INTEGER NOT NULL, " +
                        "`yearKnown` INTEGER NOT NULL, `countUp` INTEGER NOT NULL, `color` INTEGER, `emoji` TEXT, `note` TEXT, " +
                        "`pinned` INTEGER NOT NULL, `showOnCalendar` INTEGER NOT NULL, `remind` INTEGER NOT NULL, `created` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `events` ADD COLUMN `reminderMinutes` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `remind` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // version 4 adds the money accounts and their entries
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `money_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `openingPence` INTEGER NOT NULL, `color` INTEGER, " +
                        "`sortOrder` INTEGER NOT NULL, `created` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `money_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `amountPence` INTEGER NOT NULL, " +
                        "`category` TEXT, `note` TEXT, `transferId` INTEGER, `created` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`accountId`) REFERENCES `money_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_entries_accountId` ON `money_entries` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_entries_day` ON `money_entries` (`day`)")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}

// a fresh install only gets an inbox, the starter pack picker adds everything else
private object StarterContent : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO items (id, type, parentId, name, sortOrder, pinned, created, updated, archived) " +
                "VALUES (1, 'FOLDER', NULL, 'Inbox', 0, 0, ?, ?, 0)",
            arrayOf(now, now),
        )
    }
}
