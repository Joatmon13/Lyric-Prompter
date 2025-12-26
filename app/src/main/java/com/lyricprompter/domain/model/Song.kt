package com.lyricprompter.domain.model

/**
 * Domain model representing a song with its lyrics and performance settings.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,

    // Musical metadata
    val bpm: Int? = null,
    val originalKey: String? = null,
    val performKey: String? = null,
    val timeSignature: String? = "4/4",

    // Count-in settings
    val countInEnabled: Boolean = true,
    val countInBeats: Int = 4,

    // Prompt settings
    val triggerPercent: Int = 70,      // 40-90
    val promptWordCount: Int = 4,       // 2-6

    // Lyrics
    val lines: List<LyricLine> = emptyList(),
    val vocabulary: Set<String> = emptySet(),

    // Organization
    val setlistIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns the raw lyrics text (all lines joined with newlines).
     */
    val lyricsText: String
        get() = lines.joinToString("\n") { it.text }

    /**
     * Returns the total number of lines.
     */
    val lineCount: Int
        get() = lines.size

    /**
     * Returns the display key (perform key if set, otherwise original key).
     */
    val displayKey: String?
        get() = performKey ?: originalKey

    companion object {
        /**
         * Musical keys for dropdown selection.
         */
        val KEYS = listOf(
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
            "Cm", "C#m", "Dm", "D#m", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "A#m", "Bm"
        )

        /**
         * Time signatures for dropdown selection.
         */
        val TIME_SIGNATURES = listOf("4/4", "3/4", "6/8", "2/4", "12/8")

        /**
         * BPM range.
         */
        val BPM_RANGE = 40..220

        /**
         * Trigger percent range.
         */
        val TRIGGER_PERCENT_RANGE = 40..90

        /**
         * Prompt word count range.
         */
        val PROMPT_WORD_COUNT_RANGE = 2..6

        /**
         * Count-in beats range.
         */
        val COUNT_IN_BEATS_RANGE = 1..8
    }
}
