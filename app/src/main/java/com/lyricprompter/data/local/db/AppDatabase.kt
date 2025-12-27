package com.lyricprompter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lyricprompter.data.local.db.entities.LogEventEntity
import com.lyricprompter.data.local.db.entities.SessionLogEntity
import com.lyricprompter.data.local.db.entities.SetlistEntity
import com.lyricprompter.data.local.db.entities.SongEntity

@Database(
    entities = [
        SongEntity::class,
        SetlistEntity::class,
        SessionLogEntity::class,
        LogEventEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        /**
         * Migration from version 1 to 2:
         * Rename countInBeats column to countInBars
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support column rename directly in older versions,
                // so we need to recreate the table
                db.execSQL("""
                    CREATE TABLE songs_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        bpm INTEGER,
                        originalKey TEXT,
                        performKey TEXT,
                        timeSignature TEXT,
                        countInEnabled INTEGER NOT NULL,
                        countInBars INTEGER NOT NULL,
                        triggerPercent INTEGER NOT NULL,
                        promptWordCount INTEGER NOT NULL,
                        lyricsJson TEXT NOT NULL,
                        vocabularyJson TEXT NOT NULL,
                        setlistIdsJson TEXT NOT NULL,
                        tagsJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Copy data from old table, converting countInBeats to countInBars
                // For existing songs, we divide by beatsPerBar to get bars (default to 1 bar if small)
                db.execSQL("""
                    INSERT INTO songs_new (id, title, artist, bpm, originalKey, performKey,
                        timeSignature, countInEnabled, countInBars, triggerPercent, promptWordCount,
                        lyricsJson, vocabularyJson, setlistIdsJson, tagsJson, createdAt, updatedAt)
                    SELECT id, title, artist, bpm, originalKey, performKey,
                        timeSignature, countInEnabled,
                        CASE
                            WHEN countInBeats >= 4 THEN 3
                            ELSE 1
                        END,
                        triggerPercent, promptWordCount,
                        lyricsJson, vocabularyJson, setlistIdsJson, tagsJson, createdAt, updatedAt
                    FROM songs
                """.trimIndent())

                // Drop old table
                db.execSQL("DROP TABLE songs")

                // Rename new table
                db.execSQL("ALTER TABLE songs_new RENAME TO songs")
            }
        }

        /**
         * Migration from version 2 to 3:
         * Add session_logs and log_events tables for diagnostic logging
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create session_logs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS session_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        durationMs INTEGER,
                        songId TEXT NOT NULL,
                        songTitle TEXT NOT NULL,
                        songArtist TEXT NOT NULL,
                        totalLines INTEGER NOT NULL,
                        triggerPercent INTEGER NOT NULL,
                        promptWordCount INTEGER NOT NULL,
                        useFullLine INTEGER NOT NULL,
                        usePhoneMic INTEGER NOT NULL,
                        countInEnabled INTEGER NOT NULL,
                        countInBars INTEGER NOT NULL,
                        bpm INTEGER,
                        audioMode TEXT NOT NULL,
                        bluetoothConnected INTEGER NOT NULL,
                        linesPrompted INTEGER NOT NULL,
                        linesSkipped INTEGER NOT NULL,
                        totalRecognitions INTEGER NOT NULL,
                        averageMatchScore REAL,
                        notes TEXT
                    )
                """.trimIndent())

                // Create log_events table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS log_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        recognizedText TEXT,
                        recognizedWords INTEGER,
                        lineIndex INTEGER,
                        lineText TEXT,
                        matchScore REAL,
                        threshold INTEGER,
                        thresholdMet INTEGER,
                        promptText TEXT,
                        promptWordCount INTEGER,
                        extraData TEXT,
                        FOREIGN KEY(sessionId) REFERENCES session_logs(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create index on sessionId for faster queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_log_events_sessionId ON log_events(sessionId)")
            }
        }
    }
}
