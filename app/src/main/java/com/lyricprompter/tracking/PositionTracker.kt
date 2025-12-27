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
        private const val TAG = "LP.Tracker"

        // How many lines ahead/behind to search
        private const val SEARCH_WINDOW_BEFORE = 0  // Never go back
        const val SEARCH_WINDOW_AFTER = 2   // Look at next 2 lines (allows catching up if one line missed)

        // Max words to keep in buffer
        const val MAX_BUFFER_SIZE = 20

        // Words to keep after prompting
        const val KEEP_AFTER_PROMPT = 5

        // How many lines to include in focused grammar (current + next N)
        private const val GRAMMAR_LINES_AHEAD = 4
    }

    /**
     * Load a song for tracking.
     */
    fun loadSong(song: Song) {
        this.song = song
        this.lineWordsList = song.lines.map { it.words }
        reset()
        Log.i(TAG, "[SONG_LOADED] lines=${song.lineCount} | triggerPct=${song.triggerPercent}")
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
            return null
        }

        val (matchedLineIndex, matchScore) = match
        val lineText = currentSong.lines.getOrNull(matchedLineIndex)?.text?.take(40) ?: ""
        Log.d(TAG, "[MATCH_RESULT] " +
            "line=$matchedLineIndex | " +
            "score=${(matchScore * 100).toInt()}% | " +
            "trigger=${currentSong.triggerPercent}% | " +
            "text=\"$lineText\"")

        // Update current position if we've moved forward
        if (matchedLineIndex > currentLineIndex) {
            Log.i(TAG, "[LINE_ADVANCE] from=$currentLineIndex | to=$matchedLineIndex")
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
            // Check if we skipped any lines - if so, prompt the next expected line instead
            val expectedLine = lastPromptedLine + 1
            if (matchedLineIndex > expectedLine && expectedLine < lineWordsList.size) {
                Log.w(TAG, "[LINE_SKIP] expected=$expectedLine | matched=$matchedLineIndex | prompting_expected")
                return triggerPrompt(expectedLine, currentSong)
            }
            return triggerPrompt(matchedLineIndex, currentSong)
        } else {
            Log.v(TAG, "[THRESHOLD_NOT_MET] " +
                "line=$matchedLineIndex | " +
                "score=${(matchScore * 100).toInt()}% | " +
                "trigger=${currentSong.triggerPercent}% | " +
                "lastPrompted=$lastPromptedLine")
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

        // Get the prompt text (which is for the NEXT line)
        val promptText = currentSong.lines.getOrNull(lineIndex)?.promptText

        return if (promptText.isNullOrEmpty()) {
            if (lineIndex >= lineWordsList.lastIndex) {
                Log.i(TAG, "[SONG_COMPLETE] lastLine=$lineIndex")
                PromptEvent.SongFinished
            } else {
                Log.i(TAG, "[LINE_COMPLETE] line=$lineIndex | noPromptText")
                PromptEvent.LineCompleted(lineIndex)
            }
        } else {
            Log.i(TAG, "[TRIGGER_PROMPT] " +
                "line=$lineIndex | " +
                "nextLine=${lineIndex + 1} | " +
                "promptText=\"$promptText\" | " +
                "bufferKept=${wordsToKeep.size}")
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

    /**
     * Mark that the first line has been spoken during count-in.
     * This sets lastPromptedLine to -1 (since we spoke line 0's text as intro,
     * and line 0's promptText is for line 1), and sets currentLineIndex to 0.
     * When line 0 is matched, it will prompt line 1.
     */
    fun markFirstLineSpoken() {
        // The count-in speaks line 0's TEXT (the actual lyrics of line 0)
        // So the user will then sing line 0, and we need to detect that
        // and prompt with line 0's promptText (which is line 1's lyrics)
        // This is correct - lastPromptedLine should stay at -1
        // currentLineIndex should be 0 (we're tracking line 0)
        currentLineIndex = 0
        lastPromptedLine = -1
        recognizedBuffer.clear()
        Log.d(TAG, "First line spoken during count-in, ready to track line 0")
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
