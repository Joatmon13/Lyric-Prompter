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
    private val audioRouter: AudioRouter
) : ViewModel() {

    companion object {
        private const val TAG = "PerformViewModel"
    }

    private val songId: String = checkNotNull(savedStateHandle["songId"])

    private val _uiState = MutableStateFlow<PerformUiState>(PerformUiState.Loading)
    val uiState: StateFlow<PerformUiState> = _uiState.asStateFlow()

    init {
        loadSong()
    }

    private fun loadSong() {
        viewModelScope.launch {
            Log.d(TAG, "Loading song: $songId")
            val song = songRepository.getSongById(songId)
            if (song == null) {
                Log.e(TAG, "Song not found: $songId")
                _uiState.value = PerformUiState.Error("Song not found")
                return@launch
            }

            Log.d(TAG, "Song loaded: ${song.title}, vocabulary size: ${song.vocabulary.size}")

            // Initialize components
            promptSpeaker.initialize()

            val vocabResult = voskEngine.loadVocabulary(song.vocabulary)
            if (vocabResult.isFailure) {
                Log.e(TAG, "Failed to load vocabulary: ${vocabResult.exceptionOrNull()?.message}")
            } else {
                Log.d(TAG, "Vocabulary loaded successfully")
            }

            positionTracker.loadSong(song)

            _uiState.value = PerformUiState.Ready(
                state = PerformanceState.initial(song)
            )
            Log.d(TAG, "Ready for performance")
        }
    }

    fun start() {
        val currentState = (_uiState.value as? PerformUiState.Ready)?.state ?: return
        val song = currentState.song

        // Enter performance mode to prevent interruptions
        val focusGranted = audioRouter.enterPerformanceMode(enableDndMode = true)
        Log.i(TAG, "Performance mode entered, audio focus granted: $focusGranted")

        viewModelScope.launch {
            // Count-in only if enabled AND BPM is set (can't do count-in without knowing the tempo)
            if (song.countInEnabled && song.bpm != null) {
                val beatsPerBar = song.beatsPerBar
                val totalBars = song.countInBars
                val totalBeats = song.countInTotalBeats

                // Initial state: bar 1, beat 1
                _uiState.update { state ->
                    if (state is PerformUiState.Ready) {
                        state.copy(
                            state = state.state.copy(
                                status = PerformanceStatus.CountIn(
                                    currentBar = 1,
                                    totalBars = totalBars,
                                    currentBeatInBar = 1,
                                    beatsPerBar = beatsPerBar
                                )
                            )
                        )
                    } else state
                }

                countInPlayer.playCountIn(
                    bpm = song.bpm,
                    beats = totalBeats,
                    onBeat = { beat ->
                        // Calculate which bar and beat within bar (1-indexed)
                        val currentBar = ((beat - 1) / beatsPerBar) + 1
                        val currentBeatInBar = ((beat - 1) % beatsPerBar) + 1

                        _uiState.update { state ->
                            if (state is PerformUiState.Ready) {
                                state.copy(
                                    state = state.state.copy(
                                        status = PerformanceStatus.CountIn(
                                            currentBar = currentBar,
                                            totalBars = totalBars,
                                            currentBeatInBar = currentBeatInBar,
                                            beatsPerBar = beatsPerBar
                                        )
                                    )
                                )
                            } else state
                        }
                    },
                    onComplete = {
                        // Start Bluetooth SCO after count-in (so metronome isn't muted)
                        audioRouter.startBluetoothForPrompts()
                        startListening()
                    }
                )
            } else {
                // No count-in, start Bluetooth immediately
                audioRouter.startBluetoothForPrompts()
                startListening()
            }
        }
    }

    private fun startListening() {
        Log.i(TAG, "Starting listening...")
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
                Log.d(TAG, "Partial result: $text")
                handleRecognition(text)
            },
            onFinalResult = { text ->
                Log.d(TAG, "Final result: $text")
                handleRecognition(text)
            },
            onError = { error ->
                Log.e(TAG, "Recognition error: ${error.message}", error)
            }
        )
    }

    private fun handleRecognition(text: String) {
        Log.v(TAG, "Handle recognition: '$text'")
        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it != "[unk]" }

        if (words.isEmpty()) {
            Log.v(TAG, "No valid words in recognition")
            return
        }

        Log.d(TAG, "Recognized words: $words")

        val event = positionTracker.onWordsRecognized(words)
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
                promptSpeaker.speak(event.promptText)
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
        Log.i(TAG, "Performance mode exited")

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
