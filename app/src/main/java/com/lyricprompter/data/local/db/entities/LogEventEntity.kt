package com.lyricprompter.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Individual event within a session - recognition results, prompts, matches, etc.
 */
@Entity(
    tableName = "log_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class LogEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,
    val timestamp: Long,
    val eventType: String,  // VOSK_PARTIAL, VOSK_FINAL, MATCH_RESULT, PROMPT_FIRED, LINE_SKIP, etc.

    // Recognition data
    val recognizedText: String?,
    val recognizedWords: Int?,

    // Match data
    val lineIndex: Int?,
    val lineText: String?,
    val matchScore: Float?,
    val threshold: Int?,
    val thresholdMet: Boolean?,

    // Prompt data
    val promptText: String?,
    val promptWordCount: Int?,

    // Additional context (JSON for flexibility)
    val extraData: String?
)

/**
 * Event types for log events.
 */
object LogEventType {
    const val SESSION_START = "SESSION_START"
    const val SESSION_END = "SESSION_END"
    const val VOCAB_LOADED = "VOCAB_LOADED"
    const val AUDIO_CONFIG = "AUDIO_CONFIG"
    const val VOSK_PARTIAL = "VOSK_PARTIAL"
    const val VOSK_FINAL = "VOSK_FINAL"
    const val MATCH_SEARCH = "MATCH_SEARCH"
    const val MATCH_RESULT = "MATCH_RESULT"
    const val THRESHOLD_MET = "THRESHOLD_MET"
    const val THRESHOLD_NOT_MET = "THRESHOLD_NOT_MET"
    const val LINE_ADVANCE = "LINE_ADVANCE"
    const val PROMPT_FIRED = "PROMPT_FIRED"
    const val PROMPT_SPOKEN = "PROMPT_SPOKEN"
    const val LINE_SKIP = "LINE_SKIP"
}
