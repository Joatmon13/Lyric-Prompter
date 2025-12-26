package com.lyricprompter.domain.model

/**
 * Represents a single line of lyrics with its normalized words and prompt text.
 *
 * @property index The line number (0-indexed)
 * @property text The original text of the line (e.g., "Is this the real life")
 * @property words The normalized words for matching (e.g., ["is", "this", "the", "real", "life"])
 * @property promptText The text to speak as a prompt (first N words of the NEXT line)
 */
data class LyricLine(
    val index: Int,
    val text: String,
    val words: List<String>,
    val promptText: String
)
