package com.lyricprompter.diagnostics

import android.util.Log
import com.lyricprompter.audio.routing.AudioRouter
import com.lyricprompter.data.local.db.SessionLogDao
import com.lyricprompter.data.local.db.entities.LogEventEntity
import com.lyricprompter.data.local.db.entities.LogEventType
import com.lyricprompter.data.local.db.entities.SessionLogEntity
import com.lyricprompter.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures all diagnostic events during a performance session to the database.
 * Automatically starts when a session begins and stops when it ends.
 *
 * IMPORTANT: Logging is disabled by default to avoid any performance impact.
 * Enable via Settings -> Developer Tools -> Enable Session Logging before testing.
 */
@Singleton
class DiagnosticLogger @Inject constructor(
    private val sessionLogDao: SessionLogDao,
    private val audioRouter: AudioRouter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentSessionId: String? = null
    private var currentSession: SessionLogEntity? = null
    private var sessionStartTime: Long = 0
    private var promptedCount = 0
    private var skippedCount = 0
    private var recognitionCount = 0
    private var matchScoreSum = 0f
    private var matchScoreCount = 0

    /**
     * Whether diagnostic logging is enabled.
     * Disabled by default to avoid any performance impact during normal use.
     */
    var isEnabled: Boolean = false

    companion object {
        private const val TAG = "LP.Diag"
    }

    /**
     * Start a new logging session.
     * Returns null if logging is disabled.
     */
    fun startSession(
        song: Song,
        usePhoneMic: Boolean,
        bluetoothConnected: Boolean
    ): String? {
        if (!isEnabled) return null

        val sessionId = UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()
        currentSessionId = sessionId

        // Reset counters
        promptedCount = 0
        skippedCount = 0
        recognitionCount = 0
        matchScoreSum = 0f
        matchScoreCount = 0

        val audioMode = when {
            usePhoneMic -> "A2DP"
            bluetoothConnected -> "SCO"
            else -> "SPEAKER"
        }

        val session = SessionLogEntity(
            id = sessionId,
            startTime = sessionStartTime,
            endTime = null,
            durationMs = null,
            songId = song.id,
            songTitle = song.title,
            songArtist = song.artist,
            totalLines = song.lineCount,
            triggerPercent = song.triggerPercent,
            promptWordCount = song.promptWordCount,
            useFullLine = song.promptWordCount == 0,
            usePhoneMic = usePhoneMic,
            countInEnabled = song.countInEnabled,
            countInBars = song.countInBars,
            bpm = song.bpm,
            audioMode = audioMode,
            bluetoothConnected = bluetoothConnected,
            linesPrompted = 0,
            linesSkipped = 0,
            totalRecognitions = 0,
            averageMatchScore = null,
            notes = null
        )

        currentSession = session

        scope.launch {
            sessionLogDao.insertSession(session)
            Log.i(TAG, "[DB] Session started: $sessionId")
        }

        logEvent(
            eventType = LogEventType.SESSION_START,
            extraData = """{"song":"${song.title}","trigger":${song.triggerPercent},"audioMode":"$audioMode"}"""
        )

        return sessionId
    }

    /**
     * End the current session.
     */
    fun endSession() {
        if (!isEnabled) return
        val sessionId = currentSessionId ?: return
        val session = currentSession ?: return

        val endTime = System.currentTimeMillis()
        val duration = endTime - sessionStartTime

        val avgScore = if (matchScoreCount > 0) matchScoreSum / matchScoreCount else null

        logEvent(
            eventType = LogEventType.SESSION_END,
            extraData = """{"duration":$duration,"prompted":$promptedCount,"skipped":$skippedCount}"""
        )

        val updatedSession = session.copy(
            endTime = endTime,
            durationMs = duration,
            linesPrompted = promptedCount,
            linesSkipped = skippedCount,
            totalRecognitions = recognitionCount,
            averageMatchScore = avgScore
        )

        scope.launch {
            sessionLogDao.updateSession(updatedSession)
            Log.i(TAG, "[DB] Session ended: $sessionId, duration=${duration}ms, prompted=$promptedCount")
        }

        currentSessionId = null
        currentSession = null
    }

    /**
     * Log a Vosk recognition result.
     */
    fun logVoskResult(text: String, wordCount: Int, isFinal: Boolean) {
        if (!isEnabled) return
        if (isFinal) {
            recognitionCount++
        }

        logEvent(
            eventType = if (isFinal) LogEventType.VOSK_FINAL else LogEventType.VOSK_PARTIAL,
            recognizedText = text,
            recognizedWords = wordCount
        )
    }

    /**
     * Log a match result.
     */
    fun logMatchResult(
        lineIndex: Int,
        lineText: String,
        matchScore: Float,
        threshold: Int,
        thresholdMet: Boolean
    ) {
        if (!isEnabled) return
        matchScoreSum += matchScore
        matchScoreCount++

        logEvent(
            eventType = if (thresholdMet) LogEventType.THRESHOLD_MET else LogEventType.THRESHOLD_NOT_MET,
            lineIndex = lineIndex,
            lineText = lineText,
            matchScore = matchScore,
            threshold = threshold,
            thresholdMet = thresholdMet
        )
    }

    /**
     * Log when a prompt is fired.
     */
    fun logPromptFired(lineIndex: Int, promptText: String, wordCount: Int) {
        if (!isEnabled) return
        promptedCount++

        logEvent(
            eventType = LogEventType.PROMPT_FIRED,
            lineIndex = lineIndex,
            promptText = promptText,
            promptWordCount = wordCount
        )
    }

    /**
     * Log when a line is skipped.
     */
    fun logLineSkip(lineIndex: Int, reason: String) {
        if (!isEnabled) return
        skippedCount++

        logEvent(
            eventType = LogEventType.LINE_SKIP,
            lineIndex = lineIndex,
            extraData = """{"reason":"$reason"}"""
        )
    }

    /**
     * Log audio configuration.
     */
    fun logAudioConfig(mode: String, scoEnabled: Boolean, btConnected: Boolean) {
        if (!isEnabled) return
        logEvent(
            eventType = LogEventType.AUDIO_CONFIG,
            extraData = """{"mode":"$mode","sco":$scoEnabled,"bt":$btConnected}"""
        )
    }

    /**
     * Log when vocabulary is loaded.
     */
    fun logVocabLoaded(wordCount: Int) {
        if (!isEnabled) return
        logEvent(
            eventType = LogEventType.VOCAB_LOADED,
            extraData = """{"words":$wordCount}"""
        )
    }

    /**
     * Log line advance.
     */
    fun logLineAdvance(fromLine: Int, toLine: Int) {
        if (!isEnabled) return
        logEvent(
            eventType = LogEventType.LINE_ADVANCE,
            lineIndex = toLine,
            extraData = """{"from":$fromLine,"to":$toLine}"""
        )
    }

    /**
     * Log prompt spoken via TTS.
     */
    fun logPromptSpoken(text: String, stream: String, rate: Float) {
        if (!isEnabled) return
        logEvent(
            eventType = LogEventType.PROMPT_SPOKEN,
            promptText = text,
            extraData = """{"stream":"$stream","rate":$rate}"""
        )
    }

    /**
     * Internal method to log an event.
     */
    private fun logEvent(
        eventType: String,
        recognizedText: String? = null,
        recognizedWords: Int? = null,
        lineIndex: Int? = null,
        lineText: String? = null,
        matchScore: Float? = null,
        threshold: Int? = null,
        thresholdMet: Boolean? = null,
        promptText: String? = null,
        promptWordCount: Int? = null,
        extraData: String? = null
    ) {
        val sessionId = currentSessionId ?: return

        val event = LogEventEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            recognizedText = recognizedText,
            recognizedWords = recognizedWords,
            lineIndex = lineIndex,
            lineText = lineText,
            matchScore = matchScore,
            threshold = threshold,
            thresholdMet = thresholdMet,
            promptText = promptText,
            promptWordCount = promptWordCount,
            extraData = extraData
        )

        scope.launch {
            sessionLogDao.insertEvent(event)
        }
    }

    /**
     * Check if a session is currently active.
     */
    fun isSessionActive(): Boolean = currentSessionId != null

    /**
     * Get current session ID.
     */
    fun getCurrentSessionId(): String? = currentSessionId
}
