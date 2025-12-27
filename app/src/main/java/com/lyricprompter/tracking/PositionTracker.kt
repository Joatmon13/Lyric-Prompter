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

    // Callback to update Vosk grammar when we need to focus on specific lines
    var onGrammarUpdateNeeded: ((Set<String>) -> Unit)? = null

    companion object {
        private const val TAG = "PositionTracker"

        // How many lines ahead/behind to search - very tight to prevent jumping
        private const val SEARCH_WINDOW_BEFORE = 0  // Never go back
        private const val SEARCH_WINDOW_AFTER = 1   // Only look at next line

        // Max words to keep in buffer
        private const val MAX_BUFFER_SIZE = 15

        // Words to keep after prompting (reduced to prevent stale matches)
        private const val KEEP_AFTER_PROMPT = 3

        // How many lines to include in focused grammar (current + next N)
        private const val GRAMMAR_LINES_AHEAD = 3
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
        // NOTE: We use full song vocabulary since Vosk can't update grammar during recognition
    }

    /**
     * Get words for the current and next few lines to focus Vosk grammar.
     */
    private fun getWordsForCurrentPosition(): Set<String> {
        val startLine = maxOf(0, currentLineIndex)
        val endLine = minOf(lineWordsList.size - 1, currentLineIndex + GRAMMAR_LINES_AHEAD)

        val words = mutableSetOf<String>()
        for (i in startLine..endLine) {
            words.addAll(lineWordsList[i])
        }
        Log.d(TAG, "Focused grammar: lines $startLine-$endLine, ${words.size} unique words")
        return words
    }

    /**
     * Update the Vosk grammar to focus on current position.
     */
    private fun updateFocusedGrammar() {
        if (lineWordsList.isEmpty()) return
        val words = getWordsForCurrentPosition()
        onGrammarUpdateNeeded?.invoke(words)
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

        // Define search window: start from line after lastPrompted (never go back)
        // Only look at lines we haven't prompted yet
        val windowStart = maxOf(currentLineIndex - SEARCH_WINDOW_BEFORE, lastPromptedLine + 1)
        val searchWindow = windowStart..(currentLineIndex + SEARCH_WINDOW_AFTER)

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

        // Check if we should trigger a prompt based on percentage threshold
        val lineWordCount = lineWordsList.getOrNull(matchedLineIndex)?.size ?: 0
        if (promptTrigger.shouldPrompt(
                lineIndex = matchedLineIndex,
                matchScore = matchScore,
                triggerPercent = currentSong.triggerPercent,
                lastPromptedLine = lastPromptedLine,
                lineWordCount = lineWordCount
            )
        ) {
            return triggerPrompt(matchedLineIndex, currentSong)
        } else {
            Log.v(TAG, "Not prompting: score ${(matchScore * 100).toInt()}% < ${currentSong.triggerPercent}% OR already prompted (last=$lastPromptedLine)")
        }

        return null
    }

    /**
     * Helper to trigger a prompt and update state.
     */
    private fun triggerPrompt(lineIndex: Int, currentSong: Song): PromptEvent? {
        lastPromptedLine = lineIndex

        // Keep last few words for context, don't clear everything
        val wordsToKeep = recognizedBuffer.takeLast(KEEP_AFTER_PROMPT)
        recognizedBuffer.clear()
        recognizedBuffer.addAll(wordsToKeep)

        // Advance position to next line
        currentLineIndex = lineIndex + 1
        Log.d(TAG, "After prompt: kept ${wordsToKeep.size} words, advanced to line $currentLineIndex")

        // NOTE: We can't update Vosk grammar while recognition is active (causes native crash)
        // Grammar is set at song start and remains for the whole song

        // Get the prompt text (which is for the NEXT line)
        val promptText = currentSong.lines.getOrNull(lineIndex)?.promptText

        return if (promptText.isNullOrEmpty()) {
            if (lineIndex >= lineWordsList.lastIndex) {
                Log.i(TAG, "Song finished at line $lineIndex")
                PromptEvent.SongFinished
            } else {
                Log.i(TAG, "Line $lineIndex completed (no prompt text)")
                PromptEvent.LineCompleted(lineIndex)
            }
        } else {
            Log.i(TAG, "PROMPT line $lineIndex: '$promptText'")
            PromptEvent.SpeakPrompt(
                lineIndex = lineIndex,
                promptText = promptText
            )
        }
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
