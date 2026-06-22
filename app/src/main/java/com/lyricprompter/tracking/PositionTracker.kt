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

        // A later line must beat the next expected line's score by this margin
        // before we skip ahead to it (stay sequential unless evidence is strong).
        private const val SKIP_MARGIN = 0.20f

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
        promptTrigger.configureSong(song.bpm)
        reset()
        Log.i(TAG, "[SONG_LOADED] lines=${song.lineCount} | triggerPct=${song.triggerPercent} | bpm=${song.bpm}")
    }

    /**
     * Reset tracking state to beginning of song.
     */
    fun reset() {
        currentLineIndex = 0
        lastPromptedLine = -1
        recognizedBuffer.clear()
        promptTrigger.resetCooldown()
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
     * NEW LOGIC: Only trigger prompts on FINAL results (silence detected).
     * This ensures we wait for the performer to finish singing the line
     * before prompting the next one.
     *
     * @param newWords Words just recognized (may be partial or final result)
     * @param isFinal True if this is a final result (silence detected), false for partial
     * @return A PromptEvent if action is needed, null otherwise
     */
    fun onWordsRecognized(newWords: List<String>, isFinal: Boolean = false): PromptEvent? {
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

        // LOOK-AHEAD MODE: score the next expected line plus a small window of
        // following lines, so we can catch up when a line was missed (e.g. a weak
        // unstressed word dropped and we never hit threshold on it). The window is
        // deliberately small so we don't jump to a repeated chorus elsewhere.
        val firstCandidate = lastPromptedLine + 1

        // Check if we've reached the end of the song
        if (firstCandidate >= lineWordsList.size) {
            Log.d(TAG, "[END_OF_SONG] nextExpected=$firstCandidate | totalLines=${lineWordsList.size}")
            return null
        }

        val lastCandidate = minOf(lineWordsList.lastIndex, firstCandidate + SEARCH_WINDOW_AFTER)

        // Bias toward the earliest (sequential) line; only prefer a later line
        // when it beats the expected line's score by SKIP_MARGIN.
        var bestLine = firstCandidate
        var bestScore = fuzzyMatcher.matchScore(recognizedBuffer, lineWordsList[firstCandidate])
        for (i in (firstCandidate + 1)..lastCandidate) {
            val score = fuzzyMatcher.matchScore(recognizedBuffer, lineWordsList[i])
            if (score > bestScore + SKIP_MARGIN) {
                bestScore = score
                bestLine = i
            }
        }

        val lineText = currentSong.lines.getOrNull(bestLine)?.text?.take(40) ?: ""
        Log.d(TAG, "[MATCH_RESULT] " +
            "line=$bestLine | " +
            "score=${(bestScore * 100).toInt()}% | " +
            "isFinal=$isFinal | " +
            "skipped=${bestLine - firstCandidate} | " +
            "text=\"$lineText\"")

        // Check if we should trigger a prompt
        // Key change: pass isFinal to shouldPrompt - only trigger on silence
        val lineWordCount = lineWordsList[bestLine].size
        if (promptTrigger.shouldPrompt(
                lineIndex = bestLine,
                matchScore = bestScore,
                triggerPercent = currentSong.triggerPercent,
                lastPromptedLine = lastPromptedLine,
                lineWordCount = lineWordCount,
                isFinal = isFinal
            )
        ) {
            if (bestLine > firstCandidate) {
                Log.i(TAG, "[CATCH_UP] skipping lines $firstCandidate..${bestLine - 1} | matched=$bestLine")
            }
            // Update currentLineIndex to matched line
            currentLineIndex = bestLine
            return triggerPrompt(bestLine, currentSong)
        } else {
            Log.v(TAG, "[WAITING] " +
                "line=$bestLine | " +
                "score=${(bestScore * 100).toInt()}% | " +
                "isFinal=$isFinal | " +
                "lastPrompted=$lastPromptedLine")
        }

        return null
    }

    /**
     * Manually advance one line, e.g. the performer taps to catch up when
     * recognition has fallen behind (a missed line). Forces the prompt for the
     * next expected line, bypassing match-score, silence and cooldown gates,
     * and always speaks the prompt if there is text for it.
     *
     * @return A PromptEvent to act on, or null if already at the end of the song.
     */
    fun advanceManually(): PromptEvent? {
        val currentSong = song ?: return null
        if (lineWordsList.isEmpty()) return null

        val nextExpectedLine = lastPromptedLine + 1
        if (nextExpectedLine >= lineWordsList.size) {
            Log.d(TAG, "[MANUAL_ADVANCE] at_end | nextExpected=$nextExpectedLine")
            return null
        }

        currentLineIndex = nextExpectedLine
        Log.i(TAG, "[MANUAL_ADVANCE] forcing prompt | line=$nextExpectedLine")
        return triggerPrompt(nextExpectedLine, currentSong, forceSpeak = true)
    }

    /**
     * Helper to trigger a prompt and update state.
     *
     * @param forceSpeak When true (manual advance), always speak the prompt
     *   regardless of whether the line carries a `//` marker.
     */
    private fun triggerPrompt(lineIndex: Int, currentSong: Song, forceSpeak: Boolean = false): PromptEvent? {
        lastPromptedLine = lineIndex

        // Set cooldown based on this line's //N notation (or use default)
        val currentLine = currentSong.lines.getOrNull(lineIndex)
        promptTrigger.setCooldownForLine(currentLine?.cooldownBeats)
        promptTrigger.markPromptTriggered()

        // Keep last few words for context, don't clear everything
        val wordsToKeep = recognizedBuffer.takeLast(KEEP_AFTER_PROMPT)
        recognizedBuffer.clear()
        recognizedBuffer.addAll(wordsToKeep)

        // Advance position to next line
        currentLineIndex = lineIndex + 1

        // Regex to match // or //N at end of line (where N is 1-16)
        val promptMarkerRegex = Regex("//([0-9]{1,2})?\\s*$")

        // Check if ANY line in the song has "//" or "//N" marker in the text
        // This handles songs that haven't been reprocessed yet
        val songHasAnyMarkers = currentSong.lines.any { promptMarkerRegex.containsMatchIn(it.text.trimEnd()) }

        // Check hasPromptMarker flag, OR check if text ends with "//" or "//N" (for songs not yet reprocessed)
        // Gson deserializes missing boolean fields as false, so we need the text fallback
        val lineTextHasMarker = currentLine?.text?.let { promptMarkerRegex.containsMatchIn(it.trimEnd()) } == true
        val hasMarker = if (forceSpeak) {
            // Manual advance: always speak, ignore marker gating
            true
        } else if (songHasAnyMarkers) {
            // Song uses markers - check if THIS line has one (either from flag or text)
            currentLine?.hasPromptMarker == true || lineTextHasMarker
        } else {
            // No markers in song - prompt every line (backward compatible)
            true
        }
        val promptText = currentLine?.promptText

        // If line doesn't have prompt marker, just return LineCompleted (no TTS prompt)
        if (!hasMarker) {
            if (lineIndex >= lineWordsList.lastIndex) {
                Log.i(TAG, "[SONG_COMPLETE] lastLine=$lineIndex")
                return PromptEvent.SongFinished
            }
            Log.i(TAG, "[LINE_COMPLETE] line=$lineIndex | noMarker")
            return PromptEvent.LineCompleted(lineIndex)
        }

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
