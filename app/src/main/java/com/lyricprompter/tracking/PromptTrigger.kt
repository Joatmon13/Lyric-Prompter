package com.lyricprompter.tracking

import javax.inject.Inject

/**
 * Logic for deciding when to trigger a prompt.
 */
class PromptTrigger @Inject constructor() {

    /**
     * Determine if a prompt should be triggered.
     *
     * @param lineIndex Current line being matched
     * @param matchScore How well the recognized words match the line (0.0-1.0)
     * @param triggerPercent Threshold percentage to trigger prompt (e.g., 70)
     * @param lastPromptedLine The last line that was prompted (-1 if none)
     * @return true if we should speak the prompt for the next line
     */
    fun shouldPrompt(
        lineIndex: Int,
        matchScore: Float,
        triggerPercent: Int,
        lastPromptedLine: Int
    ): Boolean {
        // Don't re-prompt the same line or previous lines
        if (lineIndex <= lastPromptedLine) return false

        // Check if we've hit the trigger threshold
        val threshold = triggerPercent / 100f
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
