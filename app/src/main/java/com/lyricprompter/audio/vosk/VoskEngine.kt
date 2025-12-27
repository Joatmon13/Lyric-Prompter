package com.lyricprompter.audio.vosk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine for Vosk offline speech recognition.
 * Handles model loading, vocabulary setup, and streaming recognition.
 */
@Singleton
class VoskEngine @Inject constructor(
    private val context: Context
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    private val _state = MutableStateFlow<VoskState>(VoskState.Uninitialized)
    val state: StateFlow<VoskState> = _state.asStateFlow()

    private var currentListener: RecognitionListener? = null

    companion object {
        private const val TAG = "VoskEngine"
        private const val SAMPLE_RATE = 16000.0f
        private const val MODEL_PATH = "vosk-model-small-en-us"
    }

    /**
     * Initialize the Vosk model. Should be called once at app startup.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value is VoskState.Ready) {
            return@withContext Result.success(Unit)
        }

        _state.value = VoskState.Loading

        try {
            // Unpack model from assets if needed
            StorageService.unpack(
                context,
                MODEL_PATH,
                "model",
                { loadedModel ->
                    model = loadedModel
                    _state.value = VoskState.Ready
                    Log.i(TAG, "Vosk model loaded successfully")
                },
                { exception ->
                    Log.e(TAG, "Failed to load Vosk model", exception)
                    _state.value = VoskState.Error(exception.message ?: "Unknown error")
                }
            )

            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to unpack Vosk model", e)
            _state.value = VoskState.Error(e.message ?: "Failed to load model")
            Result.failure(e)
        }
    }

    /**
     * Load a custom vocabulary for a song.
     * This constrains recognition to improve accuracy and speed.
     *
     * @param vocabulary Set of words expected in the song
     */
    fun loadVocabulary(vocabulary: Set<String>): Result<Unit> {
        val currentModel = model ?: return Result.failure(
            IllegalStateException("Model not initialized")
        )

        return try {
            // Build JSON grammar for Vosk
            val grammar = buildGrammar(vocabulary)

            // Create recognizer with grammar
            recognizer?.close()
            recognizer = Recognizer(currentModel, SAMPLE_RATE, grammar)

            Log.i(TAG, "Loaded vocabulary with ${vocabulary.size} words")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vocabulary", e)
            Result.failure(e)
        }
    }

    /**
     * Update the grammar to focus on specific words (e.g., for current/next lines).
     * This dramatically improves recognition accuracy by narrowing what Vosk listens for.
     *
     * @param words Words to listen for (typically from current and next few lines)
     */
    fun updateGrammar(words: Set<String>) {
        val currentRecognizer = recognizer ?: return

        try {
            val grammar = buildGrammar(words)
            currentRecognizer.setGrammar(grammar)
            Log.d(TAG, "Updated grammar to ${words.size} words: ${words.take(10)}...")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update grammar", e)
        }
    }

    /**
     * Start streaming speech recognition.
     *
     * @param onPartialResult Called with partial recognition results
     * @param onFinalResult Called with final recognition results
     * @param onError Called if an error occurs
     */
    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            onError(IllegalStateException("Recognizer not initialized. Call loadVocabulary first."))
            return
        }

        try {
            stopListening()

            currentListener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseResult(it)
                        if (text.isNotEmpty()) {
                            onPartialResult(text)
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseResult(it)
                        if (text.isNotEmpty()) {
                            onFinalResult(text)
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseResult(it)
                        if (text.isNotEmpty()) {
                            onFinalResult(text)
                        }
                    }
                }

                override fun onError(exception: Exception?) {
                    exception?.let { onError(it) }
                }

                override fun onTimeout() {
                    // No action needed - recognition continues
                }
            }

            speechService = SpeechService(currentRecognizer, SAMPLE_RATE).apply {
                startListening(currentListener)
            }

            Log.i(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            onError(e)
        }
    }

    /**
     * Stop speech recognition.
     */
    fun stopListening() {
        speechService?.apply {
            stop()
            shutdown()
        }
        speechService = null
        currentListener = null
        Log.i(TAG, "Stopped listening")
    }

    /**
     * Release all resources.
     */
    fun release() {
        stopListening()
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        _state.value = VoskState.Uninitialized
        Log.i(TAG, "Released Vosk resources")
    }

    /**
     * Build Vosk grammar JSON from vocabulary.
     */
    private fun buildGrammar(words: Set<String>): String {
        val wordList = words
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\", \"") { escapeJson(it) }

        // Include [unk] for unknown words (coughs, mumbles, etc.)
        return "[\"$wordList\", \"[unk]\"]"
    }

    /**
     * Escape special characters in JSON string.
     */
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    /**
     * Parse Vosk result JSON to extract recognized text.
     */
    private fun parseResult(json: String): String {
        // Vosk returns JSON like: {"text": "hello world"} or {"partial": "hello"}
        return try {
            val textMatch = Regex("\"(?:text|partial)\"\\s*:\\s*\"([^\"]*?)\"").find(json)
            textMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse result: $json", e)
            ""
        }
    }
}

/**
 * Vosk engine state.
 */
sealed interface VoskState {
    data object Uninitialized : VoskState
    data object Loading : VoskState
    data object Ready : VoskState
    data class Error(val message: String) : VoskState
}
