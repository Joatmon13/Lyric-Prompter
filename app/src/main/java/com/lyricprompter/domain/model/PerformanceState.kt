package com.lyricprompter.domain.model

/**
 * Represents the current state of a performance session.
 */
data class PerformanceState(
    val status: PerformanceStatus,
    val currentLineIndex: Int,
    val recognizedWords: List<String>,
    val lineConfidence: Float,
    val lastPromptedLine: Int,
    val startTime: Long?,
    val song: Song
) {
    companion object {
        fun initial(song: Song) = PerformanceState(
            status = PerformanceStatus.Ready,
            currentLineIndex = 0,
            recognizedWords = emptyList(),
            lineConfidence = 0f,
            lastPromptedLine = -1,
            startTime = null,
            song = song
        )
    }
}

/**
 * Performance status states.
 */
sealed interface PerformanceStatus {
    data object Ready : PerformanceStatus
    data class CountIn(val currentBeat: Int, val totalBeats: Int) : PerformanceStatus
    data object Listening : PerformanceStatus
    data object Paused : PerformanceStatus
    data object Finished : PerformanceStatus
}
