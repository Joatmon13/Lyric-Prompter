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
     * @param promptWordCount Number of words to include in prompts (default 4)
     * @return A new Song with processed lyrics
     */
    fun process(
        rawLyrics: String,
        title: String,
        artist: String,
        promptWordCount: Int = 4
    ): Song {
        // 1. Clean up text
        val cleaned = cleanLyrics(rawLyrics)

        // 2. Split into lines
        val lineTexts = splitIntoLines(cleaned)

        // 3. Build LyricLine objects with prompts for the NEXT line
        val lyricLines = lineTexts.mapIndexed { index, text ->
            val nextLineText = lineTexts.getOrNull(index + 1)
            LyricLine(
                index = index,
                text = text,
                words = extractWords(text),
                promptText = generatePrompt(nextLineText, promptWordCount)
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
     */
    private fun generatePrompt(nextLine: String?, wordCount: Int): String {
        if (nextLine.isNullOrBlank()) return ""

        // Split preserving original capitalization for TTS
        val words = nextLine
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        return words.take(wordCount).joinToString(" ")
    }
}
