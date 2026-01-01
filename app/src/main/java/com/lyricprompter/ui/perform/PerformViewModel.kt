package com.lyricprompter.ui.perform

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.audio.routing.AudioRouter
import com.lyricprompter.audio.tts.CountInPlayer
import com.lyricprompter.audio.tts.PromptSpeaker
import com.lyricprompter.audio.vosk.VoskEngine
import com.lyricprompter.data.repository.SongRepository
import com.lyricprompter.diagnostics.DiagnosticLogger
import com.lyricprompter.domain.model.PerformanceState
import com.lyricprompter.domain.model.PerformanceStatus
import com.lyricprompter.domain.model.Song
import com.lyricprompter.tracking.PositionTracker
import com.lyricprompter.tracking.PromptEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerformViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val voskEngine: VoskEngine,
    private val positionTracker: PositionTracker,
    private val promptSpeaker: PromptSpeaker,
    private val countInPlayer: CountInPlayer,
    private val audioRouter: AudioRouter,
    private val diagnosticLogger: DiagnosticLogger
) : ViewModel() {

    companion object {
        private const val TAG = "LP.Session"
    }

    private var sessionStartTime: Long = 0
    private var promptedCount = 0
    private var skippedCount = 0

    private val songId: String = checkNotNull(savedStateHandle["songId"])

    private val _uiState = MutableStateFlow<PerformUiState>(PerformUiState.Loading)
    val uiState: StateFlow<PerformUiState> = _uiState.asStateFlow()

    init {
        loadSong()
    }

    private fun loadSong() {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId)
            if (song == null) {
                Log.e(TAG, "[LOAD_ERROR] songId=$songId | error=not_found")
                _uiState.value = PerformUiState.Error("Song not found")
                return@launch
            }

            // Initialize components
            promptSpeaker.initialize()
            val vocabResult = voskEngine.loadVocabulary(song.vocabulary)
            positionTracker.loadSong(song)

            // Log vocabulary load to diagnostics
            diagnosticLogger.logVocabLoaded(song.vocabulary.size)

            _uiState.value = PerformUiState.Ready(
                state = PerformanceState.initial(song)
            )

            Log.i(TAG, "[SONG_LOADED] " +
                "song=\"${song.title}\" | " +
                "artist=\"${song.artist}\" | " +
                "lines=${song.lineCount} | " +
                "vocabSize=${song.vocabulary.size} | " +
                "vocabLoaded=${vocabResult.isSuccess}")
        }
    }

    fun start() {
        val currentState = (_uiState.value as? PerformUiState.Ready)?.state ?: return
        val song = currentState.song

        // Reset counters
        sessionStartTime = System.currentTimeMillis()
        promptedCount = 0
        skippedCount = 0

        // Enter performance mode to prevent interruptions
        val focusGranted = audioRouter.enterPerformanceMode(enableDndMode = true)
        val usePhoneMic = audioRouter.usePhoneMic
        val btConnected = audioRouter.isBluetoothConnected()
        val audioSource = if (usePhoneMic) "PHONE_MIC+A2DP" else "BLUETOOTH_SCO"

        // Start diagnostic session
        diagnosticLogger.startSession(
            song = song,
            usePhoneMic = usePhoneMic,
            bluetoothConnected = btConnected
        )

        Log.i(TAG, "[SESSION_START] " +
            "song=\"${song.title}\" | " +
            "artist=\"${song.artist}\" | " +
            "lines=${song.lineCount} | " +
            "triggerPct=${song.triggerPercent} | " +
            "promptWords=${song.promptWordCount} | " +
            "audioSource=$audioSource | " +
            "audioFocus=$focusGranted")

        viewModelScope.launch {
            // Audio-based intro (speaks song name, key, time sig, count, first line)
            if (song.countInEnabled) {
                val beatsPerBar = song.beatsPerBar
                val totalBars = song.countInBars
                val totalBeats = song.countInTotalBeats

                // Initial state: bar 1, beat 1, barsRemaining = totalBars (countdown: 3 -> 2 -> 1)
                _uiState.update { state ->
                    if (state is PerformUiState.Ready) {
                        state.copy(
                            state = state.state.copy(
                                status = PerformanceStatus.CountIn(
                                    currentBar = 1,
                                    totalBars = totalBars,
                                    currentBeatInBar = 1,
                                    beatsPerBar = beatsPerBar,
                                    barsRemaining = totalBars
                                )
                            )
                        )
                    } else state
                }

                // Use new audio-based intro with full song context
                countInPlayer.playCountIn(
                    song = song,
                    onBeat = { beat ->
                        // Calculate which bar and beat within bar (1-indexed)
                        val currentBar = ((beat - 1) / beatsPerBar) + 1
                        val currentBeatInBar = ((beat - 1) % beatsPerBar) + 1
                        // Bars remaining counts down: 3 -> 2 -> 1 -> 0 (on last beat of last bar)
                        val barsRemaining = totalBars - currentBar + 1

                        _uiState.update { state ->
                            if (state is PerformUiState.Ready) {
                                state.copy(
                                    state = state.state.copy(
                                        status = PerformanceStatus.CountIn(
                                            currentBar = currentBar,
                                            totalBars = totalBars,
                                            currentBeatInBar = currentBeatInBar,
                                            beatsPerBar = beatsPerBar,
                                            barsRemaining = barsRemaining
                                        )
                                    )
                                )
                            } else state
                        }
                    },
                    onComplete = {
                        Log.d(TAG, "[COUNT_IN_DONE] starting_listening")
                        startListening()
                    }
                )
            } else {
                // No intro, start Bluetooth and listening immediately
                audioRouter.startBluetoothForPrompts()
                startListening()
            }
        }
    }

    private fun startListening() {
        Log.i(TAG, "[LISTENING_START]")
        _uiState.update { state ->
            if (state is PerformUiState.Ready) {
                state.copy(
                    state = state.state.copy(
                        status = PerformanceStatus.Listening,
                        startTime = System.currentTimeMillis()
                    )
                )
            } else state
        }

        voskEngine.startListening(
            onPartialResult = { text ->
                handleRecognition(text, isPartial = true)
            },
            onFinalResult = { text ->
                handleRecognition(text, isPartial = false)
            },
            onError = { error ->
                Log.e(TAG, "[VOSK_ERROR] error=\"${error.message}\"")
            }
        )
    }

    private fun handleRecognition(text: String, isPartial: Boolean = false) {
        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it != "[unk]" }

        if (words.isEmpty()) return

        // Log Vosk recognition to diagnostics
        diagnosticLogger.logVoskResult(text, words.size, isFinal = !isPartial)

        val event = positionTracker.onWordsRecognized(words, isFinal = !isPartial)
        val trackingState = positionTracker.getState()

        // Update UI state (keep only last 15 words - UI displays 8, matching uses its own buffer)
        _uiState.update { state ->
            if (state is PerformUiState.Ready) {
                val updatedWords = (state.state.recognizedWords + words).takeLast(15)
                state.copy(
                    state = state.state.copy(
                        currentLineIndex = trackingState.currentLineIndex,
                        recognizedWords = updatedWords
                    )
                )
            } else state
        }

        // Handle prompt events
        when (event) {
            is PromptEvent.SpeakPrompt -> {
                promptedCount++
                val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000.0
                Log.i(TAG, "[PROMPT_FIRED] " +
                    "line=${event.lineIndex} | " +
                    "promptText=\"${event.promptText}\" | " +
                    "elapsed=${"%.1f".format(elapsed)}s")

                // Log prompt to diagnostics
                diagnosticLogger.logPromptFired(
                    lineIndex = event.lineIndex,
                    promptText = event.promptText,
                    wordCount = event.promptText.split(Regex("\\s+")).size
                )

                // Brief pause before speaking to let user finish their phrase
                viewModelScope.launch {
                    kotlinx.coroutines.delay(150)
                    promptSpeaker.speak(event.promptText)
                }
                _uiState.update { state ->
                    if (state is PerformUiState.Ready) {
                        state.copy(
                            state = state.state.copy(
                                lastPromptedLine = event.lineIndex
                            )
                        )
                    } else state
                }
            }
            is PromptEvent.SongFinished -> {
                Log.i(TAG, "[SONG_FINISHED] prompted=$promptedCount")
                stop()
            }
            else -> { /* No action */ }
        }
    }

    fun stop() {
        voskEngine.stopListening()
        countInPlayer.stop()
        promptSpeaker.stop()
        audioRouter.exitPerformanceMode()

        // End diagnostic session
        diagnosticLogger.endSession()

        val duration = (System.currentTimeMillis() - sessionStartTime) / 1000.0
        val currentState = (_uiState.value as? PerformUiState.Ready)?.state
        val totalLines = currentState?.song?.lineCount ?: 0

        Log.i(TAG, "[SESSION_END] " +
            "duration=${"%.1f".format(duration)}s | " +
            "linesTotal=$totalLines | " +
            "linesPrompted=$promptedCount")

        _uiState.update { state ->
            if (state is PerformUiState.Ready) {
                state.copy(
                    state = state.state.copy(
                        status = PerformanceStatus.Finished
                    )
                )
            } else state
        }
    }

    fun restart() {
        positionTracker.reset()
        _uiState.update { state ->
            if (state is PerformUiState.Ready) {
                state.copy(
                    state = PerformanceState.initial(state.state.song)
                )
            } else state
        }
    }

    override fun onCleared() {
        super.onCleared()
        voskEngine.stopListening()
        countInPlayer.stop()
        promptSpeaker.stop()
        audioRouter.exitPerformanceMode()
    }
}

sealed interface PerformUiState {
    data object Loading : PerformUiState
    data class Ready(val state: PerformanceState) : PerformUiState
    data class Error(val message: String) : PerformUiState
}
