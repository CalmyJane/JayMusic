package com.calmyjane.jaymusic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class, ExcludedFolderEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
    abstract fun excludedFolders(): ExcludedFolderDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jaymusic.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * v2: rating sync with Audio Station. Ratings given in JayMusic so far are newer than the
         * NAS's, so they are marked to be sent on the first sync.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN ratingDirty INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN serverSongId TEXT")
                db.execSQL("UPDATE tracks SET ratingDirty = 1 WHERE rating > 0")
            }
        }
    }
}
