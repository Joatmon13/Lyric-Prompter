package com.lyricprompter.tracking

import android.util.Log
import javax.inject.Inject

/**
 * Logic for deciding when to trigger a prompt.
 */
class PromptTrigger @Inject constructor() {

    companion object {
        private const val TAG = "PromptTrigger"

        // Short lines (4 or fewer words) get a reduced threshold
        private const val SHORT_LINE_THRESHOLD = 4
        private const val SHORT_LINE_REDUCTION = 0.15f  // Reduce by 15%
    }

    /**
     * Determine if a prompt should be triggered.
     *
     * @param lineIndex Current line being matched
     * @param matchScore How well the recognized words match the line (0.0-1.0)
     * @param triggerPercent Threshold percentage to trigger prompt (e.g., 70)
     * @param lastPromptedLine The last line that was prompted (-1 if none)
     * @param lineWordCount Number of words in the line (for short line handling)
     * @return true if we should speak the prompt for the next line
     */
    fun shouldPrompt(
        lineIndex: Int,
        matchScore: Float,
        triggerPercent: Int,
        lastPromptedLine: Int,
        lineWordCount: Int = 0
    ): Boolean {
        // Don't re-prompt the same line or previous lines
        if (lineIndex <= lastPromptedLine) return false

        // Calculate threshold - reduce for short lines
        var threshold = triggerPercent / 100f
        if (lineWordCount in 1..SHORT_LINE_THRESHOLD) {
            threshold = (threshold - SHORT_LINE_REDUCTION).coerceAtLeast(0.25f)
            Log.d(TAG, "Short line ($lineWordCount words): reduced threshold to ${(threshold * 100).toInt()}%")
        }

        return matchScore >= threshold
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
