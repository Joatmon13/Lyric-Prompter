package com.lyricprompter.audio.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
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
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var isLoaded = false
    private var isStopped = false

    companion object {
        private const val TAG = "CountInPlayer"
    }

    /**
     * Initialize the sound pool and load click sound.
     */
    fun initialize() {
        if (soundPool != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
            .apply {
                setOnLoadCompleteListener { _, _, status ->
                    isLoaded = status == 0
                    if (isLoaded) {
                        Log.i(TAG, "Click sound loaded")
                    } else {
                        Log.e(TAG, "Failed to load click sound")
                    }
                }

                // Load click sound from assets or generate programmatically
                // For now, we'll use TTS to say "1, 2, 3, 4" as a fallback
                // TODO: Add actual click sound file to res/raw
            }

        Log.i(TAG, "CountInPlayer initialized")
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
        if (isLoaded && soundPool != null) {
            soundPool?.play(clickSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            // Fallback: could use ToneGenerator or just skip
            Log.d(TAG, "Click (sound not loaded, silent)")
        }
    }

    /**
     * Stop the count-in early.
     */
    fun stop() {
        isStopped = true
        soundPool?.autoPause()
        Log.i(TAG, "Count-in stopped")
    }

    /**
     * Release resources.
     */
    fun release() {
        stop()
        soundPool?.release()
        soundPool = null
        isLoaded = false
        Log.i(TAG, "CountInPlayer released")
    }
}
