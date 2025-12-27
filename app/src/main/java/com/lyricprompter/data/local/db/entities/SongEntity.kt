package com.lyricprompter.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing songs in the database.
 * Complex data (lyrics, vocabulary) is stored as JSON strings.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,

    // Musical metadata
    val bpm: Int?,
    val originalKey: String?,
    val performKey: String?,
    val timeSignature: String?,

    // Count-in settings
    val countInEnabled: Boolean,
    val countInBars: Int,

    // Prompt settings
    val triggerPercent: Int,
    val promptWordCount: Int,

    // Lyrics stored as JSON
    val lyricsJson: String,
    val vocabularyJson: String,

    // Organization stored as JSON
    val setlistIdsJson: String,
    val tagsJson: String,

    // Timestamps
    val createdAt: Long,
    val updatedAt: Long
)
