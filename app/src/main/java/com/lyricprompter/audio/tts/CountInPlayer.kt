package com.lyricprompter.audio.tts

import android.util.Log
import com.lyricprompter.audio.routing.AudioRouter
import com.lyricprompter.domain.model.Song
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles audio-based count-in using TTS for a fully audio-first experience.
 * Speaks: song name → key → time signature → count "1, 2, 3, 4" → first line
 */
@Singleton
class CountInPlayer @Inject constructor(
    private val promptSpeaker: PromptSpeaker,
    private val audioRouter: AudioRouter
) {
    private var isStopped = false

    companion object {
        private const val TAG = "CountInPlayer"
    }

    /**
     * Play the full audio-based intro sequence.
     *
     * @param song The song to introduce
     * @param onBeat Callback for each count beat (for UI updates)
     * @param onComplete Callback when intro is finished
     */
    suspend fun playCountIn(
        song: Song,
        onBeat: (Int) -> Unit,
        onComplete: () -> Unit
    ) {
        isStopped = false
        val bpm = song.bpm ?: 120
        val beats = song.countInTotalBeats
        val beatsPerBar = song.beatsPerBar
        val intervalMs = (60_000.0 / bpm).toLong()

        Log.i(TAG, "Starting audio intro for '${song.title}' at $bpm BPM")

        // Start Bluetooth SCO first so TTS goes to headphones
        Log.i(TAG, "Starting Bluetooth for audio intro")
        audioRouter.startBluetoothForPrompts()
        delay(600) // Give SCO time to fully connect (prevents clipping first word)

        if (isStopped) return

        // 1. Speak song name
        Log.i(TAG, "Speaking song name: ${song.title}")
        promptSpeaker.speakAndWait(song.title)
        delay(400)

        if (isStopped) return

        // 2. Speak key (if set)
        song.displayKey?.let { key ->
            Log.i(TAG, "Speaking key: $key")
            promptSpeaker.speakAndWait("Key of $key")
            delay(400)
        }

        if (isStopped) return

        // 3. Speak time signature
        song.timeSignature?.let { timeSig ->
            Log.i(TAG, "Speaking time signature: $timeSig")
            promptSpeaker.speakAndWait("$timeSig time")
            delay(400)
        }

        if (isStopped) return

        // 4. Count in (speak numbers at tempo)
        Log.i(TAG, "Starting count: $beats beats at $bpm BPM")
        for (beat in 1..beats) {
            if (isStopped) {
                Log.i(TAG, "Count-in stopped")
                return
            }

            onBeat(beat)

            // Speak beat number on downbeats (beat 1 of each bar)
            val beatInBar = ((beat - 1) % beatsPerBar) + 1
            if (beatInBar == 1) {
                val barNumber = ((beat - 1) / beatsPerBar) + 1
                promptSpeaker.speak(barNumber.toString())
            }

            if (beat < beats) {
                delay(intervalMs)
            }
        }

        // Small pause after last count
        delay(intervalMs / 2)

        if (isStopped) return

        // 5. Speak first line as prompt
        val firstLine = song.lines.firstOrNull()?.text
        if (firstLine != null) {
            Log.i(TAG, "Speaking first line: $firstLine")
            promptSpeaker.speakAndWait(firstLine)
            delay(200)
        }

        if (!isStopped) {
            Log.i(TAG, "Audio intro complete")
            onComplete()
        }
    }

    /**
     * Legacy method for backward compatibility - redirects to full intro.
     */
    suspend fun playCountIn(
        bpm: Int,
        beats: Int,
        onBeat: (Int) -> Unit,
        onComplete: () -> Unit
    ) {
        // This is called when song doesn't have full context
        // Just do a simple numeric count
        isStopped = false
        val intervalMs = (60_000.0 / bpm).toLong()

        Log.i(TAG, "Starting simple count-in: $beats beats at $bpm BPM")

        audioRouter.startBluetoothForPrompts()
        delay(300)

        for (beat in 1..beats) {
            if (isStopped) return

            onBeat(beat)
            promptSpeaker.speak(beat.toString())

            if (beat < beats) {
                delay(intervalMs)
            }
        }

        delay(intervalMs / 2)

        if (!isStopped) {
            Log.i(TAG, "Count-in complete")
            onComplete()
        }
    }

    /**
     * Stop the intro early.
     */
    fun stop() {
        isStopped = true
        promptSpeaker.stop()
        Log.i(TAG, "Intro stopped")
    }

    /**
     * Release resources.
     */
    fun release() {
        stop()
        Log.i(TAG, "CountInPlayer released")
    }
}
