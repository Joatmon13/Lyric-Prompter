package com.lyricprompter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lyricprompter.data.local.db.entities.SetlistEntity
import com.lyricprompter.data.local.db.entities.SongEntity

@Database(
    entities = [
        SongEntity::class,
        SetlistEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
}
