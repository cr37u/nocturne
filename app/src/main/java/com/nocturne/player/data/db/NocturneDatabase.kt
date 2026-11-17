package com.nocturne.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nocturne.player.data.SearchKey

class Converters {
    @TypeConverter fun fromStatus(s: DownloadStatus): String = s.name
    @TypeConverter fun toStatus(s: String): DownloadStatus = DownloadStatus.valueOf(s)
}

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackCrossRef::class, DownloadEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NocturneDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        /**
         * v1 → v2: adds `userEdited` and `searchKey` to `tracks` and fills `searchKey` for every
         * existing row. Purely additive: library, playlists, downloads and files stay untouched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN searchKey TEXT NOT NULL DEFAULT ''")
                backfillSearchKeys(db)
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun backfillSearchKeys(db: SupportSQLiteDatabase) {
            val rows = ArrayList<Triple<String, String, String>>()
            db.query("SELECT id, title, artist FROM tracks").use { c ->
                while (c.moveToNext()) rows += Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
            rows.forEach { (id, title, artist) ->
                db.execSQL("UPDATE tracks SET searchKey = ? WHERE id = ?", arrayOf(SearchKey.of(title, artist), id))
            }
        }
    }
}
