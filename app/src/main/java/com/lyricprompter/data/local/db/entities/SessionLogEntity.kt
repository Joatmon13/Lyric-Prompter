package com.lyricprompter.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Captures a complete performance session with all settings for analysis.
 */
@Entity(tableName = "session_logs")
data class SessionLogEntity(
    @PrimaryKey
    val id: String,

    // Session timing
    val startTime: Long,
    val endTime: Long?,
    val durationMs: Long?,

    // Song info
    val songId: String,
    val songTitle: String,
    val songArtist: String,
    val totalLines: Int,

    // Settings at time of session
    val triggerPercent: Int,
    val promptWordCount: Int,
    val useFullLine: Boolean,
    val usePhoneMic: Boolean,
    val countInEnabled: Boolean,
    val countInBars: Int,
    val bpm: Int?,

    // Audio configuration
    val audioMode: String,  // "SCO", "A2DP", "SPEAKER"
    val bluetoothConnected: Boolean,

    // Results
    val linesPrompted: Int,
    val linesSkipped: Int,
    val totalRecognitions: Int,
    val averageMatchScore: Float?,

    // User notes (optional)
    val notes: String?
)
