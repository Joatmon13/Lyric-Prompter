package com.lyricprompter.domain.usecase

import com.lyricprompter.domain.model.LyricLine
import com.lyricprompter.domain.model.Song
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for processing raw lyrics text into a structured Song.
 * Handles text cleaning, line splitting, word extraction, and vocabulary building.
 */
class ProcessLyricsUseCase @Inject constructor() {

    /**
     * Process raw lyrics text and create a Song with the given metadata.
     *
     * @param rawLyrics The raw lyrics text
     * @param title The song title
     * @param artist The artist name
     * @param promptWordCount Number of words to include in prompts (0 = full line, default)
     * @return A new Song with processed lyrics
     */
    fun process(
        rawLyrics: String,
        title: String,
        artist: String,
        promptWordCount: Int = 0
    ): Song {
        // 1. Clean up text
        val cleaned = cleanLyrics(rawLyrics)

        // 2. Split into lines (keep original with markers for detecting)
        val rawLineTexts = splitIntoLines(cleaned)

        // 3. Build LyricLine objects with prompts for the NEXT line
        // If ANY line has "//" marker, use marker-based prompting (only prompt when marker present)
        // If NO lines have markers, default to prompting every line (backward compatible)
        val anyHasMarker = rawLineTexts.any { hasPromptMarker(it) }

        val lyricLines = rawLineTexts.mapIndexed { index, rawText ->
            val hasMarker = hasPromptMarker(rawText)
            // Keep // in the text so it's visible in the editor
            // But strip it for word extraction and prompt generation
            val textForDisplay = rawText  // Keep // for visibility
            val textForMatching = stripPromptMarker(rawText)  // Strip for word matching
            val nextRawText = rawLineTexts.getOrNull(index + 1)
            val nextText = nextRawText?.let { stripPromptMarker(it) }

            LyricLine(
                index = index,
                text = textForDisplay,
                words = extractWords(textForMatching),
                promptText = generatePrompt(nextText, promptWordCount),
                // If no markers in song, default to prompting every line
                // If markers exist, only prompt lines with markers
                hasPromptMarker = if (anyHasMarker) hasMarker else true
            )
        }

        // 4. Extract vocabulary (all unique words)
        val vocabulary = lyricLines
            .flatMap { it.words }
            .toSet()

        // 5. Build Song with defaults
        return Song(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            artist = artist.trim(),
            lines = lyricLines,
            vocabulary = vocabulary,
            promptWordCount = promptWordCount
        )
    }

    /**
     * Reprocess an existing song's lyrics (e.g., after editing).
     */
    fun reprocess(song: Song, newLyrics: String): Song {
        val processed = process(
            rawLyrics = newLyrics,
            title = song.title,
            artist = song.artist,
            promptWordCount = song.promptWordCount
        )

        return song.copy(
            lines = processed.lines,
            vocabulary = processed.vocabulary,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Update prompt word count for a song (regenerates prompts).
     */
    fun updatePromptWordCount(song: Song, promptWordCount: Int): Song {
        val lineTexts = song.lines.map { it.text }

        val updatedLines = song.lines.mapIndexed { index, line ->
            val nextLineText = lineTexts.getOrNull(index + 1)
            line.copy(
                promptText = generatePrompt(nextLineText, promptWordCount)
            )
        }

        return song.copy(
            lines = updatedLines,
            promptWordCount = promptWordCount,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Clean up raw lyrics text.
     */
    private fun cleanLyrics(raw: String): String {
        return raw
            // Remove section markers like [Verse 1], [Chorus], [Bridge], etc.
            .replace(Regex("\\[.*?]"), "")
            // Remove parenthetical annotations like (x2), (repeat), etc.
            .replace(Regex("\\(x\\d+\\)"), "")
            .replace(Regex("\\(repeat\\)", RegexOption.IGNORE_CASE), "")
            // Normalize line endings
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Remove multiple blank lines (keep single blank lines for verse breaks)
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Split lyrics text into individual lines.
     */
    private fun splitIntoLines(text: String): List<String> {
        return text
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Check if a line has the "//" prompt marker at the end.
     */
    private fun hasPromptMarker(line: String): Boolean {
        return line.trimEnd().endsWith("//")
    }

    /**
     * Strip the "//" prompt marker from the end of a line.
     */
    private fun stripPromptMarker(line: String): String {
        return if (hasPromptMarker(line)) {
            line.trimEnd().dropLast(2).trimEnd()
        } else {
            line
        }
    }

    /**
     * Extract normalized words from a line for matching.
     */
    private fun extractWords(line: String): List<String> {
        return line
            .lowercase()
            // Keep apostrophes for contractions (don't, I'm, etc.)
            .replace(Regex("[^a-z0-9'\\s]"), "")
            // Split on whitespace
            .split(Regex("\\s+"))
            // Remove empty strings and standalone apostrophes
            .filter { it.isNotEmpty() && it != "'" }
    }

    /**
     * Generate prompt text from the next line.
     * If wordCount is 0 or negative, use the full line.
     */
    private fun generatePrompt(nextLine: String?, wordCount: Int): String {
        if (nextLine.isNullOrBlank()) return ""

        // If wordCount is 0 or less, use the full line
        if (wordCount <= 0) {
            return nextLine.trim()
        }

        // Split preserving original capitalization for TTS
        val words = nextLine
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        // If wordCount is >= line length, just return full line
        if (wordCount >= words.size) {
            return nextLine.trim()
        }

        return words.take(wordCount).joinToString(" ")
    }
}
