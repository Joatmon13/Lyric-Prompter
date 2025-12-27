package com.lyricprompter.domain.model

import kotlin.math.ceil

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
    val countInBars: Int = 3,

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
         * Count-in bars range.
         */
        val COUNT_IN_BARS_RANGE = 2..6

        /**
         * Target duration for count-in in seconds.
         */
        private const val COUNT_IN_TARGET_SECONDS = 8.0

        /**
         * Get beats per bar from time signature string.
         */
        fun beatsPerBar(timeSignature: String?): Int {
            return when (timeSignature) {
                "4/4" -> 4
                "3/4" -> 3
                "2/4" -> 2
                "6/8" -> 6  // Compound time - 6 eighth notes
                "12/8" -> 4 // Compound time - felt as 4 beats
                else -> 4   // Default to 4/4
            }
        }

        /**
         * Calculate optimal count-in bars for a given BPM to achieve ~8 seconds.
         * Returns a value clamped to COUNT_IN_BARS_RANGE.
         *
         * Examples (4/4 time):
         * - 60 BPM → 2 bars (~8 sec)
         * - 90 BPM → 3 bars (~8 sec)
         * - 120 BPM → 4 bars (~8 sec)
         * - 150 BPM → 5 bars (~8 sec)
         * - 180 BPM → 6 bars (~8 sec)
         */
        fun calculateOptimalCountInBars(bpm: Int?, timeSignature: String?): Int {
            val bpmValue = bpm ?: return 3  // Default if no BPM
            val beatsPerBarValue = beatsPerBar(timeSignature)

            // Calculate how many beats fit in target duration
            val beatsInTargetDuration = (COUNT_IN_TARGET_SECONDS * bpmValue) / 60.0

            // Convert to bars (round up to complete bar)
            val bars = ceil(beatsInTargetDuration / beatsPerBarValue).toInt()

            // Clamp to valid range
            return bars.coerceIn(COUNT_IN_BARS_RANGE)
        }
    }

    /**
     * Returns beats per bar for this song's time signature.
     */
    val beatsPerBar: Int
        get() = Companion.beatsPerBar(timeSignature)

    /**
     * Returns total count-in beats (bars × beats per bar).
     */
    val countInTotalBeats: Int
        get() = countInBars * beatsPerBar
}
