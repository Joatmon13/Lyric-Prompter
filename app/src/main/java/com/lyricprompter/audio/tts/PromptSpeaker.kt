package com.lyricprompter.audio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.lyricprompter.audio.routing.AudioRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Text-to-speech wrapper for speaking lyric prompts.
 */
@Singleton
class PromptSpeaker @Inject constructor(
    private val context: Context,
    private val audioRouter: AudioRouter
) {
    private var tts: TextToSpeech? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var speechRate = 1.0f
    private var pitch = 1.0f

    companion object {
        private const val TAG = "PromptSpeaker"
    }

    /**
     * Initialize the TTS engine.
     */
    suspend fun initialize(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "TTS language not supported")
                    _isReady.value = false
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("TTS language not supported")))
                    }
                } else {
                    setupUtteranceListener()
                    _isReady.value = true
                    Log.i(TAG, "TTS initialized successfully")
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                _isReady.value = false
                if (continuation.isActive) {
                    continuation.resume(Result.failure(Exception("TTS initialization failed")))
                }
            }
        }

        continuation.invokeOnCancellation {
            // Cleanup if cancelled
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.e(TAG, "TTS error for utterance: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                Log.e(TAG, "TTS error $errorCode for utterance: $utteranceId")
            }
        })
    }

    /**
     * Speak the given text immediately.
     *
     * @param text The text to speak
     * @param onComplete Optional callback when speech completes
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!_isReady.value) {
            Log.w(TAG, "TTS not ready, cannot speak")
            onComplete?.invoke()
            return
        }

        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        // Set up completion listener if provided
        if (onComplete != null) {
            val currentListener = tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(id: String?) {
                        _isSpeaking.value = false
                        if (id == utteranceId) {
                            onComplete()
                        }
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(id: String?) {
                        _isSpeaking.value = false
                        if (id == utteranceId) {
                            onComplete()
                        }
                    }
                }
            )
        }

        // Apply current settings
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)

        // Speak with QUEUE_FLUSH to interrupt any current speech
        @Suppress("DEPRECATION")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        Log.d(TAG, "Speaking: $text")
    }

    /**
     * Speak text and wait for completion.
     */
    suspend fun speakAndWait(text: String): Unit = suspendCancellableCoroutine { continuation ->
        speak(text) {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        continuation.invokeOnCancellation {
            stop()
        }
    }

    /**
     * Stop any current speech.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Configure speech rate.
     *
     * @param rate Speech rate (0.5 = half speed, 1.0 = normal, 2.0 = double)
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    /**
     * Configure pitch.
     *
     * @param pitchValue Pitch (0.5 = low, 1.0 = normal, 2.0 = high)
     */
    fun setPitch(pitchValue: Float) {
        pitch = pitchValue.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
    }

    /**
     * Get available TTS voices.
     */
    fun getAvailableVoices(): List<String> {
        return tts?.voices?.map { it.name } ?: emptyList()
    }

    /**
     * Release TTS resources.
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        Log.i(TAG, "TTS released")
    }
}
