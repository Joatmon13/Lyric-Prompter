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

        // Short lines (4 or fewer words) get a reduced threshold
        private const val SHORT_LINE_THRESHOLD = 4
        private const val SHORT_LINE_REDUCTION = 0.15f  // Reduce by 15%

        // Default cooldown when BPM is not available (fallback)
        // Short since silence detection handles the main timing
        private const val DEFAULT_COOLDOWN_MS = 1000L  // 1 second

        // Minimum/maximum cooldown bounds (safety limits)
        // With silence detection, we only need a short cooldown to prevent
        // rapid-fire prompts from brief pauses mid-line
        private const val MIN_COOLDOWN_MS = 500L    // 0.5 seconds minimum
        private const val MAX_COOLDOWN_MS = 2000L   // 2 seconds maximum

        // Bars per line for cooldown calculation
        // Using 0.5 bars - just a brief debounce since silence detection handles timing
        private const val BARS_PER_LINE = 0.5f

        /**
         * Calculate cooldown duration based on BPM and time signature.
         *
         * With silence detection, this is just a debounce to prevent rapid-fire
         * prompts from brief pauses. We use 0.5 bars.
         * Formula: cooldown = (barsPerLine × beatsPerBar × 60 / BPM) seconds
         *
         * Examples (4/4 time, 0.5 bars):
         * - 60 BPM:  0.5 bar × 4 beats × (60/60)  = 2.0 sec (capped at max)
         * - 90 BPM:  0.5 bar × 4 beats × (60/90)  = 1.3 sec
         * - 120 BPM: 0.5 bar × 4 beats × (60/120) = 1.0 sec
         * - 150 BPM: 0.5 bar × 4 beats × (60/150) = 0.8 sec
         * - 180 BPM: 0.5 bar × 4 beats × (60/180) = 0.67 sec
         */
        fun calculateCooldownMs(bpm: Int?, timeSignature: String?): Long {
            if (bpm == null || bpm <= 0) {
                return DEFAULT_COOLDOWN_MS
            }

            val beatsPerBar = Song.beatsPerBar(timeSignature)
            val totalBeats = BARS_PER_LINE * beatsPerBar
            val cooldownSeconds = (totalBeats * 60.0) / bpm
            val cooldownMs = (cooldownSeconds * 1000).toLong()

            return cooldownMs.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
        }
    }

    // Track when the last prompt was triggered
    private var lastPromptTimeMs: Long = 0L

    // Current cooldown duration (set when song is loaded)
    private var cooldownMs: Long = DEFAULT_COOLDOWN_MS

    /**
     * Configure cooldown based on song's BPM and time signature.
     * Call this when a song is loaded.
     */
    fun configureCooldown(bpm: Int?, timeSignature: String?) {
        cooldownMs = calculateCooldownMs(bpm, timeSignature)
        Log.i(TAG, "[COOLDOWN_CONFIG] bpm=$bpm | timeSig=$timeSignature | cooldown=${cooldownMs}ms")
    }

    /**
     * Determine if a prompt should be triggered.
     *
     * NEW LOGIC: Wait for silence (isFinal=true) before triggering.
     * This ensures we don't interrupt while the performer is still singing.
     *
     * Requirements to trigger:
     * 1. Must have matched at least one word (score > 0)
     * 2. Must be a FINAL result (silence detected - user stopped singing)
     * 3. Must not be in cooldown (prevents rapid-fire prompts)
     *
     * @param lineIndex Current line being matched
     * @param matchScore How well the recognized words match the line (0.0-1.0)
     * @param triggerPercent IGNORED - kept for API compatibility but no longer used
     * @param lastPromptedLine The last line that was prompted (-1 if none)
     * @param lineWordCount IGNORED - kept for API compatibility but no longer used
     * @param isFinal True if this is a final result (silence detected)
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

        // Must have matched at least one word
        val hasAnyMatch = matchScore > 0f
        if (!hasAnyMatch) return false

        // KEY CHANGE: Only trigger on FINAL results (silence detected)
        // This waits for the performer to finish singing before prompting
        if (!isFinal) {
            Log.v(TAG, "[WAITING_FOR_SILENCE] score=${(matchScore * 100).toInt()}% - still speaking...")
            return false
        }

        // Check cooldown - don't prompt too soon after previous prompt
        val now = System.currentTimeMillis()
        val timeSinceLastPrompt = now - lastPromptTimeMs
        if (lastPromptTimeMs > 0 && timeSinceLastPrompt < cooldownMs) {
            Log.v(TAG, "[COOLDOWN] ${timeSinceLastPrompt}ms < ${cooldownMs}ms, waiting...")
            return false
        }

        Log.d(TAG, "[SILENCE_DETECTED] score=${(matchScore * 100).toInt()}% - triggering prompt")
        return true
    }

    /**
     * Called when a prompt is actually triggered.
     * Updates the cooldown timer.
     */
    fun markPromptTriggered() {
        lastPromptTimeMs = System.currentTimeMillis()
        Log.d(TAG, "[COOLDOWN_START] Next prompt blocked for ${cooldownMs}ms")
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
