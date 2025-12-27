package com.lyricprompter.tracking

import android.util.Log
import com.lyricprompter.domain.model.Song
import javax.inject.Inject

/**
 * Tracks the performer's position in the song based on recognized words.
 * Coordinates fuzzy matching and prompt triggering.
 */
class PositionTracker @Inject constructor(
    private val fuzzyMatcher: FuzzyMatcher,
    private val promptTrigger: PromptTrigger
) {
    private var song: Song? = null
    private var lineWordsList: List<List<String>> = emptyList()
    private var currentLineIndex = 0
    private var lastPromptedLine = -1
    private val recognizedBuffer = mutableListOf<String>()

    companion object {
        private const val TAG = "PositionTracker"

        // How many lines ahead/behind to search
        private const val SEARCH_WINDOW_BEFORE = 2
        private const val SEARCH_WINDOW_AFTER = 5

        // Max words to keep in buffer
        private const val MAX_BUFFER_SIZE = 30
    }

    /**
     * Load a song for tracking.
     */
    fun loadSong(song: Song) {
        this.song = song
        this.lineWordsList = song.lines.map { it.words }
        reset()
    }

    /**
     * Reset tracking state to beginning of song.
     */
    fun reset() {
        currentLineIndex = 0
        lastPromptedLine = -1
        recognizedBuffer.clear()
    }

    /**
     * Process newly recognized words and check for prompt events.
     *
     * @param newWords Words just recognized (may be partial or final result)
     * @return A PromptEvent if action is needed, null otherwise
     */
    fun onWordsRecognized(newWords: List<String>): PromptEvent? {
        val currentSong = song ?: return null
        if (newWords.isEmpty()) return null
        if (lineWordsList.isEmpty()) return null

        // Add new words to buffer (normalized to lowercase)
        val normalizedWords = newWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        recognizedBuffer.addAll(normalizedWords)

        // Keep buffer bounded
        while (recognizedBuffer.size > MAX_BUFFER_SIZE) {
            recognizedBuffer.removeAt(0)
        }

        // Define search window around current position
        val searchWindow = (currentLineIndex - SEARCH_WINDOW_BEFORE)..
            (currentLineIndex + SEARCH_WINDOW_AFTER)

        // Find best matching line
        val match = fuzzyMatcher.findBestMatch(
            recognizedWords = recognizedBuffer,
            lineWordsList = lineWordsList,
            searchWindow = searchWindow
        )

        if (match == null) {
            Log.v(TAG, "No match found in window $searchWindow, buffer: ${recognizedBuffer.takeLast(5)}")
            return null
        }

        val (matchedLineIndex, matchScore) = match
        val lineText = currentSong.lines.getOrNull(matchedLineIndex)?.text?.take(30) ?: ""
        Log.d(TAG, "Match: line $matchedLineIndex (${(matchScore * 100).toInt()}%) '$lineText...' trigger=${currentSong.triggerPercent}% lastPrompted=$lastPromptedLine")

        // Update current position if we've moved forward
        if (matchedLineIndex > currentLineIndex) {
            Log.d(TAG, "Advanced from line $currentLineIndex to $matchedLineIndex")
            currentLineIndex = matchedLineIndex
        }

        // Check if we should trigger a prompt
        if (promptTrigger.shouldPrompt(
                lineIndex = matchedLineIndex,
                matchScore = matchScore,
                triggerPercent = currentSong.triggerPercent,
                lastPromptedLine = lastPromptedLine
            )
        ) {
            lastPromptedLine = matchedLineIndex

            // IMPORTANT: Clear buffer and advance to next line after prompting
            // This prevents re-matching the same line with old words
            recognizedBuffer.clear()
            currentLineIndex = matchedLineIndex + 1
            Log.d(TAG, "After prompt: cleared buffer, advanced to line $currentLineIndex")

            // Get the prompt text (which is for the NEXT line)
            val promptText = currentSong.lines.getOrNull(matchedLineIndex)?.promptText

            return if (promptText.isNullOrEmpty()) {
                // No more lines to prompt - song is finishing
                if (matchedLineIndex >= lineWordsList.lastIndex) {
                    Log.i(TAG, "Song finished at line $matchedLineIndex")
                    PromptEvent.SongFinished
                } else {
                    Log.i(TAG, "Line $matchedLineIndex completed (no prompt text)")
                    PromptEvent.LineCompleted(matchedLineIndex)
                }
            } else {
                Log.i(TAG, "PROMPT line $matchedLineIndex: '$promptText'")
                PromptEvent.SpeakPrompt(
                    lineIndex = matchedLineIndex,
                    promptText = promptText
                )
            }
        } else {
            Log.v(TAG, "Not prompting: score ${(matchScore * 100).toInt()}% < ${currentSong.triggerPercent}% OR already prompted (last=$lastPromptedLine)")
        }

        return null
    }

    /**
     * Get current tracking state for display.
     */
    fun getState(): TrackingState {
        val currentSong = song
        return TrackingState(
            currentLineIndex = currentLineIndex,
            totalLines = currentSong?.lineCount ?: 0,
            lastPromptedLine = lastPromptedLine,
            bufferSize = recognizedBuffer.size,
            currentLineText = currentSong?.lines?.getOrNull(currentLineIndex)?.text
        )
    }

    /**
     * Manually set position (e.g., user taps a line to resync).
     */
    fun jumpToLine(lineIndex: Int) {
        val maxIndex = lineWordsList.lastIndex
        currentLineIndex = lineIndex.coerceIn(0, maxIndex)
        // Don't reset lastPromptedLine - prevent re-prompting lines already done
        recognizedBuffer.clear()
    }
}

/**
 * Events emitted by the position tracker.
 */
sealed interface PromptEvent {
    /**
     * Speak the prompt for the next line.
     */
    data class SpeakPrompt(
        val lineIndex: Int,
        val promptText: String
    ) : PromptEvent

    /**
     * A line was completed (used when there's no prompt text).
     */
    data class LineCompleted(
        val lineIndex: Int
    ) : PromptEvent

    /**
     * The song has finished.
     */
    data object SongFinished : PromptEvent
}

/**
 * Current tracking state for UI display.
 */
data class TrackingState(
    val currentLineIndex: Int,
    val totalLines: Int,
    val lastPromptedLine: Int,
    val bufferSize: Int,
    val currentLineText: String?
)
