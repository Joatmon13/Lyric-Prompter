package com.lyricprompter.audio.tts

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.lyricprompter.audio.routing.AudioRouter
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays metronome clicks for count-in before a performance.
 */
@Singleton
class CountInPlayer @Inject constructor(
    private val context: Context,
    private val audioRouter: AudioRouter
) {
    private var toneGenerator: ToneGenerator? = null
    private var isStopped = false

    companion object {
        private const val TAG = "CountInPlayer"
        private const val CLICK_DURATION_MS = 100
    }

    /**
     * Initialize the tone generator.
     */
    fun initialize() {
        if (toneGenerator != null) return

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            Log.i(TAG, "CountInPlayer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Play count-in clicks at the specified BPM.
     *
     * @param bpm Beats per minute
     * @param beats Number of beats to play
     * @param onBeat Callback for each beat (for UI updates)
     * @param onComplete Callback when count-in is finished
     */
    suspend fun playCountIn(
        bpm: Int,
        beats: Int,
        onBeat: (Int) -> Unit,
        onComplete: () -> Unit
    ) {
        initialize()
        isStopped = false
        val intervalMs = (60_000.0 / bpm).toLong()

        Log.i(TAG, "Starting count-in: $beats beats at $bpm BPM (${intervalMs}ms interval)")

        for (beat in 1..beats) {
            if (isStopped) {
                Log.i(TAG, "Count-in stopped")
                return
            }

            onBeat(beat)
            playClick()

            if (beat < beats) {
                delay(intervalMs)
            }
        }

        // Small delay after last beat before starting performance
        delay(intervalMs / 2)

        if (!isStopped) {
            Log.i(TAG, "Count-in complete")
            onComplete()
        }
    }

    /**
     * Play a single click sound.
     */
    private fun playClick() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, CLICK_DURATION_MS)
            Log.d(TAG, "Click!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play click", e)
        }
    }

    /**
     * Stop the count-in early.
     */
    fun stop() {
        isStopped = true
        toneGenerator?.stopTone()
        Log.i(TAG, "Count-in stopped")
    }

    /**
     * Release resources.
     */
    fun release() {
        stop()
        toneGenerator?.release()
        toneGenerator = null
        Log.i(TAG, "CountInPlayer released")
    }
}
