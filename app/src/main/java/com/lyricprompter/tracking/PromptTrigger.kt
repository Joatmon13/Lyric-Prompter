package com.lyricprompter.tracking

import android.util.Log
import com.lyricprompter.domain.model.Song
import javax.inject.Inject

/**
 * Logic for deciding when to trigger a prompt.
 */
class PromptTrigger @Inject constructor() {

    companion object {
        private const val TAG = "LP.Trigger"

        // Default beats for cooldown when not specified per-line
        private const val DEFAULT_COOLDOWN_BEATS = 2

        // Default cooldown when BPM is not available (fallback in ms)
        private const val DEFAULT_COOLDOWN_MS = 1000L  // 1 second

        /**
         * Calculate cooldown duration in ms based on beats and BPM.
         * Formula: cooldown = (beats × 60 / BPM) seconds
         *
         * Examples:
         * - 2 beats at 120 BPM: 2 × (60/120) = 1.0 sec
         * - 4 beats at 120 BPM: 4 × (60/120) = 2.0 sec
         * - 8 beats at 120 BPM: 8 × (60/120) = 4.0 sec
         * - 2 beats at 60 BPM:  2 × (60/60)  = 2.0 sec
         */
        fun calculateCooldownMs(beats: Int, bpm: Int?): Long {
            if (bpm == null || bpm <= 0) {
                return DEFAULT_COOLDOWN_MS
            }
            val cooldownSeconds = (beats * 60.0) / bpm
            return (cooldownSeconds * 1000).toLong()
        }
    }

    // Track when the last prompt was triggered
    private var lastPromptTimeMs: Long = 0L

    // Current cooldown for the active line (in ms)
    private var currentCooldownMs: Long = DEFAULT_COOLDOWN_MS

    // Song's BPM for calculating per-line cooldowns
    private var songBpm: Int? = null

    /**
     * Configure with song's BPM. Called when a song is loaded.
     */
    fun configureSong(bpm: Int?) {
        songBpm = bpm
        currentCooldownMs = calculateCooldownMs(DEFAULT_COOLDOWN_BEATS, bpm)
        Log.i(TAG, "[SONG_CONFIG] bpm=$bpm | defaultCooldown=${currentCooldownMs}ms")
    }

    /**
     * Set the cooldown for the current line based on its beat count.
     * @param beats Number of beats from //N notation, or null to use default
     */
    fun setCooldownForLine(beats: Int?) {
        val actualBeats = beats ?: DEFAULT_COOLDOWN_BEATS
        currentCooldownMs = calculateCooldownMs(actualBeats, songBpm)
        Log.d(TAG, "[LINE_COOLDOWN] beats=$actualBeats | cooldown=${currentCooldownMs}ms")
    }

    /**
     * Determine if a prompt should be triggered.
     *
     * Logic: trigger as soon as the line is confidently matched, gated by a
     * BPM-derived cooldown (no longer waits for Vosk silence detection, which
     * pushed the prompt past the end of the line).
     *
     * Requirements to trigger:
     * 1. Must meet minimum match threshold (triggerPercent from song settings)
     * 2. Must not be in cooldown (BPM/`//N`-derived spacing between prompts)
     *
     * @param lineIndex Current line being matched
     * @param matchScore How well the recognized words match the line (0.0-1.0)
     * @param triggerPercent Minimum match percentage required (from song settings)
     * @param lastPromptedLine The last line that was prompted (-1 if none)
     * @param lineWordCount Number of words in the line (unused but kept for API)
     * @param isFinal True if this is a final (silence) result; informational only
     * @return true if we should speak the prompt for the next line
     */
    fun shouldPrompt(
        lineIndex: Int,
        matchScore: Float,
        triggerPercent: Int,
        lastPromptedLine: Int,
        lineWordCount: Int = 0,
        isFinal: Boolean = false
    ): Boolean {
        // Don't re-prompt the same line or previous lines
        if (lineIndex <= lastPromptedLine) return false

        // Check if match score meets threshold
        val threshold = triggerPercent / 100f
        val meetsThreshold = matchScore >= threshold
        if (!meetsThreshold) {
            if (matchScore > 0f) {
                Log.v(TAG, "[BELOW_THRESHOLD] score=${(matchScore * 100).toInt()}% < ${triggerPercent}%")
            }
            return false
        }

        // Check cooldown - don't prompt too soon after the previous prompt.
        // Cooldown is BPM-derived (see calculateCooldownMs), so spacing scales
        // with tempo and the per-line //N notation. This is the timing gate:
        // once a line's prompt fires, the next can't fire for ~N beats, which
        // gives the performer time to sing the next line before it's prompted.
        val now = System.currentTimeMillis()
        val timeSinceLastPrompt = now - lastPromptTimeMs
        if (lastPromptTimeMs > 0 && timeSinceLastPrompt < currentCooldownMs) {
            Log.v(TAG, "[COOLDOWN] ${timeSinceLastPrompt}ms < ${currentCooldownMs}ms, waiting...")
            return false
        }

        // Fire as soon as the line is confidently matched, WITHOUT waiting for
        // Vosk to declare silence. Waiting for the FINAL result added ~0.5-0.8s
        // of lag and pushed the prompt past the end of the line. triggerPercent
        // controls how early within the line this lands (lower = earlier prompt).
        Log.d(TAG, "[TRIGGER] score=${(matchScore * 100).toInt()}% >= ${triggerPercent}% | isFinal=$isFinal - prompting")
        return true
    }

    /**
     * Called when a prompt is actually triggered.
     * Updates the cooldown timer.
     */
    fun markPromptTriggered() {
        lastPromptTimeMs = System.currentTimeMillis()
        Log.d(TAG, "[COOLDOWN_START] Next prompt blocked for ${currentCooldownMs}ms")
    }

    /**
     * Reset the cooldown timer (e.g., when starting a new song).
     */
    fun resetCooldown() {
        lastPromptTimeMs = 0L
    }

    /**
     * Check if the performer appears to have moved past a line without triggering.
     * This can happen if they skip words or speak too quickly.
     *
     * @param currentLineIndex Best matching line
     * @param lastPromptedLine Last prompted line
     * @return Number of lines skipped (0 if none)
     */
    fun linesSkipped(currentLineIndex: Int, lastPromptedLine: Int): Int {
        val expectedNext = lastPromptedLine + 1
        return if (currentLineIndex > expectedNext) {
            currentLineIndex - expectedNext
        } else {
            0
        }
    }
}
