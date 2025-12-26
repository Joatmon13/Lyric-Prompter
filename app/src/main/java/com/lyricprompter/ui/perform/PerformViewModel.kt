package com.lyricprompter.ui.perform

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val countInPlayer: CountInPlayer
) : ViewModel() {

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
                _uiState.value = PerformUiState.Error("Song not found")
                return@launch
            }

            // Initialize components
            promptSpeaker.initialize()
            voskEngine.loadVocabulary(song.vocabulary)
            positionTracker.loadSong(song)

            _uiState.value = PerformUiState.Ready(
                state = PerformanceState.initial(song)
            )
        }
    }

    fun start() {
        val currentState = (_uiState.value as? PerformUiState.Ready)?.state ?: return
        val song = currentState.song

        viewModelScope.launch {
            // Count-in if enabled
            if (song.countInEnabled && song.bpm != null) {
                _uiState.update { state ->
                    if (state is PerformUiState.Ready) {
                        state.copy(
                            state = state.state.copy(
                                status = PerformanceStatus.CountIn(1, song.countInBeats)
                            )
                        )
                    } else state
                }

                countInPlayer.playCountIn(
                    bpm = song.bpm,
                    beats = song.countInBeats,
                    onBeat = { beat ->
                        _uiState.update { state ->
                            if (state is PerformUiState.Ready) {
                                state.copy(
                                    state = state.state.copy(
                                        status = PerformanceStatus.CountIn(beat, song.countInBeats)
                                    )
                                )
                            } else state
                        }
                    },
                    onComplete = { startListening() }
                )
            } else {
                startListening()
            }
        }
    }

    private fun startListening() {
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
            onPartialResult = { text -> handleRecognition(text) },
            onFinalResult = { text -> handleRecognition(text) },
            onError = { /* Log but continue */ }
        )
    }

    private fun handleRecognition(text: String) {
        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it != "[unk]" }

        if (words.isEmpty()) return

        val event = positionTracker.onWordsRecognized(words)
        val trackingState = positionTracker.getState()

        // Update UI state
        _uiState.update { state ->
            if (state is PerformUiState.Ready) {
                state.copy(
                    state = state.state.copy(
                        currentLineIndex = trackingState.currentLineIndex,
                        recognizedWords = state.state.recognizedWords + words
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
    }
}

sealed interface PerformUiState {
    data object Loading : PerformUiState
    data class Ready(val state: PerformanceState) : PerformUiState
    data class Error(val message: String) : PerformUiState
}
